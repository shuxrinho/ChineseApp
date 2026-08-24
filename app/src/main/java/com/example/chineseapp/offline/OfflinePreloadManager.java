package com.example.chineseapp.offline;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.example.chineseapp.Dictionary.AppDatabase;
import com.example.chineseapp.Dictionary.DictionaryDao;
import com.example.chineseapp.Dictionary.DictionaryEntry;
import com.example.chineseapp.Sentences.SentenceDao;
import com.example.chineseapp.Sentences.SentenceEntry;
import com.example.chineseapp.supabase.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class OfflinePreloadManager {

    private static final int PAGE_SIZE = 1000;
    private static final int EXPECTED_WORDS = 124000;
    private static final int EXPECTED_SENTENCES = 22000;

    public interface StatusListener {
        void onStatus(String message);
    }

    public static final class PreloadResult {
        public final boolean refreshed;
        public final boolean usedCachedCopy;
        public final String errorMessage;

        PreloadResult(boolean refreshed, boolean usedCachedCopy, String errorMessage) {
            this.refreshed = refreshed;
            this.usedCachedCopy = usedCachedCopy;
            this.errorMessage = errorMessage;
        }
    }

    private final Context context;
    private final SupabaseClient client;
    private final AppDatabase database;

    public OfflinePreloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new SupabaseClient(this.context);
        this.database = AppDatabase.getInstance(this.context);
    }

    public PreloadResult ensureOfflineData(StatusListener listener) {
        try {
            OfflineContentStore.ensureBootstrapFiles(context);
            DictionaryDao dictionaryDao = database.dictionaryDao();
            SentenceDao sentenceDao = database.sentenceDao();
            int wordsCount = dictionaryDao.count();
            int sentencesCount = sentenceDao.count();
            boolean hasLists = OfflineContentStore.hasListFiles(context);
            boolean shouldRefresh = wordsCount == 0 || sentencesCount == 0 || !hasLists;

            if (!shouldRefresh) {
                if (listener != null) listener.onStatus("Datasets already cached.");
                return new PreloadResult(false, true, null);
            }

            if (!SupabaseClient.hasConfig()) {
                return new PreloadResult(false, wordsCount > 0 && sentencesCount > 0, "Supabase keys are missing.");
            }

            if (listener != null) listener.onStatus("Preparing local database...");
            dictionaryDao.clearAll();
            sentenceDao.clearAll();

            int downloadedWords = downloadWords(dictionaryDao, listener);
            int downloadedSentences = downloadSentences(sentenceDao, listener);

            if (listener != null) listener.onStatus("Downloading popular lists 82%...");
            JSONArray popularLists = fetchAllRows("popular_word_lists", "id.asc");
            if (listener != null) listener.onStatus("Downloading list mappings 88%...");
            JSONArray popularListWords = fetchAllRows("popular_list_words", "list_id.asc,word_id.asc");

            if (listener != null) listener.onStatus("Saving list files 96%...");
            OfflineContentStore.writeListDataset(
                    context,
                    popularLists,
                    popularListWords,
                    currentAppVersionCode(),
                    downloadedWords,
                    downloadedSentences
            );

            if (listener != null) listener.onStatus("Datasets ready 100%.");
            OfflineContentStore.invalidateMemoryCache();
            return new PreloadResult(true, true, null);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "Unexpected preload error";
            boolean hasBackup = database.dictionaryDao().count() > 0 && database.sentenceDao().count() > 0;
            return new PreloadResult(false, hasBackup, message);
        }
    }

    private int downloadWords(DictionaryDao dao, StatusListener listener) throws Exception {
        int offset = 0;
        int total = 0;

        while (true) {
            int percent = Math.min(65, Math.max(0, (int) ((total / (float) EXPECTED_WORDS) * 65f)));
            if (listener != null) listener.onStatus("Downloading words " + percent + "% (" + total + ")");

            JSONArray page = fetchPage("words", "id.asc", offset);
            if (page.length() == 0) break;

            List<DictionaryEntry> entries = new ArrayList<>(page.length());
            for (int i = 0; i < page.length(); i++) {
                JSONObject obj = page.optJSONObject(i);
                if (obj == null) continue;

                DictionaryEntry entry = new DictionaryEntry();
                entry.id = obj.optInt("id", 0);
                entry.traditional = firstNonEmpty(obj.optString("traditional", ""), obj.optString("trad", ""));
                entry.simplified = firstNonEmpty(obj.optString("hanzi", ""), obj.optString("simp", ""), obj.optString("simplified", ""));
                entry.pinyin = obj.optString("pinyin", "");
                entry.english = firstNonEmpty(obj.optString("english", ""), obj.optString("meaning", ""));
                if (entry.id > 0 && entry.simplified != null && !entry.simplified.trim().isEmpty()) {
                    entries.add(entry);
                }
            }
            dao.insertAll(entries);
            total += page.length();

            if (page.length() < PAGE_SIZE) break;
            offset += PAGE_SIZE;
        }
        return total;
    }

    private int downloadSentences(SentenceDao dao, StatusListener listener) throws Exception {
        int offset = 0;
        int total = 0;

        while (true) {
            int percent = 65 + Math.min(15, Math.max(0, (int) ((total / (float) EXPECTED_SENTENCES) * 15f)));
            if (listener != null) listener.onStatus("Downloading sentences " + percent + "% (" + total + ")");

            JSONArray page = fetchPage("sentences", "id.asc", offset);
            if (page.length() == 0) break;

            List<SentenceEntry> entries = new ArrayList<>(page.length());
            for (int i = 0; i < page.length(); i++) {
                JSONObject obj = page.optJSONObject(i);
                if (obj == null) continue;

                SentenceEntry entry = new SentenceEntry();
                entry.id = obj.optInt("id", 0);
                entry.mandarin = firstNonEmpty(obj.optString("mandarin", ""), obj.optString("chinese", ""));
                entry.pinyin = obj.optString("pinyin", "");
                entry.english = firstNonEmpty(obj.optString("english", ""), obj.optString("meaning", ""));
                entry.hsk = obj.optInt("hsk", 1);
                if (entry.id > 0 && entry.mandarin != null && !entry.mandarin.trim().isEmpty()) {
                    entries.add(entry);
                }
            }
            dao.insertAll(entries);
            total += page.length();

            if (page.length() < PAGE_SIZE) break;
            offset += PAGE_SIZE;
        }
        return total;
    }

    private JSONArray fetchAllRows(String tableName, String orderBy) throws Exception {
        JSONArray merged = new JSONArray();
        int offset = 0;

        while (true) {
            JSONArray page = fetchPage(tableName, orderBy, offset);
            for (int i = 0; i < page.length(); i++) {
                merged.put(page.get(i));
            }

            if (page.length() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return merged;
    }

    private JSONArray fetchPage(String tableName, String orderBy, int offset) throws Exception {
        String path = tableName
                + "?select=*"
                + "&order=" + orderBy
                + "&limit=" + PAGE_SIZE
                + "&offset=" + offset;
        return new JSONArray(client.getRest(path, false).getString("raw"));
    }

    private long currentAppVersionCode() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        //noinspection deprecation
        return info.versionCode;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "";
    }
}
