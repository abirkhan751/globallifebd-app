package com.globallifebd.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AppFirebaseMessagingService extends FirebaseMessagingService {

    public static final String TAG = "AppFirebaseMessaging";
    public static final String CHANNEL_ID = "GLOBAL_LIFE_BD";
    public static final String PREF_NAME = "globallife_fcm_prefs";
    public static final String KEY_FCM_TOKEN = "fcm_token";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Registration Token: " + token);

        // Save token locally
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply();

        // Attempt server synchronization
        syncTokenToServer(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        String title = null;
        String body = null;
        String imageUrl = null;
        String targetUrl = null;

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
            if (remoteMessage.getNotification().getImageUrl() != null) {
                imageUrl = remoteMessage.getNotification().getImageUrl().toString();
            }
        }

        // Check data payload (overrides or supplements notification)
        if (remoteMessage.getData().size() > 0) {
            if (title == null || title.isEmpty()) {
                title = remoteMessage.getData().get("title");
            }
            if (body == null || body.isEmpty()) {
                body = remoteMessage.getData().get("body");
            }
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = remoteMessage.getData().get("image");
            }
            targetUrl = remoteMessage.getData().get("url");
        }

        if (title == null || title.isEmpty()) {
            title = getString(R.string.app_name);
        }
        if (body == null || body.isEmpty()) {
            body = "নতুন নোটিফিকেশন এসেছে";
        }
        if (targetUrl == null || targetUrl.isEmpty()) {
            targetUrl = getString(R.string.web_url);
        }

        displayNotification(title, body, imageUrl, targetUrl);
    }

    private void displayNotification(String title, String body, String imageUrl, String targetUrl) {
        createNotificationChannel(this);

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("target_url", targetUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        int notificationId = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent, pendingIntentFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        // Download and attach big picture image if available
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Bitmap bitmap = downloadBitmap(imageUrl);
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
                        .setStyle(new NotificationCompat.BigPictureStyle()
                                .bigPicture(bitmap)
                                .bigLargeIcon((Bitmap) null)
                                .setSummaryText(body));
            }
        }

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Notification permission missing: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to display notification: " + e.getMessage());
        }
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GLOBAL LIFE BD Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("গুরুত্বপূর্ণ নোটিশ, অফার ও ইনকাম আপডেট");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Bitmap downloadBitmap(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.connect();
            InputStream input = conn.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.w(TAG, "Failed to download notification image: " + e.getMessage());
            return null;
        }
    }

    /**
     * Send device FCM token to Laravel backend /api/fcm-token with session cookies.
     */
    public static void syncTokenToServer(Context context, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                String baseUrl = context.getString(R.string.web_url);
                String apiEndpoint = baseUrl + "/api/fcm-token";

                String cookies = CookieManager.getInstance().getCookie(baseUrl);

                URL url = new URL(apiEndpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");

                if (cookies != null && !cookies.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookies);
                }

                JSONObject payload = new JSONObject();
                payload.put("token", token);
                payload.put("device_type", "android");
                payload.put("device_name", Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");

                byte[] postBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(postBytes.length);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Token sync HTTP response code: " + responseCode);
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Token sync exception: " + e.getMessage());
            }
        }).start();
    }
}

