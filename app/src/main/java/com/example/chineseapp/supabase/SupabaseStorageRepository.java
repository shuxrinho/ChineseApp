package com.example.chineseapp.supabase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.chineseapp.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseStorageRepository {
    private static final String TAG_STORAGE = "CHAPP_SB_STORAGE";
    private static final int AVATAR_SIZE_PX = 512;
    private static final int DECODE_MAX_PX = 2048;
    private static final int MAX_AVATAR_BYTES = 4 * 1024 * 1024;
    private static final MediaType JPEG = MediaType.get("image/jpeg");

    private final Context context;
    private final OkHttpClient client;
    private final SupabaseClient supabaseClient;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public SupabaseStorageRepository(Context context) {
        this.context = context.getApplicationContext();
        this.supabaseClient = new SupabaseClient(this.context);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void uploadProfilePhoto(Uri fileUri, ResultCallback<String> callback) {
        Log.d(TAG_STORAGE, "Queueing avatar upload. uri=" + fileUri);
        io.execute(() -> {
            try {
                Log.d(TAG_STORAGE, "Avatar upload worker started. hasConfig=" + SupabaseClient.hasConfig()
                        + ", loggedIn=" + SessionManager.isLoggedIn(context)
                        + ", urlConfigured=" + !BuildConfig.SUPABASE_URL.isEmpty());
                if (!SupabaseClient.hasConfig()) {
                    throw new IOException("Supabase is not configured.");
                }
                if (!SessionManager.isLoggedIn(context)) {
                    throw new IOException("Please log in to update your profile photo.");
                }

                String userId = SessionManager.getUserId(context);
                String objectPath = userId + "/avatar.jpg";
                Log.d(TAG_STORAGE, "Preparing avatar bytes. userId=" + userId
                        + ", objectPath=" + objectPath);
                byte[] bytes = prepareAvatarBytes(fileUri);
                Log.d(TAG_STORAGE, "Prepared avatar bytes. byteCount=" + bytes.length);

                if (bytes.length > MAX_AVATAR_BYTES) {
                    throw new IOException("Image is too large. Choose a smaller photo.");
                }

                uploadBytesWithAuthRetry(objectPath, bytes);

                String publicUrl = BuildConfig.SUPABASE_URL
                        + "/storage/v1/object/public/avatars/"
                        + objectPath
                        + "?v="
                        + System.currentTimeMillis();
                Log.d(TAG_STORAGE, "Avatar upload completed. publicUrl=" + publicUrl);
                postSuccess(callback, publicUrl);
            } catch (Exception e) {
                Log.e(TAG_STORAGE, "Avatar upload failed: " + e.getMessage(), e);
                postError(callback, e.getMessage() == null ? "Upload failed." : e.getMessage());
            }
        });
    }

    private byte[] prepareAvatarBytes(Uri uri) throws IOException {
        Log.d(TAG_STORAGE, "Decoding selected image. uri=" + uri);
        Bitmap source = decodeScaled(uri);
        if (source == null) {
            throw new IOException("Could not read selected image.");
        }

        Log.d(TAG_STORAGE, "Decoded selected image. width=" + source.getWidth()
                + ", height=" + source.getHeight());
        Bitmap square = Bitmap.createBitmap(AVATAR_SIZE_PX, AVATAR_SIZE_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(square);
        canvas.drawColor(Color.WHITE);

        int srcSize = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - srcSize) / 2;
        int top = (source.getHeight() - srcSize) / 2;
        Rect src = new Rect(left, top, left + srcSize, top + srcSize);
        Rect dest = new Rect(0, 0, AVATAR_SIZE_PX, AVATAR_SIZE_PX);
        canvas.drawBitmap(source, src, dest, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        square.compress(Bitmap.CompressFormat.JPEG, 88, out);
        Log.d(TAG_STORAGE, "Compressed avatar. outputBytes=" + out.size()
                + ", targetSize=" + AVATAR_SIZE_PX);

        source.recycle();
        square.recycle();
        return out.toByteArray();
    }

    private void uploadBytesWithAuthRetry(String objectPath, byte[] bytes) throws IOException {
        UploadResponse first = executeUpload(objectPath, bytes, SessionManager.getAccessToken(context));
        if (first.successful) return;

        if (!isExpiredJwtStorageError(first.code, first.body)) {
            throw new IOException("Upload failed: " + first.code + " " + first.body);
        }

        Log.w(TAG_STORAGE, "Storage upload token expired. Refreshing session and retrying once.");
        String refreshedToken = supabaseClient.refreshSession();
        UploadResponse retried = executeUpload(objectPath, bytes, refreshedToken);
        if (!retried.successful) {
            throw new IOException("Upload failed after token refresh: " + retried.code + " " + retried.body);
        }
    }

    private UploadResponse executeUpload(String objectPath, byte[] bytes, String accessToken) throws IOException {
        RequestBody requestBody = RequestBody.create(bytes, JPEG);
        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/storage/v1/object/avatars/" + objectPath)
                .header("Authorization", "Bearer " + accessToken)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "image/jpeg")
                .header("Cache-Control", "3600")
                .header("x-upsert", "true")
                .post(requestBody)
                .build();

        Log.d(TAG_STORAGE, "Uploading avatar to Supabase Storage. url=" + request.url());
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            String bodyPreview = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            Log.d(TAG_STORAGE, "Storage upload response. code=" + response.code()
                    + ", successful=" + response.isSuccessful()
                    + ", body=" + bodyPreview);
            return new UploadResponse(response.code(), response.isSuccessful(), body);
        }
    }

    private boolean isExpiredJwtStorageError(int code, String body) {
        if (code != 400 && code != 401 && code != 403) return false;
        String lower = body == null ? "" : body.toLowerCase();
        return lower.contains("jwt expired")
                || lower.contains("invalid jwt")
                || lower.contains("\"exp\" claim timestamp check failed")
                || lower.contains("\\\"exp\\\" claim timestamp check failed")
                || lower.contains("exp claim timestamp check failed")
                || lower.contains("claim timestamp check failed");
    }

    private Bitmap decodeScaled(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Could not open selected image.");
            }
            BitmapFactory.decodeStream(input, null, bounds);
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight);
        Log.d(TAG_STORAGE, "Image decode bounds. width=" + bounds.outWidth
                + ", height=" + bounds.outHeight
                + ", sampleSize=" + decode.inSampleSize);

        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Could not reopen selected image.");
            }
            return BitmapFactory.decodeStream(input, null, decode);
        }
    }

    private int calculateInSampleSize(int width, int height) {
        int inSampleSize = 1;
        int largest = Math.max(width, height);
        while (largest / inSampleSize > DECODE_MAX_PX) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private <T> void postSuccess(ResultCallback<T> callback, T value) {
        main.post(() -> callback.onSuccess(value));
    }

    private <T> void postError(ResultCallback<T> callback, String error) {
        main.post(() -> callback.onError(error));
    }

    private static final class UploadResponse {
        final int code;
        final boolean successful;
        final String body;

        UploadResponse(int code, boolean successful, String body) {
            this.code = code;
            this.successful = successful;
            this.body = body == null ? "" : body;
        }
    }
}
