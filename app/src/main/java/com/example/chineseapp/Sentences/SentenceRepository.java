package com.example.chineseapp.Sentences;

import android.content.Context;

import com.example.chineseapp.Dictionary.AppDatabase;

import java.util.Collections;
import java.util.List;

public class SentenceRepository {

    private final SentenceDao dao;

    public SentenceRepository(Context context) {
        dao = AppDatabase.getInstance(context).sentenceDao();
    }

    public void insertAll(List<SentenceEntry> entries) {
        dao.insertAll(entries);
    }

    public List<SentenceEntry> search(String query) {
        List<SentenceEntry> results;
        if (query == null || query.trim().isEmpty()) {
            results = dao.getRandomBeginner();
        } else {
            results = dao.search(query);
        }

        // Clever sorting algorithm: lower score = better (low HSK, short length, better match)
        if (!results.isEmpty()) {
            final String lowerQuery = query == null ? "" : query.toLowerCase();
            results.sort((a, b) -> Integer.compare(getScore(a, lowerQuery), getScore(b, lowerQuery)));
        }

        return results;
    }

    private int getScore(SentenceEntry entry, String query) {
        int score = entry.hsk * 10 + entry.mandarin.length();

        if (!query.isEmpty()) {
            if (entry.mandarin.toLowerCase().contains(query)) {
                score -= 30; // Strong preference for mandarin match
            } else if (entry.pinyin.toLowerCase().contains(query)) {
                score -= 20;
            } else if (entry.english.toLowerCase().contains(query)) {
                score -= 10;
            }
        }

        // Bonus for very short sentences
        if (entry.mandarin.length() < 10) score -= 5;

        return score;
    }
    public List<SentenceEntry> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return dao.getByIds(ids);
    }
}
