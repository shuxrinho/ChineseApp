package com.example.chineseapp.supabase;

import android.content.Context;
import android.util.Log;

import com.example.chineseapp.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseClient {

    private static final String TAG_HTTP = "CHAPP_SB_HTTP";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Context context;
    private final OkHttpClient client;
    private final Object refreshLock = new Object();

    public SupabaseClient(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public JSONObject postAuth(String path, JSONObject payload) throws IOException {
        Log.d(TAG_HTTP, "postAuth path=" + path + ", url=" + BuildConfig.SUPABASE_URL + path);
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request request = baseBuilder(path)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
        return executeJson(request);
    }

    public JSONObject postAuthWithSession(String path, JSONObject payload) throws IOException {
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request.Builder builder = baseBuilder(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + SessionManager.getAccessToken(context))
                .post(body);
        return executeJsonWithAuthRetry(builder.build(), true);
    }

    public JSONObject patchAuthWithSession(String path, JSONObject payload) throws IOException {
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request.Builder builder = baseBuilder(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + SessionManager.getAccessToken(context))
                .patch(body);
        return executeJsonWithAuthRetry(builder.build(), true);
    }

    public JSONObject postRpc(String functionName, JSONObject payload, boolean withAuth) throws IOException {
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request.Builder builder = baseBuilder("/rest/v1/rpc/" + functionName)
                .header("Content-Type", "application/json")
                .post(body);

        if (withAuth) {
            String token = SessionManager.getAccessToken(context);
            builder.header("Authorization", "Bearer " + token);
        }

        return executeJsonWithAuthRetry(builder.build(), withAuth);
    }

    public JSONObject postRpcWithoutBody(String functionName, boolean withAuth) throws IOException {
        return postRpc(functionName, new JSONObject(), withAuth);
    }

    public JSONObject getRest(String path, boolean withAuth) throws IOException {
        Request.Builder builder = baseBuilder("/rest/v1/" + path)
                .get();

        if (withAuth) {
            String token = SessionManager.getAccessToken(context);
            builder.header("Authorization", "Bearer " + token);
        }

        return executeJsonWithAuthRetry(builder.build(), withAuth);
    }

    public JSONObject upsertRest(String tableAndQuery, JSONObject payload, boolean withAuth) throws IOException {
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request.Builder builder = baseBuilder("/rest/v1/" + tableAndQuery)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .post(body);

        if (withAuth) {
            String token = SessionManager.getAccessToken(context);
            builder.header("Authorization", "Bearer " + token);
        }

        return executeJsonWithAuthRetry(builder.build(), withAuth);
    }

    public void postAuthNoBody(String path, boolean withAuth) throws IOException {
        RequestBody body = RequestBody.create("{}", JSON);
        Request.Builder builder = baseBuilder(path)
                .header("Content-Type", "application/json")
                .post(body);

        if (withAuth) {
            String token = SessionManager.getAccessToken(context);
            builder.header("Authorization", "Bearer " + token);
        }

        executeJsonWithAuthRetry(builder.build(), withAuth);
    }

    public String refreshSession() throws IOException {
        return refreshAccessToken();
    }

    private Request.Builder baseBuilder(String path) {
        return new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + path)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Accept", "application/json");
    }

    private JSONObject executeJson(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            String bodyPreview = body.length() > 800 ? body.substring(0, 800) + "..." : body;

            if (!response.isSuccessful()) {
                Log.e(TAG_HTTP, "HTTP FAIL code=" + response.code() + ", url=" + request.url() + ", body=" + bodyPreview);
                throw new SupabaseHttpException(response.code(), body);
            }
            String successLogBody = request.url().encodedPath().startsWith("/auth/v1/")
                    ? "<redacted auth response>"
                    : bodyPreview;
            Log.d(TAG_HTTP, "HTTP OK code=" + response.code() + ", url=" + request.url() + ", body=" + successLogBody);

            JSONObject wrapper = new JSONObject();
            wrapper.put("raw", body);
            return wrapper;
        } catch (Exception e) {
            Log.e(TAG_HTTP, "HTTP EXCEPTION url=" + request.url() + ", message=" + e.getMessage(), e);
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(e);
        }
    }

    private JSONObject executeJsonWithAuthRetry(Request request, boolean withAuth) throws IOException {
        try {
            return executeJson(request);
        } catch (SupabaseHttpException e) {
            if (!withAuth || !isJwtExpiredError(e)) {
                throw e;
            }

            String refreshedToken = refreshAccessToken();
            Request retried = request.newBuilder()
                    .header("Authorization", "Bearer " + refreshedToken)
                    .build();
            return executeJson(retried);
        }
    }

    private boolean isJwtExpiredError(SupabaseHttpException exception) {
        if (exception == null) return false;
        if (exception.code != 400 && exception.code != 401 && exception.code != 403) return false;
        String body = exception.body == null ? "" : exception.body.toLowerCase();
        return body.contains("jwt expired")
                || body.contains("invalid jwt")
                || body.contains("\"exp\" claim timestamp check failed")
                || body.contains("\\\"exp\\\" claim timestamp check failed")
                || body.contains("exp claim timestamp check failed")
                || body.contains("claim timestamp check failed");
    }

    private String refreshAccessToken() throws IOException {
        synchronized (refreshLock) {
            String refresh = SessionManager.getRefreshToken(context);
            if (refresh == null || refresh.trim().isEmpty()) {
                throw new IOException("Session expired. Please log in again.");
            }

            JSONObject payload = new JSONObject();
            try {
                payload.put("refresh_token", refresh);
            } catch (JSONException e) {
                throw new IOException(e);
            }

            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request request = baseBuilder("/auth/v1/token?grant_type=refresh_token")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            JSONObject response = executeJson(request);
            try {
                JSONObject parsed = new JSONObject(response.getString("raw"));
                String access = parsed.optString("access_token", "");
                String refreshToken = parsed.optString("refresh_token", "");

                if (access.isEmpty()) {
                    JSONObject session = parsed.optJSONObject("session");
                    if (session != null) {
                        access = session.optString("access_token", "");
                        if (refreshToken.isEmpty()) {
                            refreshToken = session.optString("refresh_token", "");
                        }
                    }
                }

                if (access.isEmpty()) {
                    throw new IOException("Session refresh failed. Please log in again.");
                }

                if (refreshToken.isEmpty()) {
                    refreshToken = refresh;
                }

                SessionManager.saveSession(
                        context,
                        access,
                        refreshToken,
                        SessionManager.getUserId(context),
                        SessionManager.getEmail(context),
                        SessionManager.getUsername(context)
                );
                return access;
            } catch (Exception e) {
                if (e instanceof IOException) throw (IOException) e;
                throw new IOException(e);
            }
        }
    }

    private static class SupabaseHttpException extends IOException {
        final int code;
        final String body;

        SupabaseHttpException(int code, String body) {
            super("HTTP " + code + ": " + body);
            this.code = code;
            this.body = body;
        }
    }

    public static boolean hasConfig() {
        return !BuildConfig.SUPABASE_URL.isEmpty()
                && !BuildConfig.SUPABASE_ANON_KEY.isEmpty()
                && !BuildConfig.SUPABASE_URL.startsWith("YOUR_")
                && !BuildConfig.SUPABASE_ANON_KEY.startsWith("YOUR_");
    }
}
