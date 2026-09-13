package com.globallifebd.app;

import android.app.Application;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;
import com.onesignal.Continue;

public class GlobalLifeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Enable verbose OneSignal logging to help debug
        OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);

        // OneSignal Initialization with App ID
        String appId = getString(R.string.onesignal_app_id);
        OneSignal.initWithContext(this, appId);

        // Request native notification permission prompt
        OneSignal.getNotifications().requestPermission(true, Continue.with(r -> {
            // Handled
        }));
    }
}
