package com.example.chineseapp.supabase;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SupabaseProfileRepository {
    private static final String TAG_PROFILE_REPO = "CHAPP_SB_PROFILE";
    private final Context context;
    private final SupabaseClient client;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public SupabaseProfileRepository(Context context) {
        this.context = context.getApplicationContext();
        this.client = new SupabaseClient(this.context);
    }

    public void updateAvatarUrl(String avatarUrl, ResultCallback<SupabaseUser> callback) {
        Log.d(TAG_PROFILE_REPO, "Queueing avatar_url update. avatarUrlPresent="
                + (avatarUrl != null && !avatarUrl.isEmpty()));
        io.execute(() -> {
            try {
                Log.d(TAG_PROFILE_REPO, "avatar_url update worker started. hasConfig="
                        + SupabaseClient.hasConfig()
                        + ", loggedIn=" + SessionManager.isLoggedIn(context)
                        + ", userId=" + SessionManager.getUserId(context));
                if (!SupabaseClient.hasConfig()) {
                    throw new IllegalStateException("Supabase is not configured.");
                }
                if (!SessionManager.isLoggedIn(context)) {
                    throw new IllegalStateException("Please log in to update your profile.");
                }

                SupabaseUser current = SessionManager.getUser(context);
                if (current == null) {
                    throw new IllegalStateException("Please log in to update your profile.");
                }
                Log.d(TAG_PROFILE_REPO, "Current profile before avatar update. id=" + current.id
                        + ", emailPresent=" + (current.email != null && !current.email.isEmpty())
                        + ", usernamePresent=" + (current.username != null && !current.username.isEmpty()));

                JSONObject payload = new JSONObject()
                        .put("id", current.id)
                        .put("email", safe(current.email))
                        .put("username", safe(current.username))
                        .put("avatar_url", safe(avatarUrl));

                Log.d(TAG_PROFILE_REPO, "Sending profiles upsert for avatar_url. id=" + current.id);
                JSONObject response = client.upsertRest("profiles?on_conflict=id", payload, true);
                SupabaseUser updated = current;
                JSONArray rows = new JSONArray(response.getString("raw"));
                Log.d(TAG_PROFILE_REPO, "profiles upsert response rows=" + rows.length());
                if (rows.length() > 0) {
                    JSONObject row = rows.optJSONObject(0);
                    if (row != null) {
                        updated = new SupabaseUser(
                                row.optString("id", current.id),
                                firstNonEmpty(row.optString("email", ""), current.email),
                                firstNonEmpty(row.optString("username", ""), current.username),
                                firstNonEmpty(row.optString("avatar_url", ""), avatarUrl)
                        );
                    }
                }

                SessionManager.saveUser(context, updated);
                Log.d(TAG_PROFILE_REPO, "Saved updated user in session. avatarPresent="
                        + (updated.avatarUrl != null && !updated.avatarUrl.isEmpty()));
                postSuccess(callback, updated);
            } catch (Exception e) {
                Log.e(TAG_PROFILE_REPO, "avatar_url update failed: " + e.getMessage(), e);
                postError(callback, e.getMessage() == null ? "Profile update failed." : e.getMessage());
            }
        });
    }

    public void updateUsername(String username, ResultCallback<SupabaseUser> callback) {
        String cleanUsername = safe(username);
        Log.d(TAG_PROFILE_REPO, "Queueing username update. usernameLength=" + cleanUsername.length());
        io.execute(() -> {
            try {
                Log.d(TAG_PROFILE_REPO, "username update worker started. hasConfig="
                        + SupabaseClient.hasConfig()
                        + ", loggedIn=" + SessionManager.isLoggedIn(context)
                        + ", userId=" + SessionManager.getUserId(context));
                if (!SupabaseClient.hasConfig()) {
                    throw new IllegalStateException("Supabase is not configured.");
                }
                if (!SessionManager.isLoggedIn(context)) {
                    throw new IllegalStateException("Please log in to update your username.");
                }

                JSONObject payload = new JSONObject().put("new_username", cleanUsername);
                JSONObject response = client.postRpc("update_profile_username", payload, true);
                JSONArray rows = new JSONArray(response.getString("raw"));
                Log.d(TAG_PROFILE_REPO, "username update response rows=" + rows.length());

                SupabaseUser current = SessionManager.getUser(context);
                SupabaseUser updated = current;
                if (rows.length() > 0) {
                    JSONObject row = rows.optJSONObject(0);
                    if (row != null) {
                        updated = new SupabaseUser(
                                row.optString("id", current == null ? SessionManager.getUserId(context) : current.id),
                                firstNonEmpty(row.optString("email", ""), current == null ? SessionManager.getEmail(context) : current.email),
                                firstNonEmpty(row.optString("username", ""), cleanUsername),
                                firstNonEmpty(row.optString("avatar_url", ""), current == null ? SessionManager.getAvatarUrl(context) : current.avatarUrl)
                        );
                    }
                }

                if (updated == null) {
                    updated = new SupabaseUser(
                            SessionManager.getUserId(context),
                            SessionManager.getEmail(context),
                            cleanUsername,
                            SessionManager.getAvatarUrl(context)
                    );
                }

                SessionManager.saveUser(context, updated);
                Log.d(TAG_PROFILE_REPO, "Saved updated username in session. userId=" + updated.id);
                postSuccess(callback, updated);
            } catch (Exception e) {
                Log.e(TAG_PROFILE_REPO, "username update failed: " + e.getMessage(), e);
                postError(callback, usernameMessageFromException(e));
            }
        });
    }

    private String usernameMessageFromException(Exception e) {
        String raw = e.getMessage() == null ? "Username update failed." : e.getMessage();
        String lower = raw.toLowerCase();
        if (lower.contains("username_taken")
                || lower.contains("duplicate key")
                || lower.contains("profiles_username_unique")) {
            return "That username is already taken.";
        }
        if (lower.contains("username_invalid")) {
            return "Use 3-24 letters, numbers, or underscores.";
        }
        if (lower.contains("auth_required")) {
            return "Please log in to update your username.";
        }
        if (lower.contains("function public.update_profile_username")) {
            return "Supabase username RPC is missing. Run the updated SQL setup.";
        }
        if (lower.contains("column reference")
                && lower.contains("ambiguous")
                && lower.contains("id")) {
            return "Supabase username RPC is outdated. Replace it with the latest SQL.";
        }
        return raw;
    }

    private String firstNonEmpty(String preferred, String fallback) {
        String value = safe(preferred);
        return value.isEmpty() ? safe(fallback) : value;
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
