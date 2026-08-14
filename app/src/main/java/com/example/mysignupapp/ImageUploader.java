package com.example.mysignupapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

/**
 * Production image uploader — replaces all Base64 Firebase storage.
 *
 * HOW TO USE:
 *
 * // Upload incident photo (during ambulance request):
 * ImageUploader.uploadIncident(context, imageUri, userPhone,
 *     url -> { reqRef.child("photoUrl").setValue(url); },
 *     err -> { Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show(); });
 *
 * // Upload profile photo:
 * ImageUploader.uploadProfile(context, imageUri, uid, "user",
 *     url -> { profileRef.child("photoUrl").setValue(url); },
 *     err -> { Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show(); });
 *
 * GRADLE — add to app/build.gradle dependencies:
 *   implementation 'com.squareup.okhttp3:okhttp:4.12.0'
 *
 * Replace all Glide image loading with:
 *   Glide.with(context).load(photoUrl).placeholder(R.drawable.ic_person_add)
 *        .circleCrop().into(imageView);
 */
public class ImageUploader {

    private static final String TAG              = "ImageUploader";
    private static final String BASE_URL         = "https://savannafibre.site/ambulance/api";
    private static final int    MAX_DIMENSION    = 1200;   // resize before upload
    private static final int    JPEG_QUALITY     = 75;     // compression quality

    private static final OkHttpClient   HTTP     = new OkHttpClient();
    private static final ExecutorService POOL    = Executors.newCachedThreadPool();
    private static final Handler        MAIN     = new Handler(Looper.getMainLooper());

    // ── Callbacks ─────────────────────────────────────────────────────────────
    public interface OnSuccess { void onSuccess(String imageUrl); }
    public interface OnError   { void onError(String errorMessage); }

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Upload an incident photo (taken during ambulance request).
     * Stores result URL in Firebase as: requests/{phone}/photoUrl
     */
    public static void uploadIncident(Context ctx, Uri imageUri,
                                       String userPhone,
                                       OnSuccess onSuccess,
                                       OnError onError) {
        POOL.execute(() -> {
            try {
                byte[] compressed = compressUri(ctx, imageUri, MAX_DIMENSION, JPEG_QUALITY);
                if (compressed == null) {
                    deliverError(onError, "Could not compress image");
                    return;
                }
                upload(BASE_URL + "/upload_incident.php",
                        compressed, "image/jpeg",
                        new String[]{"user_phone", userPhone},
                        onSuccess, onError);
            } catch (Exception e) {
                Log.e(TAG, "uploadIncident: " + e.getMessage());
                deliverError(onError, "Upload failed: " + e.getMessage());
            }
        });
    }

    /**
     * Upload a profile photo for user, driver, or admin.
     * @param uid  Firebase UID (for user/admin) or phone number (for driver)
     * @param role "user" | "driver" | "admin"
     */
    public static void uploadProfile(Context ctx, Uri imageUri,
                                      String uid, String role,
                                      OnSuccess onSuccess,
                                      OnError onError) {
        POOL.execute(() -> {
            try {
                // Profile photos: square crop + smaller size
                byte[] compressed = compressUri(ctx, imageUri, 600, 80);
                if (compressed == null) {
                    deliverError(onError, "Could not compress image");
                    return;
                }
                upload(BASE_URL + "/upload_profile.php",
                        compressed, "image/jpeg",
                        new String[]{"uid", uid, "role", role},
                        onSuccess, onError);
            } catch (Exception e) {
                Log.e(TAG, "uploadProfile: " + e.getMessage());
                deliverError(onError, "Upload failed: " + e.getMessage());
            }
        });
    }

    /**
     * Upload from a Bitmap directly (e.g. from camera thumbnail).
     */
    public static void uploadIncidentFromBitmap(Bitmap bmp, String userPhone,
                                                  OnSuccess onSuccess,
                                                  OnError onError) {
        POOL.execute(() -> {
            try {
                byte[] compressed = compressBitmap(bmp, MAX_DIMENSION, JPEG_QUALITY);
                upload(BASE_URL + "/upload_incident.php",
                        compressed, "image/jpeg",
                        new String[]{"user_phone", userPhone},
                        onSuccess, onError);
            } catch (Exception e) {
                deliverError(onError, "Upload failed: " + e.getMessage());
            }
        });
    }

    public static void uploadProfileFromBitmap(Bitmap bmp, String uid, String role,
                                                 OnSuccess onSuccess,
                                                 OnError onError) {
        POOL.execute(() -> {
            try {
                byte[] compressed = compressBitmap(bmp, 600, 80);
                upload(BASE_URL + "/upload_profile.php",
                        compressed, "image/jpeg",
                        new String[]{"uid", uid, "role", role},
                        onSuccess, onError);
            } catch (Exception e) {
                deliverError(onError, "Upload failed: " + e.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL — OkHttp multipart upload
    // ══════════════════════════════════════════════════════════════════════════

    private static void upload(String url,
                                byte[] imageBytes, String mimeType,
                                String[] extraParams,
                                OnSuccess onSuccess, OnError onError) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "photo.jpg",
                        RequestBody.create(imageBytes,
                                MediaType.parse(mimeType)));

        // Add extra params in pairs: [key, value, key, value, ...]
        for (int i = 0; i + 1 < extraParams.length; i += 2) {
            builder.addFormDataPart(extraParams[i], extraParams[i + 1]);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(builder.build())
                .build();

        HTTP.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                deliverError(onError, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Server response: " + body);

                try {
                    JSONObject json = new JSONObject(body);
                    if (json.optBoolean("success")) {
                        String imageUrl = json.getString("url");
                        MAIN.post(() -> { if (onSuccess != null) onSuccess.onSuccess(imageUrl); });
                    } else {
                        String msg = json.optString("message", "Upload failed");
                        deliverError(onError, msg);
                    }
                } catch (Exception e) {
                    deliverError(onError, "Server error: " + body);
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPRESSION
    // ══════════════════════════════════════════════════════════════════════════

    /** Compress a URI (gallery or file picker) to JPEG bytes */
    private static byte[] compressUri(Context ctx, Uri uri,
                                       int maxDim, int quality) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp == null) return null;
            return compressBitmap(bmp, maxDim, quality);
        } catch (Exception e) {
            Log.e(TAG, "compressUri: " + e.getMessage());
            return null;
        }
    }

    /** Scale + compress a Bitmap to JPEG bytes */
    public static byte[] compressBitmap(Bitmap src, int maxDim, int quality) {
        // Scale down if needed
        int w = src.getWidth(), h = src.getHeight();
        if (w > maxDim || h > maxDim) {
            float ratio = (float) maxDim / Math.max(w, h);
            src = Bitmap.createScaledBitmap(src, (int)(w * ratio), (int)(h * ratio), true);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        src.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return baos.toByteArray();
    }

    private static void deliverError(OnError onError, String msg) {
        MAIN.post(() -> { if (onError != null) onError.onError(msg); });
    }
}
