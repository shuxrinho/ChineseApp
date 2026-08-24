package com.example.chineseapp.offline;

import android.content.Context;

import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.UserList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LocalUserDataStore {

    private static final int MAX_CUSTOM_LISTS = 6;
    private static final Object LOCK = new Object();

    private LocalUserDataStore() {
    }

    public static List<UserList> getUserLists(Context context) {
        synchronized (LOCK) {
            UserListsModel model = readListsModel(context);
            List<UserList> out = new ArrayList<>();
            for (ListRow row : model.lists) {
                out.add(new UserList(row.position, row.title, row.description));
            }
            out.sort(Comparator.comparingInt(a -> a.position));
            return out;
        }
    }

    public static List<Integer> getUserListWordIds(Context context, int position) {
        synchronized (LOCK) {
            UserListsModel model = readListsModel(context);
            ListRow target = findByPosition(model, position);
            return target == null ? new ArrayList<>() : new ArrayList<>(target.wordIds);
        }
    }

    public static int createUserList(Context context, String title, String description) {
        synchronized (LOCK) {
            UserListsModel model = readListsModel(context);
            if (model.lists.size() >= MAX_CUSTOM_LISTS) {
                return -1;
            }

            int nextPosition = findNextPosition(model);
            if (nextPosition < 1) return -1;

            ListRow row = new ListRow();
            row.position = nextPosition;
            row.title = safeText(title).trim();
            row.description = safeText(description).trim();
            model.lists.add(row);
            model.lists.sort(Comparator.comparingInt(a -> a.position));
            writeListsModel(context, model);
            return nextPosition;
        }
    }

    public static boolean renameUserList(Context context, int position, String title, String description) {
        synchronized (LOCK) {
            UserListsModel model = readListsModel(context);
            ListRow target = findByPosition(model, position);
            if (target == null) return false;

            target.title = safeText(title).trim();
            target.description = safeText(description).trim();
            writeListsModel(context, model);
            return true;
        }
    }

    public static boolean removeUserList(Context context, int position) {
        synchronized (LOCK) {
            UserListsModel model = readListsModel(context);
            int removeIdx = -1;
            for (int i = 0; i < model.lists.size(); i++) {
                if (model.lists.get(i).position == position) {
                    removeIdx = i;
                    break;
                }
            }
            if (removeIdx < 0) return false;

            model.lists.remove(removeIdx);

            for (ListRow row : model.lists) {
                if (row.position > position) {
                    row.position -= 1;
                }
            }

            model.lists.sort(Comparator.comparingInt(a -> a.position));
            writeListsModel(context, model);
            return true;
        }
    }

    public static boolean containsWordInList(Context context, int position, int wordId) {
        synchronized (LOCK) {
            UserListsModel model = readListsModel(context);
            ListRow target = findByPosition(model, position);
            return target != null && target.wordIds.contains(wordId);
        }
    }

    public static boolean setWordInList(Context context, int position, int wordId, boolean shouldExist) {
        if (wordId <= 0) return false;

        synchronized (LOCK) {
            UserListsModel model = readListsModel(context);
            ListRow target = findByPosition(model, position);
            if (target == null) return false;

            boolean changed;
            if (shouldExist) {
                changed = !target.wordIds.contains(wordId) && target.wordIds.add(wordId);
            } else {
                changed = target.wordIds.remove((Integer) wordId);
            }

            if (changed) {
                writeListsModel(context, model);
            }
            return changed;
        }
    }

    public static Set<Long> getFavouriteSentenceIds(Context context) {
        synchronized (LOCK) {
            Set<Long> out = new HashSet<>();
            JSONArray array = readFavouritesArray(context);
            for (int i = 0; i < array.length(); i++) {
                long id = array.optLong(i, -1L);
                if (id > 0L) out.add(id);
            }
            return out;
        }
    }

    public static void replaceUserLists(Context context, List<UserList> lists, List<List<Integer>> wordIdsByList) {
        synchronized (LOCK) {
            UserListsModel model = new UserListsModel();
            if (lists != null) {
                for (int i = 0; i < lists.size(); i++) {
                    UserList source = lists.get(i);
                    if (source == null || source.position < 1 || source.position > MAX_CUSTOM_LISTS) continue;

                    ListRow row = new ListRow();
                    row.position = source.position;
                    row.title = safeText(source.title).trim();
                    row.description = safeText(source.description).trim();

                    if (wordIdsByList != null && i < wordIdsByList.size() && wordIdsByList.get(i) != null) {
                        for (Integer id : wordIdsByList.get(i)) {
                            if (id != null && id > 0 && !row.wordIds.contains(id)) {
                                row.wordIds.add(id);
                            }
                        }
                    }
                    model.lists.add(row);
                }
            }
            model.lists.sort(Comparator.comparingInt(a -> a.position));
            writeListsModel(context, model);
        }
    }

    public static void setFavouriteSentence(Context context, long sentenceId, boolean shouldExist) {
        if (sentenceId <= 0L) return;

        synchronized (LOCK) {
            Set<Long> ids = getFavouriteSentenceIds(context);
            if (shouldExist) ids.add(sentenceId);
            else ids.remove(sentenceId);

            JSONArray out = new JSONArray();
            List<Long> sorted = new ArrayList<>(ids);
            Collections.sort(sorted);
            for (Long id : sorted) out.put(id);
            writeFavouritesArray(context, out);
        }
    }

    private static int findNextPosition(UserListsModel model) {
        for (int i = 1; i <= MAX_CUSTOM_LISTS; i++) {
            if (findByPosition(model, i) == null) return i;
        }
        return -1;
    }

    private static ListRow findByPosition(UserListsModel model, int position) {
        for (ListRow row : model.lists) {
            if (row.position == position) return row;
        }
        return null;
    }

    private static UserListsModel readListsModel(Context context) {
        try {
            OfflineContentStore.ensureBootstrapFiles(context);
        } catch (Exception ignored) {
        }

        File file = listsFile(context);
        if (!file.exists() || file.length() == 0L) {
            return new UserListsModel();
        }

        try {
            String raw = readText(file);
            JSONObject obj = raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
            JSONArray listArr = obj.optJSONArray("lists");

            UserListsModel model = new UserListsModel();
            if (listArr == null) return model;

            for (int i = 0; i < listArr.length(); i++) {
                JSONObject rowObj = listArr.optJSONObject(i);
                if (rowObj == null) continue;

                int position = rowObj.optInt("position", -1);
                if (position < 1 || position > MAX_CUSTOM_LISTS) continue;

                ListRow row = new ListRow();
                row.position = position;
                row.title = rowObj.optString("title", "");
                row.description = rowObj.optString("description", "");

                JSONArray ids = rowObj.optJSONArray("word_ids");
                if (ids != null) {
                    for (int j = 0; j < ids.length(); j++) {
                        int id = ids.optInt(j, -1);
                        if (id > 0 && !row.wordIds.contains(id)) {
                            row.wordIds.add(id);
                        }
                    }
                }
                model.lists.add(row);
            }
            model.lists.sort(Comparator.comparingInt(a -> a.position));
            return model;
        } catch (Exception ignored) {
            return new UserListsModel();
        }
    }

    private static void writeListsModel(Context context, UserListsModel model) {
        JSONObject root = new JSONObject();
        JSONArray listArr = new JSONArray();
        List<ListRow> rows = new ArrayList<>(model.lists);
        rows.sort(Comparator.comparingInt(a -> a.position));

        for (ListRow row : rows) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("position", row.position);
                obj.put("title", safeText(row.title));
                obj.put("description", safeText(row.description));

                JSONArray ids = new JSONArray();
                for (Integer id : row.wordIds) {
                    if (id != null && id > 0) ids.put(id);
                }
                obj.put("word_ids", ids);
            } catch (Exception ignored) {
            }
            listArr.put(obj);
        }

        try {
            root.put("lists", listArr);
        } catch (Exception ignored) {
        }

        writeText(listsFile(context), root.toString());
    }

    private static JSONArray readFavouritesArray(Context context) {
        File file = favouritesFile(context);
        if (!file.exists() || file.length() == 0L) return new JSONArray();
        try {
            String raw = readText(file);
            return raw.trim().isEmpty() ? new JSONArray() : new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void writeFavouritesArray(Context context, JSONArray ids) {
        writeText(favouritesFile(context), ids.toString());
    }

    private static File listsFile(Context context) {
        String uid = safeUserId(context);
        return new File(OfflineContentStore.cacheDir(context), "user_lists_" + uid + ".json");
    }

    private static File favouritesFile(Context context) {
        String uid = safeUserId(context);
        return new File(OfflineContentStore.cacheDir(context), "sentence_favourites_" + uid + ".json");
    }

    private static String safeUserId(Context context) {
        String uid = SessionManager.getUserId(context);
        if (uid == null || uid.trim().isEmpty()) uid = "guest";
        return uid.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                bos.write(buffer, 0, read);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        File temp = new File(parent, file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(temp, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception ignored) {
            return;
        }

        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        temp.renameTo(file);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static class UserListsModel {
        final List<ListRow> lists = new ArrayList<>();
    }

    private static class ListRow {
        int position;
        String title = "";
        String description = "";
        final List<Integer> wordIds = new ArrayList<>();
    }
}
