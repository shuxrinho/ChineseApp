package com.example.chineseapp.supabase;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SupabaseAuthRepository {

    private static final String TAG_AUTH = "CHAPP_SB_AUTH";
    private final Context context;
    private final SupabaseClient client;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public SupabaseAuthRepository(Context context) {
        this.context = context.getApplicationContext();
        this.client = new SupabaseClient(this.context);
    }

    public void signUp(String email, String password, String username, ResultCallback<SupabaseUser> callback) {
        io.execute(() -> {
            try {
                Log.d(TAG_AUTH, "signUp start: email=" + email + ", username=" + username);
                ensureConfigured();

                JSONObject payload = new JSONObject()
                        .put("email", email)
                        .put("password", password)
                        .put("data", new JSONObject().put("username", username));

                JSONObject response = client.postAuth("/auth/v1/signup", payload);
                JSONObject parsed = new JSONObject(response.getString("raw"));
                JSONObject user = parsed.optJSONObject("user");

                // Supabase may return either:
                // 1) { user: {...}, access_token: ... }
                // 2) direct user object at top-level (especially when email confirmation is required)
                if (user == null && parsed.has("id") && parsed.has("email")) {
                    user = parsed;
                    Log.d(TAG_AUTH, "signUp response uses top-level user shape.");
                }
                Log.d(TAG_AUTH, "signUp response parsed. hasUser=" + (user != null));

                if (user == null) {
                    throw new IOException("Signup succeeded but no user returned. Check email confirmation settings.");
                }

                String userId = user.optString("id", parsed.optString("id", ""));
                String userEmail = user.optString("email", email);
                JSONObject userMeta = user.optJSONObject("user_metadata");
                String userName = username;
                String avatarUrl = "";
                boolean emailConfirmed = user.optBoolean("email_confirmed", false);
                if (userMeta != null) {
                    userName = userMeta.optString("username", username);
                    avatarUrl = userMeta.optString("avatar_url", "");
                }
                Log.d(TAG_AUTH, "signUp user metadata. userId=" + userId
                        + ", avatarPresent=" + !avatarUrl.isEmpty()
                        + ", emailConfirmed=" + emailConfirmed);

                String access = parsed.optString("access_token", "");
                String refresh = parsed.optString("refresh_token", "");

                // Some Supabase responses wrap tokens inside "session".
                if (access.isEmpty() || refresh.isEmpty()) {
                    JSONObject session = parsed.optJSONObject("session");
                    if (session != null) {
                        access = session.optString("access_token", access);
                        refresh = session.optString("refresh_token", refresh);
                    }
                }
                Log.d(TAG_AUTH, "signUp token status: accessEmpty=" + access.isEmpty() + ", refreshEmpty=" + refresh.isEmpty());

                if (!access.isEmpty() && !userId.isEmpty()) {
                    SessionManager.saveSession(context, access, refresh, userId, userEmail, userName, avatarUrl);
                    SupabaseUser profileUser = resolveProfileWithoutBlockingAuth(userId, userEmail, userName, avatarUrl, emailConfirmed);
                    SessionManager.saveUser(context, profileUser);
                    userName = profileUser.username;
                    userEmail = profileUser.email;
                    avatarUrl = profileUser.avatarUrl;
                    Log.d(TAG_AUTH, "signUp session saved for userId=" + userId);
                }

                if (userId.isEmpty()) {
                    throw new IOException("Signup returned without user id.");
                }

                postSuccess(callback, new SupabaseUser(userId, userEmail, userName, avatarUrl, emailConfirmed));
            } catch (Exception e) {
                Log.e(TAG_AUTH, "signUp failed: " + e.getMessage(), e);
                postError(callback, messageFromException(e));
            }
        });
    }

    public void signIn(String email, String password, ResultCallback<SupabaseUser> callback) {
        io.execute(() -> {
            try {
                Log.d(TAG_AUTH, "signIn start: email=" + email);
                ensureConfigured();

                JSONObject payload = new JSONObject()
                        .put("email", email)
                        .put("password", password);

                JSONObject response = client.postAuth("/auth/v1/token?grant_type=password", payload);
                JSONObject parsed = new JSONObject(response.getString("raw"));

                JSONObject user = parsed.optJSONObject("user");
                if (user == null) {
                    throw new IOException("Login failed: user payload is missing.");
                }

                String userId = user.optString("id", "");
                String userEmail = user.optString("email", email);

                JSONObject appMeta = user.optJSONObject("user_metadata");
                String username = appMeta != null ? appMeta.optString("username", "") : "";
                String avatarUrl = appMeta != null ? appMeta.optString("avatar_url", "") : "";
                boolean emailConfirmed = user.optBoolean("email_confirmed", false);
                Log.d(TAG_AUTH, "signIn user metadata. userId=" + userId
                        + ", avatarPresent=" + !avatarUrl.isEmpty()
                        + ", emailConfirmed=" + emailConfirmed);

                String access = parsed.optString("access_token", "");
                String refresh = parsed.optString("refresh_token", "");

                if (access.isEmpty() || refresh.isEmpty()) {
                    JSONObject session = parsed.optJSONObject("session");
                    if (session != null) {
                        access = session.optString("access_token", access);
                        refresh = session.optString("refresh_token", refresh);
                    }
                }

                if (access.isEmpty() || userId.isEmpty()) {
                    throw new IOException("Login failed: access token was not returned.");
                }

                SessionManager.saveSession(context, access, refresh, userId, userEmail, username, avatarUrl);
                SupabaseUser profileUser = resolveProfileWithoutBlockingAuth(userId, userEmail, username, avatarUrl, emailConfirmed);
                SessionManager.saveUser(context, profileUser);
                Log.d(TAG_AUTH, "signIn success: userId=" + userId + ", username=" + profileUser.username);
                postSuccess(callback, profileUser);
            } catch (Exception e) {
                Log.e(TAG_AUTH, "signIn failed: " + e.getMessage(), e);
                postError(callback, messageFromException(e));
            }
        });
    }

    public void signOut(ResultCallback<Boolean> callback) {
        io.execute(() -> {
            try {
                Log.d(TAG_AUTH, "signOut start");
                if (SessionManager.isLoggedIn(context)) {
                    client.postAuthNoBody("/auth/v1/logout", true);
                }
            } catch (Exception ignored) {
                // Always clear local session even if remote logout fails.
                Log.e(TAG_AUTH, "signOut remote call failed: " + ignored.getMessage(), ignored);
            }

            SessionManager.clearSession(context);
            Log.d(TAG_AUTH, "signOut local session cleared");
            postSuccess(callback, true);
        });
    }

    private void ensureConfigured() throws IOException {
        if (!SupabaseClient.hasConfig()) {
            Log.e(TAG_AUTH, "Supabase config missing or placeholder values are still set.");
            throw new IOException("Supabase keys are missing. Add SUPABASE_URL and SUPABASE_ANON_KEY in local.properties.");
        }
        Log.d(TAG_AUTH, "Supabase config found.");
    }

    private String messageFromException(Exception e) {
        if (e instanceof JSONException) {
            return "Invalid response from Supabase.";
        }
        String raw = e.getMessage() != null ? e.getMessage() : "Unexpected error";
        String lower = raw.toLowerCase();

        if (lower.contains("\"error_code\":\"email_not_confirmed\"")
                || lower.contains("email not confirmed")) {
            return "Email not confirmed. Check inbox/spam, open the Supabase confirmation email, then log in.";
        }

        if (lower.contains("\"error_code\":\"invalid_login_credentials\"")
                || lower.contains("invalid login credentials")) {
            return "Invalid email or password.";
        }

        if (lower.contains("\"error_code\":\"user_already_exists\"")
                || lower.contains("user already registered")) {
            return "This email is already registered. Please log in.";
        }

        if (lower.contains("\"error_code\":\"over_email_send_rate_limit\"")
                || lower.contains("email rate limit exceeded")) {
            return "Too many signup emails were requested. Please wait a few minutes and try again.";
        }

        if (lower.contains("supabase keys are missing")) {
            return "App is not configured: set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties.";
        }

        return raw;
    }

    private SupabaseUser resolveProfileWithoutBlockingAuth(
            String userId,
            String email,
            String username,
            String avatarUrl,
            boolean emailConfirmed
    ) {
        try {
            return loadOrCreateProfile(userId, email, username, avatarUrl, emailConfirmed);
        } catch (Exception e) {
            Log.e(TAG_AUTH, "Profile lookup failed after auth success: " + e.getMessage(), e);
            return new SupabaseUser(userId, email, username, avatarUrl, emailConfirmed);
        }
    }

    private SupabaseUser loadOrCreateProfile(
            String userId,
            String email,
            String username,
            String avatarUrl,
            boolean emailConfirmed
    ) throws Exception {
        Log.d(TAG_AUTH, "Loading profile row. userId=" + userId);
        SupabaseUser loaded = fetchProfile(userId, email, username, avatarUrl, emailConfirmed);
        if (loaded != null) {
            Log.d(TAG_AUTH, "Profile row found. userId=" + userId
                    + ", avatarPresent=" + (loaded.avatarUrl != null && !loaded.avatarUrl.isEmpty()));
            return loaded;
        }

        Log.d(TAG_AUTH, "Profile row missing, creating. userId=" + userId);
        JSONObject payload = new JSONObject()
                .put("id", userId)
                .put("email", safe(email))
                .put("username", safe(username))
                .put("avatar_url", safe(avatarUrl));

        JSONObject response = client.upsertRest("profiles?on_conflict=id", payload, true);
        JSONArray array = new JSONArray(response.getString("raw"));
        Log.d(TAG_AUTH, "Profile create/upsert response rows=" + array.length());
        if (array.length() > 0) {
            JSONObject profile = array.optJSONObject(0);
            if (profile != null) {
                return userFromProfile(profile, userId, email, username, avatarUrl, emailConfirmed);
            }
        }
        return new SupabaseUser(userId, email, username, avatarUrl, emailConfirmed);
    }

    private SupabaseUser fetchProfile(
            String userId,
            String email,
            String username,
            String avatarUrl,
            boolean emailConfirmed
    ) throws Exception {
        JSONObject response = client.getRest(
                "profiles?id=eq." + userId + "&select=id,email,username,avatar_url",
                true
        );
        JSONArray array = new JSONArray(response.getString("raw"));
        Log.d(TAG_AUTH, "Profile fetch response rows=" + array.length() + ", userId=" + userId);
        if (array.length() == 0) return null;

        JSONObject profile = array.optJSONObject(0);
        if (profile == null) return null;
        return userFromProfile(profile, userId, email, username, avatarUrl, emailConfirmed);
    }

    private SupabaseUser userFromProfile(
            JSONObject profile,
            String fallbackId,
            String fallbackEmail,
            String fallbackUsername,
            String fallbackAvatarUrl,
            boolean emailConfirmed
    ) {
        String id = profile.optString("id", fallbackId);
        String email = firstNonEmpty(profile.optString("email", ""), fallbackEmail);
        String username = firstNonEmpty(profile.optString("username", ""), fallbackUsername);
        String avatarUrl = firstNonEmpty(profile.optString("avatar_url", ""), fallbackAvatarUrl);
        return new SupabaseUser(id, email, username, avatarUrl, emailConfirmed);
    }

    private String firstNonEmpty(String preferred, String fallback) {
        String safePreferred = safe(preferred);
        return safePreferred.isEmpty() ? safe(fallback) : safePreferred;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> void postSuccess(ResultCallback<T> callback, T value) {
        main.post(() -> callback.onSuccess(value));
    }

    private <T> void postError(ResultCallback<T> callback, String error) {
        main.post(() -> callback.onError(error));
    }
}
