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
                if (userMeta != null) {
                    userName = userMeta.optString("username", username);
                    avatarUrl = userMeta.optString("avatar_url", "");
                }
                Log.d(TAG_AUTH, "signUp user metadata. userId=" + userId
                        + ", avatarPresent=" + !avatarUrl.isEmpty());

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
                    SupabaseUser profileUser = resolveProfileWithoutBlockingAuth(userId, userEmail, userName, avatarUrl);
                    SessionManager.saveUser(context, profileUser);
                    userName = profileUser.username;
                    userEmail = profileUser.email;
                    avatarUrl = profileUser.avatarUrl;
                    Log.d(TAG_AUTH, "signUp session saved for userId=" + userId);
                }

                if (userId.isEmpty()) {
                    throw new IOException("Signup returned without user id.");
                }

                postSuccess(callback, new SupabaseUser(userId, userEmail, userName, avatarUrl));
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
                Log.d(TAG_AUTH, "signIn user metadata. userId=" + userId
                        + ", avatarPresent=" + !avatarUrl.isEmpty());

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
                SupabaseUser profileUser = resolveProfileWithoutBlockingAuth(userId, userEmail, username, avatarUrl);
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

    /**
     * Checks the current password before asking Supabase Auth to send an email-change OTP.
     * The password is used only for this request and is never persisted locally.
     */
    public void requestEmailChange(String currentPassword, String newEmail, ResultCallback<Boolean> callback) {
        io.execute(() -> {
            try {
                ensureConfigured();
                if (!SessionManager.isLoggedIn(context)) {
                    throw new IOException("Please log in again before changing your email.");
                }

                String currentEmail = SessionManager.getEmail(context);
                if (currentEmail.isEmpty()) {
                    throw new IOException("Your current email is unavailable. Please log in again.");
                }

                // A password-grant request validates the password without replacing the active session.
                client.postAuth("/auth/v1/token?grant_type=password", new JSONObject()
                        .put("email", currentEmail)
                        .put("password", currentPassword));

                client.putAuthWithSession("/auth/v1/user", new JSONObject().put("email", newEmail));
                postSuccess(callback, true);
            } catch (Exception e) {
                Log.e(TAG_AUTH, "Email-change request failed: " + e.getMessage(), e);
                postError(callback, emailChangeMessageFromException(e));
            }
        });
    }

    /** Verifies Supabase's six-digit email-change OTP and synchronizes the retained session. */
    public void confirmEmailChange(String newEmail, String code, ResultCallback<SupabaseUser> callback) {
        io.execute(() -> {
            try {
                ensureConfigured();
                JSONObject response = client.postAuth("/auth/v1/verify", new JSONObject()
                        .put("type", "email_change")
                        .put("email", newEmail)
                        .put("token", code));
                JSONObject parsed = new JSONObject(response.getString("raw"));
                JSONObject session = parsed.optJSONObject("session");
                String access = parsed.optString("access_token", session == null ? "" : session.optString("access_token", ""));
                String refresh = parsed.optString("refresh_token", session == null ? "" : session.optString("refresh_token", ""));
                JSONObject user = parsed.optJSONObject("user");
                if (user == null) {
                    throw new IOException("Email confirmation succeeded but Supabase did not return the updated user.");
                }

                String userId = user.optString("id", SessionManager.getUserId(context));
                String confirmedEmail = user.optString("email", newEmail);
                SupabaseUser current = SessionManager.getUser(context);
                String username = current == null ? SessionManager.getUsername(context) : current.username;
                String avatarUrl = current == null ? SessionManager.getAvatarUrl(context) : current.avatarUrl;
                if (access.isEmpty()) {
                    access = client.refreshSession();
                    refresh = SessionManager.getRefreshToken(context);
                }
                if (access.isEmpty()) {
                    throw new IOException("Your email was confirmed, but the session could not be refreshed. Please log in again.");
                }

                SessionManager.saveSession(context, access, refresh, userId, confirmedEmail, username, avatarUrl);
                SupabaseUser updated = updateProfileEmail(userId, confirmedEmail, username, avatarUrl);
                SessionManager.saveUser(context, updated);
                postSuccess(callback, updated);
            } catch (Exception e) {
                Log.e(TAG_AUTH, "Email-change confirmation failed: " + e.getMessage(), e);
                postError(callback, emailChangeMessageFromException(e));
            }
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

    private SupabaseUser updateProfileEmail(String userId, String email, String username, String avatarUrl) throws Exception {
        JSONObject payload = new JSONObject()
                .put("id", userId)
                .put("email", safe(email))
                .put("username", safe(username))
                .put("avatar_url", safe(avatarUrl));
        JSONObject response = client.upsertRest("profiles?on_conflict=id", payload, true);
        JSONArray rows = new JSONArray(response.getString("raw"));
        JSONObject row = rows.length() > 0 ? rows.optJSONObject(0) : null;
        return row == null ? new SupabaseUser(userId, email, username, avatarUrl)
                : userFromProfile(row, userId, email, username, avatarUrl);
    }

    private String emailChangeMessageFromException(Exception e) {
        String raw = e.getMessage() == null ? "Email change failed." : e.getMessage();
        String lower = raw.toLowerCase();
        if (lower.contains("invalid_login_credentials")) return "Your current password is incorrect.";
        if (lower.contains("otp_expired") || lower.contains("token has expired")) return "That code has expired. Request a new one.";
        if (lower.contains("otp_disabled") || lower.contains("invalid token") || lower.contains("token is invalid")) return "That code is invalid. Check it and try again.";
        if (lower.contains("email_exists") || lower.contains("already registered")) return "That email address is already in use.";
        if (lower.contains("over_email_send_rate_limit") || lower.contains("rate limit")) return "Too many codes were requested. Please wait a few minutes.";
        return raw;
    }

    private SupabaseUser resolveProfileWithoutBlockingAuth(
            String userId,
            String email,
            String username,
            String avatarUrl
    ) {
        try {
            return loadOrCreateProfile(userId, email, username, avatarUrl);
        } catch (Exception e) {
            Log.e(TAG_AUTH, "Profile lookup failed after auth success: " + e.getMessage(), e);
            return new SupabaseUser(userId, email, username, avatarUrl);
        }
    }

    private SupabaseUser loadOrCreateProfile(
            String userId,
            String email,
            String username,
            String avatarUrl
    ) throws Exception {
        Log.d(TAG_AUTH, "Loading profile row. userId=" + userId);
        SupabaseUser loaded = fetchProfile(userId, email, username, avatarUrl);
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
                return userFromProfile(profile, userId, email, username, avatarUrl);
            }
        }
        return new SupabaseUser(userId, email, username, avatarUrl);
    }

    private SupabaseUser fetchProfile(
            String userId,
            String email,
            String username,
            String avatarUrl
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
        return userFromProfile(profile, userId, email, username, avatarUrl);
    }

    private SupabaseUser userFromProfile(
            JSONObject profile,
            String fallbackId,
            String fallbackEmail,
            String fallbackUsername,
            String fallbackAvatarUrl
    ) {
        String id = profile.optString("id", fallbackId);
        String email = firstNonEmpty(profile.optString("email", ""), fallbackEmail);
        String username = firstNonEmpty(profile.optString("username", ""), fallbackUsername);
        String avatarUrl = firstNonEmpty(profile.optString("avatar_url", ""), fallbackAvatarUrl);
        return new SupabaseUser(id, email, username, avatarUrl);
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
