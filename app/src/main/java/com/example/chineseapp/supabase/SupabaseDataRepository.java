package com.example.chineseapp.supabase;

import android.content.Context;

import com.example.chineseapp.Dictionary.DictionaryEntry;
import com.example.chineseapp.Dictionary.DictionaryRepository;
import com.example.chineseapp.Sentences.SentenceEntry;
import com.example.chineseapp.Sentences.SentenceRepository;
import com.example.chineseapp.WordLists.WordItem;
import com.example.chineseapp.offline.LocalUserDataStore;
import com.example.chineseapp.offline.OfflineContentStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SupabaseDataRepository {

    private final Context context;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final DictionaryRepository dictionaryRepository;
    private final SentenceRepository sentenceRepository;

    public SupabaseDataRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dictionaryRepository = new DictionaryRepository(this.context);
        this.sentenceRepository = new SentenceRepository(this.context);
    }

    public void searchWords(String query, ResultCallback<List<WordItem>> callback) {
        io.execute(() -> {
            try {
                postSuccess(callback, searchWordsSync(query));
            } catch (Exception e) {
                postError(callback, message(e));
            }
        });
    }

    public void searchSentences(String query, ResultCallback<List<SentenceEntry>> callback) {
        io.execute(() -> {
            try {
                postSuccess(callback, searchSentencesSync(query));
            } catch (Exception e) {
                postError(callback, message(e));
            }
        });
    }

    public void getFavouriteSentenceIds(ResultCallback<Set<Long>> callback) {
        io.execute(() -> {
            try {
                postSuccess(callback, favouriteSentenceIdsSync());
            } catch (Exception e) {
                postError(callback, message(e));
            }
        });
    }

    public void getSentencesByIds(Set<Long> ids, ResultCallback<List<SentenceEntry>> callback) {
        io.execute(() -> {
            try {
                postSuccess(callback, sentencesByIdsSync(ids));
            } catch (Exception e) {
                postError(callback, message(e));
            }
        });
    }

    public void setSentenceFavourite(long sentenceId, boolean shouldExist, ResultCallback<Boolean> callback) {
        io.execute(() -> {
            try {
                setSentenceFavouriteSync(sentenceId, shouldExist);
                postSuccess(callback, true);
            } catch (Exception e) {
                postError(callback, message(e));
            }
        });
    }

    public List<WordItem> searchWordsSync(String query) {
        return toWordItems(dictionaryRepository.search(query));
    }

    public List<SentenceEntry> searchSentencesSync(String query) {
        return sentenceRepository.search(query);
    }

    public Set<Long> favouriteSentenceIdsSync() {
        return LocalUserDataStore.getFavouriteSentenceIds(context);
    }

    public List<SentenceEntry> sentencesByIdsSync(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        return sentenceRepository.getByIds(new ArrayList<>(ids));
    }

    public void setSentenceFavouriteSync(long sentenceId, boolean shouldExist) {
        LocalUserDataStore.setFavouriteSentence(context, sentenceId, shouldExist);
    }

    public List<UserList> getUserListsSync() {
        return LocalUserDataStore.getUserLists(context);
    }

    public List<WordItem> getUserListWordsSync(int position) {
        List<Integer> ids = LocalUserDataStore.getUserListWordIds(context, position);
        return getWordsByIdsSync(ids);
    }

    public List<WordItem> getPopularListWordsSync(int listId) {
        return getWordsByIdsSync(OfflineContentStore.getPopularListWordIds(context, listId));
    }

    public List<WordItem> getPopularListFallbackSync(int listId) {
        return OfflineContentStore.getPopularListWords(context, listId);
    }

    public List<WordItem> getRadicalWordsSync() {
        return getPopularListWordsSync(108);
    }

    public List<WordItem> getAllWordsSync() {
        return toWordItems(dictionaryRepository.getAllWords());
    }

    public List<WordItem> getWordsByIdsSync(List<Integer> ids) {
        return toWordItems(dictionaryRepository.getByIds(ids));
    }

    public int addUserListSync(String title, String description) {
        return LocalUserDataStore.createUserList(context, title, description);
    }

    public boolean renameUserListSync(int position, String title, String description) {
        return LocalUserDataStore.renameUserList(context, position, title, description);
    }

    public boolean removeUserListSync(int position) {
        return LocalUserDataStore.removeUserList(context, position);
    }

    public boolean containsWordSync(int position, int wordId) {
        return LocalUserDataStore.containsWordInList(context, position, wordId);
    }

    public boolean addWordToUserListSync(int position, int wordId) {
        return LocalUserDataStore.setWordInList(context, position, wordId, true);
    }

    public boolean removeWordFromUserListSync(int position, int wordId) {
        return LocalUserDataStore.setWordInList(context, position, wordId, false);
    }

    private <T> void postSuccess(ResultCallback<T> callback, T value) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onSuccess(value));
    }

    private <T> void postError(ResultCallback<T> callback, String error) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(error));
    }

    private String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Unexpected error";
    }

    private List<WordItem> toWordItems(List<DictionaryEntry> entries) {
        List<WordItem> out = new ArrayList<>();
        if (entries == null) return out;
        for (DictionaryEntry entry : entries) {
            if (entry == null) continue;
            out.add(new WordItem(entry.id, entry.simplified, entry.pinyin, entry.english));
        }
        return out;
    }
}
