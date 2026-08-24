package com.example.chineseapp.offline;

import android.content.Context;
import android.content.res.AssetManager;

import com.example.chineseapp.Sentences.SentenceEntry;
import com.example.chineseapp.WordLists.ListRepository;
import com.example.chineseapp.WordLists.WordItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OfflineContentStore {

    public static final String CACHE_DIR_NAME = "offline_assets";

    private static final String FILE_WORDS = "words.json";
    private static final String FILE_SENTENCES = "sentences.json";
    private static final String FILE_POPULAR_LISTS = "popular_word_lists.json";
    private static final String FILE_POPULAR_LIST_WORDS = "popular_list_words.json";
    private static final String FILE_MANIFEST = "manifest.json";

    private static final String[] REQUIRED_FILES = {
            FILE_WORDS,
            FILE_SENTENCES,
            FILE_POPULAR_LISTS,
            FILE_POPULAR_LIST_WORDS,
            FILE_MANIFEST
    };

    private static final String[] BUNDLED_SEED_FILES = {
            "offline_words.json",
            "offline_sentences.json",
            "offline_popular_word_lists.json",
            "offline_popular_list_words.json"
    };

    private static final Object LOCK = new Object();
    private static CachedData cachedData;

    private OfflineContentStore() {
    }

    public static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static boolean hasRequiredFiles(Context context) {
        File root = cacheDir(context);
        for (String file : REQUIRED_FILES) {
            File target = new File(root, file);
            if (!target.exists() || target.length() == 0L) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasListFiles(Context context) {
        File root = cacheDir(context);
        File popularLists = new File(root, FILE_POPULAR_LISTS);
        File popularListWords = new File(root, FILE_POPULAR_LIST_WORDS);
        try {
            return popularLists.exists()
                    && popularListWords.exists()
                    && readJsonArray(popularLists).length() > 0
                    && readJsonArray(popularListWords).length() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static long cachedAppVersionCode(Context context) {
        try {
            JSONObject manifest = readJsonObject(new File(cacheDir(context), FILE_MANIFEST));
            return manifest.optLong("app_version_code", -1L);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    public static void ensureBootstrapFiles(Context context) throws IOException {
        File root = cacheDir(context);

        File words = new File(root, FILE_WORDS);
        if (!words.exists()) {
            copyBundledSeedOrWriteEmpty(context, BUNDLED_SEED_FILES[0], words);
        }

        File sentences = new File(root, FILE_SENTENCES);
        if (!sentences.exists()) {
            copyBundledSeedOrWriteEmpty(context, BUNDLED_SEED_FILES[1], sentences);
        }

        File popularLists = new File(root, FILE_POPULAR_LISTS);
        if (!popularLists.exists()) {
            copyBundledSeedOrWriteEmpty(context, BUNDLED_SEED_FILES[2], popularLists);
        }

        File popularListWords = new File(root, FILE_POPULAR_LIST_WORDS);
        if (!popularListWords.exists()) {
            copyBundledSeedOrWriteEmpty(context, BUNDLED_SEED_FILES[3], popularListWords);
        }

        File manifest = new File(root, FILE_MANIFEST);
        if (!manifest.exists()) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("app_version_code", -1L);
                obj.put("updated_at_utc_ms", 0L);
            } catch (Exception ignored) {
            }
            writeText(manifest, obj.toString());
        }
    }

    public static void writeStaticDataset(
            Context context,
            JSONArray words,
            JSONArray sentences,
            JSONArray popularLists,
            JSONArray popularListWords,
            long appVersionCode
    ) throws IOException {
        File root = cacheDir(context);
        writeText(new File(root, FILE_WORDS), words.toString());
        writeText(new File(root, FILE_SENTENCES), sentences.toString());
        writeText(new File(root, FILE_POPULAR_LISTS), popularLists.toString());
        writeText(new File(root, FILE_POPULAR_LIST_WORDS), popularListWords.toString());

        JSONObject manifest = new JSONObject();
        try {
            manifest.put("app_version_code", appVersionCode);
            manifest.put("updated_at_utc_ms", System.currentTimeMillis());
            manifest.put("words_count", words.length());
            manifest.put("sentences_count", sentences.length());
            manifest.put("popular_lists_count", popularLists.length());
            manifest.put("popular_list_words_count", popularListWords.length());
        } catch (Exception ignored) {
        }
        writeText(new File(root, FILE_MANIFEST), manifest.toString());

        invalidateMemoryCache();
    }

    public static void writeListDataset(
            Context context,
            JSONArray popularLists,
            JSONArray popularListWords,
            long appVersionCode,
            int wordsCount,
            int sentencesCount
    ) throws IOException {
        File root = cacheDir(context);
        writeText(new File(root, FILE_POPULAR_LISTS), popularLists.toString());
        writeText(new File(root, FILE_POPULAR_LIST_WORDS), popularListWords.toString());

        JSONObject manifest = new JSONObject();
        try {
            manifest.put("app_version_code", appVersionCode);
            manifest.put("updated_at_utc_ms", System.currentTimeMillis());
            manifest.put("words_count", wordsCount);
            manifest.put("sentences_count", sentencesCount);
            manifest.put("popular_lists_count", popularLists.length());
            manifest.put("popular_list_words_count", popularListWords.length());
        } catch (Exception ignored) {
        }
        writeText(new File(root, FILE_MANIFEST), manifest.toString());
        invalidateMemoryCache();
    }

    public static List<Integer> getPopularListWordIds(Context context, int listId) {
        CachedData data = requirePopularListWordMap(context);
        List<Integer> ids = data.popularWordIds.get(listId);
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    public static String getPopularListTitle(Context context, int listId, String fallback) {
        CachedData data = requirePopularLists(context);
        String value = data.popularListNames.get(listId);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static void invalidateMemoryCache() {
        synchronized (LOCK) {
            cachedData = null;
        }
    }

    public static List<WordItem> searchWords(Context context, String query, int maxResults) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isEmpty()) return new ArrayList<>();

        CachedData data = requireWords(context);
        String lower = safeQuery.toLowerCase(Locale.US);
        String normalized = normalizePinyin(lower);

        List<ScoredWord> scored = new ArrayList<>();
        for (WordRecord item : data.words) {
            boolean match = item.hanziLower.contains(lower)
                    || item.pinyinLower.contains(lower)
                    || item.meaningLower.contains(lower)
                    || (!normalized.isEmpty() && item.pinyinNormalized.contains(normalized));

            if (!match) continue;
            scored.add(new ScoredWord(item, scoreWord(item, lower, normalized)));
        }

        scored.sort(Comparator.comparingInt(a -> a.score));

        int limit = Math.max(0, maxResults);
        List<WordItem> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && (limit == 0 || out.size() < limit); i++) {
            out.add(copyWord(scored.get(i).record.toWordItem()));
        }
        return out;
    }

    public static List<SentenceEntry> searchSentences(Context context, String query, int maxResults) {
        CachedData data = requireSentences(context);
        String safeQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        int limit = maxResults <= 0 ? 120 : maxResults;

        if (safeQuery.isEmpty()) {
            List<SentenceEntry> beginner = new ArrayList<>();
            for (SentenceRecord record : data.sentences) {
                if (record.entry.hsk <= 2 && safeText(record.entry.mandarin).length() <= 25) {
                    beginner.add(copySentence(record.entry));
                }
            }
            if (beginner.isEmpty()) {
                beginner.addAll(copySentenceRecords(data.sentences));
            }
            Collections.shuffle(beginner);
            return beginner.subList(0, Math.min(beginner.size(), Math.min(limit, 20)));
        }

        String normalized = normalizePinyin(safeQuery);
        List<ScoredSentence> scored = new ArrayList<>();
        for (SentenceRecord record : data.sentences) {
            boolean matches = record.mandarinLower.contains(safeQuery)
                    || record.pinyinLower.contains(safeQuery)
                    || record.englishLower.contains(safeQuery)
                    || (!normalized.isEmpty() && record.pinyinNormalized.contains(normalized));

            if (!matches) continue;
            scored.add(new ScoredSentence(record, scoreSentence(record, safeQuery)));
        }

        scored.sort(Comparator.comparingInt(a -> a.score));
        List<SentenceEntry> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && out.size() < limit; i++) {
            out.add(copySentence(scored.get(i).record.entry));
        }
        return out;
    }

    public static List<SentenceEntry> getSentencesByIds(Context context, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        CachedData data = requireSentences(context);

        List<SentenceEntry> out = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) continue;
            SentenceRecord record = data.sentencesById.get((int) id.longValue());
            if (record != null) {
                out.add(copySentence(record.entry));
            }
        }
        out.sort((a, b) -> {
            int byHsk = Integer.compare(a.hsk, b.hsk);
            if (byHsk != 0) return byHsk;
            return Integer.compare(safeText(a.mandarin).length(), safeText(b.mandarin).length());
        });
        return out;
    }

    public static List<WordItem> getWordsByIds(Context context, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        CachedData data = requireWords(context);

        List<WordItem> out = new ArrayList<>();
        for (Integer id : ids) {
            if (id == null) continue;
            WordRecord item = data.wordsById.get(id);
            if (item != null) {
                out.add(copyWord(item.toWordItem()));
            }
        }
        return out;
    }

    public static WordItem getWordById(Context context, int id) {
        CachedData data = requireWords(context);
        WordRecord item = data.wordsById.get(id);
        return item == null ? null : copyWord(item.toWordItem());
    }

    public static List<WordItem> getPopularListWords(Context context, int listId) {
        CachedData data = requireWords(context);
        requirePopularListWordMap(context);

        List<Integer> mappedIds = data.popularWordIds.get(listId);
        List<WordItem> out = new ArrayList<>();
        if (mappedIds != null && !mappedIds.isEmpty()) {
            Set<Integer> seen = new HashSet<>();
            for (Integer id : mappedIds) {
                if (id == null || !seen.add(id)) continue;
                WordRecord record = data.wordsById.get(id);
                if (record != null) {
                    out.add(copyWord(record.toWordItem()));
                }
            }
        }

        if (!out.isEmpty()) {
            return out;
        }
        return fallbackWordsForList(data, listId);
    }

    private static CachedData requireWords(Context context) {
        synchronized (LOCK) {
            CachedData data = dataLocked();
            if (data.wordsLoaded) {
                return data;
            }

            try {
                ensureBootstrapReady(context, data);
                data.words.clear();
                data.wordsById.clear();
                parseWords(readJsonArray(new File(cacheDir(context), FILE_WORDS)), data);
            } catch (Exception ignored) {
                data.words.clear();
                data.wordsById.clear();
            }
            data.wordsLoaded = true;
            return data;
        }
    }

    private static CachedData requireSentences(Context context) {
        synchronized (LOCK) {
            CachedData data = dataLocked();
            if (data.sentencesLoaded) {
                return data;
            }

            try {
                ensureBootstrapReady(context, data);
                data.sentences.clear();
                data.sentencesById.clear();
                parseSentences(readJsonArray(new File(cacheDir(context), FILE_SENTENCES)), data);
            } catch (Exception ignored) {
                data.sentences.clear();
                data.sentencesById.clear();
            }
            data.sentencesLoaded = true;
            return data;
        }
    }

    private static CachedData requirePopularLists(Context context) {
        synchronized (LOCK) {
            CachedData data = dataLocked();
            if (data.popularListsLoaded) {
                return data;
            }

            try {
                ensureBootstrapReady(context, data);
                data.popularListNames.clear();
                data.popularListDescriptions.clear();
                parsePopularLists(readJsonArray(new File(cacheDir(context), FILE_POPULAR_LISTS)), data);
            } catch (Exception ignored) {
                data.popularListNames.clear();
                data.popularListDescriptions.clear();
            }
            data.popularListsLoaded = true;
            return data;
        }
    }

    private static CachedData requirePopularListWordMap(Context context) {
        synchronized (LOCK) {
            CachedData data = dataLocked();
            if (data.popularListWordMapLoaded) {
                return data;
            }

            try {
                ensureBootstrapReady(context, data);
                data.popularWordIds.clear();
                parsePopularListWordMap(readJsonArray(new File(cacheDir(context), FILE_POPULAR_LIST_WORDS)), data);
            } catch (Exception ignored) {
                data.popularWordIds.clear();
            }
            data.popularListWordMapLoaded = true;
            return data;
        }
    }

    private static CachedData dataLocked() {
        if (cachedData == null) {
            cachedData = new CachedData();
        }
        return cachedData;
    }

    private static void ensureBootstrapReady(Context context, CachedData data) throws IOException {
        if (data.bootstrapFilesChecked) return;
        ensureBootstrapFiles(context);
        data.bootstrapFilesChecked = true;
    }

    private static void parseWords(JSONArray words, CachedData data) {
        int fallbackId = 1;
        for (int i = 0; i < words.length(); i++) {
            JSONObject obj = words.optJSONObject(i);
            if (obj == null) continue;

            int id = safeInt(obj, "id", 0);
            if (id <= 0) {
                id = fallbackId++;
            }

            String hanzi = firstNonEmpty(
                    obj.optString("simp", ""),
                    obj.optString("hanzi", ""),
                    obj.optString("simplified", ""),
                    obj.optString("traditional", ""),
                    obj.optString("trad", "")
            );
            if (hanzi.isEmpty()) continue;

            String pinyin = firstNonEmpty(
                    obj.optString("pinyin", ""),
                    obj.optString("py", "")
            );

            String meaning = firstNonEmpty(
                    obj.optString("meaning", ""),
                    obj.optString("english", ""),
                    obj.optString("translation", "")
            );

            int hsk = safeInt(obj, "hsk", 0);
            boolean isRadical = obj.optBoolean("is_radical", false);

            WordRecord record = new WordRecord(id, hanzi, pinyin, meaning, hsk, isRadical);
            data.words.add(record);
            data.wordsById.put(id, record);
        }
    }

    private static void parseSentences(JSONArray sentences, CachedData data) {
        int fallbackId = 1;
        for (int i = 0; i < sentences.length(); i++) {
            JSONObject obj = sentences.optJSONObject(i);
            if (obj == null) continue;

            int id = safeInt(obj, "id", 0);
            if (id <= 0) {
                id = fallbackId++;
            }

            SentenceEntry entry = new SentenceEntry();
            entry.id = id;
            entry.mandarin = firstNonEmpty(
                    obj.optString("mandarin", ""),
                    obj.optString("chinese", "")
            );
            entry.pinyin = obj.optString("pinyin", "");
            entry.english = firstNonEmpty(
                    obj.optString("english", ""),
                    obj.optString("meaning", "")
            );
            entry.hsk = safeInt(obj, "hsk", 0);

            if (entry.mandarin == null || entry.mandarin.isEmpty()) continue;
            SentenceRecord record = new SentenceRecord(entry);
            data.sentences.add(record);
            data.sentencesById.put(entry.id, record);
        }
    }

    private static void parsePopularLists(JSONArray popularLists, CachedData data) {
        for (int i = 0; i < popularLists.length(); i++) {
            JSONObject obj = popularLists.optJSONObject(i);
            if (obj == null) continue;

            int id = safeInt(obj, "id", -1);
            if (id <= 0) continue;

            String name = obj.optString("name", "");
            String description = obj.optString("description", "");
            data.popularListNames.put(id, name);
            data.popularListDescriptions.put(id, description);
        }
    }

    private static void parsePopularListWordMap(JSONArray rows, CachedData data) {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject obj = rows.optJSONObject(i);
            if (obj == null) continue;

            int listId = safeInt(obj, "list_id", -1);
            int wordId = safeInt(obj, "word_id", -1);
            if (listId <= 0 || wordId <= 0) continue;

            List<Integer> ids = data.popularWordIds.get(listId);
            if (ids == null) {
                ids = new ArrayList<>();
                data.popularWordIds.put(listId, ids);
            }
            ids.add(wordId);
        }
    }

    private static int safeInt(JSONObject obj, String key, int fallback) {
        if (!obj.has(key)) return fallback;
        Object raw = obj.opt(key);
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt((String) raw);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int scoreWord(WordRecord item, String lowerQuery, String normalizedQuery) {
        int score = safeText(item.hanzi).length();

        if (item.hanziLower.equals(lowerQuery)) score -= 140;
        else if (item.hanziLower.startsWith(lowerQuery)) score -= 120;
        else if (item.hanziLower.contains(lowerQuery)) score -= 100;

        if (item.pinyinLower.equals(lowerQuery)) score -= 90;
        else if (item.pinyinLower.startsWith(lowerQuery)) score -= 70;
        else if (item.pinyinLower.contains(lowerQuery)) score -= 55;

        if (item.meaningLower.startsWith(lowerQuery)) score -= 40;
        else if (item.meaningLower.contains(lowerQuery)) score -= 25;

        if (!normalizedQuery.isEmpty() && item.pinyinNormalized.contains(normalizedQuery)) {
            score -= 50;
        }

        if (item.hsk > 0) score += item.hsk * 3;
        return score;
    }

    private static int scoreSentence(SentenceRecord record, String query) {
        SentenceEntry entry = record.entry;
        int score = entry.hsk * 10 + safeText(entry.mandarin).length();

        if (record.mandarinLower.contains(query)) {
            score -= 30;
        } else if (record.pinyinLower.contains(query)) {
            score -= 20;
        } else if (record.englishLower.contains(query)) {
            score -= 10;
        }

        if (safeText(entry.mandarin).length() < 10) {
            score -= 5;
        }
        return score;
    }

    private static List<WordItem> fallbackWordsForList(CachedData data, int listId) {
        List<WordItem> out = new ArrayList<>();
        for (WordRecord row : data.words) {
            if (matchesPopularFallback(listId, row)) {
                out.add(copyWord(row.toWordItem()));
                if (out.size() >= 220) break;
            }
        }
        return out;
    }

    private static boolean matchesPopularFallback(int listId, WordRecord row) {
        if (row == null) return false;
        String meaning = row.meaningLower;

        switch (listId) {
            case ListRepository.LIST_HSK1:
                return row.hsk == 1;
            case ListRepository.LIST_GREETINGS:
                return containsAny(meaning, "hello", "goodbye", "bye", "thank", "sorry", "please", "name", "how are");
            case ListRepository.LIST_FOOD:
                return containsAny(meaning, "eat", "food", "drink", "tea", "water", "rice", "noodle",
                        "meat", "fish", "vegetable", "fruit", "restaurant", "hungry", "delicious");
            case ListRepository.LIST_TRAVEL:
                return containsAny(meaning, "go", "come", "travel", "walk", "run", "bus", "train",
                        "plane", "car", "taxi", "hotel", "ticket", "airport", "station", "passport", "visa");
            case ListRepository.LIST_NUMBERS:
                return containsAny(meaning, "one", "two", "three", "four", "five", "six", "seven", "eight",
                        "nine", "ten", "hundred", "thousand", "time", "hour", "minute", "second", "day", "week",
                        "month", "year", "today", "tomorrow", "yesterday", "morning", "evening", "night");
            case ListRepository.LIST_FAMILY:
                return containsAny(meaning, "family", "father", "mother", "dad", "mom", "brother", "sister",
                        "son", "daughter", "child", "home", "house", "sleep", "work", "study", "school",
                        "friend", "love", "like", "happy", "sad", "tired");
            case ListRepository.LIST_RADICALS:
                return row.isRadical;
            default:
                return false;
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private static JSONArray readJsonArray(File file) throws Exception {
        String text = readText(file);
        return text.trim().isEmpty() ? new JSONArray() : new JSONArray(text);
    }

    private static JSONObject readJsonObject(File file) throws Exception {
        String text = readText(file);
        return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static String readText(File file) throws IOException {
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

    private static void writeText(File file, String content) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(temp, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        temp.renameTo(file);
    }

    private static void copyBundledSeedOrWriteEmpty(Context context, String assetName, File target) throws IOException {
        AssetManager assetManager = context.getAssets();
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            try (var in = assetManager.open(assetName)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.flush();
                return;
            } catch (Exception ignored) {
                out.write("[]".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String lower(String value) {
        return safeText(value).toLowerCase(Locale.US);
    }

    private static String normalizePinyin(String value) {
        String lower = lower(value);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static WordItem copyWord(WordItem src) {
        if (src == null) return null;
        return new WordItem(src.id, src.hanzi, src.pinyin, src.meaning);
    }

    private static SentenceEntry copySentence(SentenceEntry src) {
        SentenceEntry copy = new SentenceEntry();
        copy.id = src.id;
        copy.mandarin = src.mandarin;
        copy.pinyin = src.pinyin;
        copy.english = src.english;
        copy.hsk = src.hsk;
        return copy;
    }

    private static List<SentenceEntry> copySentenceRecords(List<SentenceRecord> source) {
        List<SentenceEntry> out = new ArrayList<>();
        for (SentenceRecord sentence : source) {
            out.add(copySentence(sentence.entry));
        }
        return out;
    }

    private static class CachedData {
        final List<WordRecord> words = new ArrayList<>();
        final Map<Integer, WordRecord> wordsById = new HashMap<>();
        final List<SentenceRecord> sentences = new ArrayList<>();
        final Map<Integer, SentenceRecord> sentencesById = new HashMap<>();
        final Map<Integer, List<Integer>> popularWordIds = new HashMap<>();
        final Map<Integer, String> popularListNames = new LinkedHashMap<>();
        final Map<Integer, String> popularListDescriptions = new LinkedHashMap<>();
        boolean wordsLoaded;
        boolean sentencesLoaded;
        boolean popularListsLoaded;
        boolean popularListWordMapLoaded;
        boolean bootstrapFilesChecked;
    }

    private static class WordRecord {
        final int id;
        final String hanzi;
        final String pinyin;
        final String meaning;
        final String hanziLower;
        final String pinyinLower;
        final String meaningLower;
        final String pinyinNormalized;
        final int hsk;
        final boolean isRadical;

        WordRecord(int id, String hanzi, String pinyin, String meaning, int hsk, boolean isRadical) {
            this.id = id;
            this.hanzi = hanzi;
            this.pinyin = pinyin;
            this.meaning = meaning;
            this.hanziLower = lower(hanzi);
            this.pinyinLower = lower(pinyin);
            this.meaningLower = lower(meaning);
            this.pinyinNormalized = normalizePinyin(pinyin);
            this.hsk = hsk;
            this.isRadical = isRadical;
        }

        WordItem toWordItem() {
            return new WordItem(id, hanzi, pinyin, meaning);
        }
    }

    private static class SentenceRecord {
        final SentenceEntry entry;
        final String mandarinLower;
        final String pinyinLower;
        final String englishLower;
        final String pinyinNormalized;

        SentenceRecord(SentenceEntry entry) {
            this.entry = entry;
            this.mandarinLower = lower(entry.mandarin);
            this.pinyinLower = lower(entry.pinyin);
            this.englishLower = lower(entry.english);
            this.pinyinNormalized = normalizePinyin(entry.pinyin);
        }
    }

    private static class ScoredWord {
        final WordRecord record;
        final int score;

        ScoredWord(WordRecord record, int score) {
            this.record = record;
            this.score = score;
        }
    }

    private static class ScoredSentence {
        final SentenceRecord record;
        final int score;

        ScoredSentence(SentenceRecord record, int score) {
            this.record = record;
            this.score = score;
        }
    }
}
