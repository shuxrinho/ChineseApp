package com.example.chineseapp.WordLists;

import android.content.Context;

import com.example.chineseapp.Dictionary.DictionaryEntry;
import com.example.chineseapp.offline.OfflineContentStore;
import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.SupabaseDataRepository;
import com.example.chineseapp.supabase.UserList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ListRepository {

    public static final int LIST_BASIC = 101;
    public static final int LIST_HSK1 = 102;
    public static final int LIST_GREETINGS = 103;
    public static final int LIST_FOOD = 104;
    public static final int LIST_TRAVEL = 105;
    public static final int LIST_NUMBERS = 106;
    public static final int LIST_FAMILY = 107;
    public static final int LIST_RADICALS = 108;
    public static final int LIST_ALL_WORDS = 109;

    private static final int MAX_CUSTOM_LISTS = 6;

    private static final Map<Integer, UserList> listMetaCache = new HashMap<>();
    private static final Map<Integer, List<WordItem>> listWordsCache = new HashMap<>();
    private static final Map<Integer, List<WordItem>> popularWordsCache = new HashMap<>();
    private static String cachedUserId = "";

    private ListRepository() {
    }

    public static void precomputePopularLists(Context context, List<DictionaryEntry> allEntries) throws IOException {
        // No-op: popular lists are served by Supabase now.
    }

    public static void preloadAll(Context context) {
        forceRefreshMeta(context);
        for (int i = 1; i <= MAX_CUSTOM_LISTS; i++) {
            if (listMetaCache.containsKey(i)) {
                loadList(context, i);
            }
        }
        int[] popularIds = {LIST_HSK1, LIST_GREETINGS, LIST_FOOD, LIST_TRAVEL, LIST_NUMBERS, LIST_FAMILY, LIST_RADICALS};
        for (int id : popularIds) {
            loadPopularList(context, id);
        }
    }

    public static void refreshCustomCache(Context context) {
        invalidateCache();
        forceRefreshMeta(context);
    }

    public static List<WordItem> loadPopularList(Context context, int listId) {
        List<WordItem> cached = popularWordsCache.get(listId);
        if (cached != null) return copyWords(cached);

        SupabaseDataRepository repository = new SupabaseDataRepository(context);
        List<WordItem> loaded;
        if (listId == LIST_RADICALS) {
            loaded = repository.getRadicalWordsSync();
        } else {
            loaded = repository.getPopularListWordsSync(listId);
        }

        if (loaded.isEmpty()) {
            loaded = repository.getPopularListFallbackSync(listId);
        }

        popularWordsCache.put(listId, copyWords(loaded));
        return loaded;
    }

    public static void saveList(Context context, int listIndex, List<WordItem> words) {
        if (!isValidCustomIndex(listIndex)) return;
        ensureMetaCache(context);
        listWordsCache.put(listIndex, copyWords(words));
    }

    public static List<WordItem> loadList(Context context, int listIndex) {
        if (!isValidCustomIndex(listIndex)) return new ArrayList<>();
        ensureMetaCache(context);

        List<WordItem> existing = listWordsCache.get(listIndex);
        if (existing != null) {
            return copyWords(existing);
        }

        List<WordItem> loaded = new SupabaseDataRepository(context).getUserListWordsSync(listIndex);
        listWordsCache.put(listIndex, copyWords(loaded));
        return loaded;
    }

    public static void renameList(Context context, int listIndex, String title, String description) {
        if (!isValidCustomIndex(listIndex)) return;
        if (title == null) title = "";
        if (description == null) description = "";

        boolean ok = new SupabaseDataRepository(context)
                .renameUserListSync(listIndex, title.trim(), description.trim());

        if (ok) {
            listMetaCache.put(listIndex, new UserList(listIndex, title.trim(), description.trim()));
        }
    }

    public static String getListTitle(Context context, int listIndex) {
        if (!isValidCustomIndex(listIndex)) return "";
        ensureMetaCache(context);
        UserList list = listMetaCache.get(listIndex);
        return list == null ? ("List " + listIndex) : list.title;
    }

    public static List<WordItem> loadAllCustomWords(Context context) {
        ensureMetaCache(context);
        Set<String> seen = new HashSet<>();
        List<WordItem> all = new ArrayList<>();

        for (int i = 1; i <= MAX_CUSTOM_LISTS; i++) {
            if (!listMetaCache.containsKey(i)) continue;
            for (WordItem item : loadList(context, i)) {
                if (item != null && seen.add(uniqueWordKey(item))) {
                    all.add(item);
                }
            }
        }

        return all;
    }

    public static List<WordItem> loadAllWords(Context context) {
        return loadAllCustomWords(context);
    }

    public static String getPopularListName(Context context, int listId) {
        String fallback = switch (listId) {
            case LIST_HSK1 -> "HSK 1 Essentials";
            case LIST_GREETINGS -> "Greetings & Introductions";
            case LIST_FOOD -> "Food & Restaurant";
            case LIST_TRAVEL -> "Travel & Transport";
            case LIST_NUMBERS -> "Numbers & Time";
            case LIST_FAMILY -> "Family & Daily Life";
            case LIST_RADICALS -> "Radicals";
            case LIST_ALL_WORDS -> "All Words";
            default -> "";
        };
        return OfflineContentStore.getPopularListTitle(context, listId, fallback);
    }

    public static String getListDescription(Context context, int listIndex) {
        if (!isValidCustomIndex(listIndex)) return "";
        ensureMetaCache(context);
        UserList list = listMetaCache.get(listIndex);
        return list == null ? "" : list.description;
    }

    public static void removeList(Context context, int removeIndex) {
        if (!isValidCustomIndex(removeIndex)) return;
        boolean ok = new SupabaseDataRepository(context).removeUserListSync(removeIndex);
        if (ok) {
            invalidateCache();
            ensureMetaCache(context);
        }
    }

    public static int getListCount(Context context) {
        ensureMetaCache(context);
        return listMetaCache.size();
    }

    public static void clearAllLists(Context context) {
        for (int i = MAX_CUSTOM_LISTS; i >= 1; i--) {
            removeList(context, i);
        }
    }

    public static int addList(Context context, String title, String description) {
        if (title == null) title = "";
        if (description == null) description = "";

        forceRefreshMeta(context);
        if (listMetaCache.size() >= MAX_CUSTOM_LISTS) {
            return -1;
        }

        int position = new SupabaseDataRepository(context)
                .addUserListSync(title.trim(), description.trim());

        if (position > 0) {
            listMetaCache.put(position, new UserList(position, title.trim(), description.trim()));
            listWordsCache.put(position, new ArrayList<>());
            return position;
        }

        return -1;
    }

    public static boolean containsWord(Context context, int listIndex, int id) {
        if (!isValidCustomIndex(listIndex) || id <= 0) return false;
        List<WordItem> words = loadList(context, listIndex);
        for (WordItem item : words) {
            if (item.id == id) return true;
        }
        return false;
    }

    public static boolean addWordToList(Context context, int listIndex, WordItem word) {
        if (!isValidCustomIndex(listIndex)) return false;
        if (word == null || word.hanzi == null || word.id <= 0) return false;

        if (containsWord(context, listIndex, word.id)) {
            return false;
        }

        boolean ok = new SupabaseDataRepository(context).addWordToUserListSync(listIndex, word.id);
        if (!ok) return false;

        List<WordItem> cached = loadList(context, listIndex);
        cached.add(new WordItem(word.id, word.hanzi, word.pinyin, word.meaning));
        listWordsCache.put(listIndex, copyWords(cached));
        return true;
    }

    public static boolean removeWordFromList(Context context, int listIndex, int id) {
        if (!isValidCustomIndex(listIndex) || id <= 0) return false;

        boolean ok = new SupabaseDataRepository(context).removeWordFromUserListSync(listIndex, id);
        if (!ok) return false;

        List<WordItem> cached = loadList(context, listIndex);
        boolean removed = false;
        for (int i = cached.size() - 1; i >= 0; i--) {
            if (cached.get(i).id == id) {
                cached.remove(i);
                removed = true;
                break;
            }
        }
        listWordsCache.put(listIndex, copyWords(cached));
        return removed;
    }

    private static boolean isValidCustomIndex(int listIndex) {
        return listIndex >= 1 && listIndex <= MAX_CUSTOM_LISTS;
    }

    private static void ensureMetaCache(Context context) {
        String userId = SessionManager.getUserId(context);
        if (userId == null) userId = "";

        if (!userId.equals(cachedUserId)) {
            invalidateCache();
            cachedUserId = userId;
        }

        if (!listMetaCache.isEmpty()) return;
        forceRefreshMeta(context);
    }

    private static void forceRefreshMeta(Context context) {
        List<UserList> fetched = new SupabaseDataRepository(context).getUserListsSync();
        listMetaCache.clear();
        for (UserList list : fetched) {
            if (isValidCustomIndex(list.position)) {
                listMetaCache.put(list.position, list);
            }
        }
    }

    private static List<WordItem> copyWords(List<WordItem> source) {
        List<WordItem> out = new ArrayList<>();
        if (source == null) return out;
        for (WordItem item : source) {
            out.add(new WordItem(item.id, item.hanzi, item.pinyin, item.meaning));
        }
        return out;
    }

    private static String uniqueWordKey(WordItem item) {
        if (item.id > 0) return "id:" + item.id;
        return ("text:"
                + safeText(item.hanzi) + "|"
                + safeText(item.pinyin) + "|"
                + safeText(item.meaning))
                .toLowerCase(Locale.US);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static void invalidateCache() {
        listMetaCache.clear();
        listWordsCache.clear();
        popularWordsCache.clear();
    }
}
