package com.globallifebd.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 1000; // 1.0 second

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorWhite));
        }

        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            android.widget.TextView txtSplashVersion = findViewById(R.id.txtSplashVersion);
            if (txtSplashVersion != null && version != null) {
                String bnVersion = version
                        .replace("0", "০")
                        .replace("1", "১")
                        .replace("2", "২")
                        .replace("3", "৩")
                        .replace("4", "৪")
                        .replace("5", "৫")
                        .replace("6", "৬")
                        .replace("7", "৭")
                        .replace("8", "৮")
                        .replace("9", "৯");
                txtSplashVersion.setText("ভার্সনঃ " + bnVersion);
            }
        } catch (Exception ignored) {}

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, SPLASH_DURATION);
    }
}

