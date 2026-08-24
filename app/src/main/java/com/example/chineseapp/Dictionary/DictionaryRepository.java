package com.example.chineseapp.Dictionary;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DictionaryRepository {

    private final DictionaryDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DictionaryRepository(Context context) {
        dao = AppDatabase.getInstance(context).dictionaryDao();
    }

    public void insertAll(List<DictionaryEntry> entries) {
        executor.execute(() -> dao.insertAll(entries));
    }

    public List<DictionaryEntry> getAllWords() {
        return safe(dao.getAllWords());
    }

    public List<DictionaryEntry> getByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return safe(dao.getByIds(ids));
    }

    public List<DictionaryEntry> search(String query) {

        if (query == null) return Collections.emptyList();

        query = query.trim().toLowerCase(Locale.US);
        if (query.isEmpty()) return Collections.emptyList();

        // 1️⃣ Hanzi
        if (query.matches(".*[\\u4e00-\\u9fff].*")) {
            return safe(dao.searchHanzi(query));
        }

        // normalize input for pinyin
        String pinyinQuery = query
                .replaceAll("[1-5]", "")
                .replaceAll("\\s+", "");

        // 2️⃣ a–z input → English + Pinyin
        if (query.matches("[a-z ]+")) {

            LinkedHashSet<DictionaryEntry> merged = new LinkedHashSet<>();

            // English
            merged.addAll(safe(dao.searchEnglish(query)));

            // Pinyin (normalized)
            merged.addAll(
                    safe(
                            dao.searchPinyinNormalized(pinyinQuery)
                    )
            );

            return new ArrayList<>(merged);
        }

        return Collections.emptyList();
    }


    private List<DictionaryEntry> safe(List<DictionaryEntry> list) {
        return list == null ? Collections.emptyList() : list;
    }


}
