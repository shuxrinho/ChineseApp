package com.example.chineseapp.supabase;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "supabase_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_AVATAR_URL = "avatar_url";

    private SessionManager() {
    }

    public static void saveSession(
            Context context,
            String accessToken,
            String refreshToken,
            String userId,
            String email,
            String username
    ) {
        saveSession(context, accessToken, refreshToken, userId, email, username, getAvatarUrl(context));
    }

    public static void saveSession(
            Context context,
            String accessToken,
            String refreshToken,
            String userId,
            String email,
            String username,
            String avatarUrl
    ) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(KEY_ACCESS_TOKEN, safe(accessToken));
        editor.putString(KEY_REFRESH_TOKEN, safe(refreshToken));
        editor.putString(KEY_USER_ID, safe(userId));
        editor.putString(KEY_EMAIL, safe(email));
        editor.putString(KEY_USERNAME, safe(username));
        editor.putString(KEY_AVATAR_URL, safe(avatarUrl));
        editor.apply();
    }

    public static void saveUser(Context context, SupabaseUser user) {
        if (user == null) return;
        saveSession(
                context,
                getAccessToken(context),
                getRefreshToken(context),
                user.id,
                user.email,
                user.username,
                user.avatarUrl
        );
    }

    public static SupabaseUser getUser(Context context) {
        String id = getUserId(context);
        if (id.isEmpty()) return null;
        return new SupabaseUser(id, getEmail(context), getUsername(context), getAvatarUrl(context));
    }

    public static boolean isLoggedIn(Context context) {
        return !getAccessToken(context).isEmpty() && !getUserId(context).isEmpty();
    }

    public static String getAccessToken(Context context) {
        return get(context, KEY_ACCESS_TOKEN);
    }

    public static String getRefreshToken(Context context) {
        return get(context, KEY_REFRESH_TOKEN);
    }

    public static String getUserId(Context context) {
        return get(context, KEY_USER_ID);
    }

    public static String getEmail(Context context) {
        return get(context, KEY_EMAIL);
    }

    public static String getUsername(Context context) {
        return get(context, KEY_USERNAME);
    }

    public static String getAvatarUrl(Context context) {
        return get(context, KEY_AVATAR_URL);
    }

    public static void saveAvatarUrl(Context context, String avatarUrl) {
        prefs(context).edit().putString(KEY_AVATAR_URL, safe(avatarUrl)).apply();
    }

    public static void clearSession(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static void clear(Context context) {
        clearSession(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String get(Context context, String key) {
        return prefs(context).getString(key, "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
