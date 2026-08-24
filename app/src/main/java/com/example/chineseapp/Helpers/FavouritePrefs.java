package com.example.chineseapp.Helpers;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class FavouritePrefs {

    private static final String PREF_NAME = "favourites_prefs";
    private static final String KEY_FAVOURITE_IDS = "favourite_sentence_ids";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void addFavourite(Context context, long sentenceId) {
        Set<String> ids = new HashSet<>(getPrefs(context).getStringSet(KEY_FAVOURITE_IDS, new HashSet<>()));
        ids.add(String.valueOf(sentenceId));
        getPrefs(context).edit().putStringSet(KEY_FAVOURITE_IDS, ids).apply();
    }

    public static void removeFavourite(Context context, long sentenceId) {
        Set<String> ids = new HashSet<>(getPrefs(context).getStringSet(KEY_FAVOURITE_IDS, new HashSet<>()));
        ids.remove(String.valueOf(sentenceId));
        getPrefs(context).edit().putStringSet(KEY_FAVOURITE_IDS, ids).apply();
    }

    public static boolean isFavourite(Context context, long sentenceId) {
        Set<String> ids = getPrefs(context).getStringSet(KEY_FAVOURITE_IDS, new HashSet<>());
        return ids.contains(String.valueOf(sentenceId));
    }

    public static Set<Long> getAllFavouriteIds(Context context) {
        Set<String> stringIds = getPrefs(context).getStringSet(KEY_FAVOURITE_IDS, new HashSet<>());
        Set<Long> longIds = new HashSet<>();
        for (String idStr : stringIds) {
            try {
                longIds.add(Long.parseLong(idStr));
            } catch (NumberFormatException ignored) {}
        }
        return longIds;
    }

    // Optional: clear all favourites (for testing or logout)
    public static void clearAll(Context context) {
        getPrefs(context).edit().remove(KEY_FAVOURITE_IDS).apply();
    }
}