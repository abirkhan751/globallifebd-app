package com.globallifebd.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class GlobalLifeApp extends Application {

    private static final String TAG = "GlobalLifeApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            Log.e(TAG, "FirebaseApp initialization error: " + e.getMessage());
        }

        // Create notification channel
        AppFirebaseMessagingService.createNotificationChannel(this);

        // Fetch FCM token and sync
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                return;
            }

            // Get new FCM registration token
            String token = task.getResult();
            Log.d(TAG, "Current FCM Token: " + token);

            SharedPreferences prefs = getSharedPreferences(AppFirebaseMessagingService.PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(AppFirebaseMessagingService.KEY_FCM_TOKEN, token).apply();

            // Sync with backend
            AppFirebaseMessagingService.syncTokenToServer(this, token);
        });
    }
}
