package com.example.chineseapp.Helpers;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppInitPrefs {

    private static final String PREFS = "app_init_prefs";

    // Keys for different initialization steps
    private static final String KEY_DICT_READY      = "dict_populated";
    private static final String KEY_POPULAR_READY   = "popular_lists_populated";
    private static final String KEY_SENTENCES_READY = "sentences_populated";

    public static boolean isDictionaryReady(Context context) {
        return getPrefs(context).getBoolean(KEY_DICT_READY, false);
    }

    public static void markDictionaryReady(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_DICT_READY, true)
                .apply();
    }

    public static boolean isPopularListsReady(Context context) {
        return getPrefs(context).getBoolean(KEY_POPULAR_READY, false);
    }

    public static void markPopularListsReady(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_POPULAR_READY, true)
                .apply();
    }

    public static boolean isSentencesReady(Context context) {
        return getPrefs(context).getBoolean(KEY_SENTENCES_READY, false);
    }

    public static void markSentencesReady(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_SENTENCES_READY, true)
                .apply();
    }

    // Helper
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // Optional: reset everything (useful for testing)
    public static void resetAll(Context context) {
        getPrefs(context).edit().clear().apply();
    }
}