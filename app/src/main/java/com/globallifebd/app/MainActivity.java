package com.globallifebd.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_RESULT_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1003;
    public static final String NOTIFICATION_CHANNEL_ID = AppFirebaseMessagingService.CHANNEL_ID;

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout offlineLayout;
    private FrameLayout splashOverlay;
    private Button btnRetry;

    private ValueCallback<Uri[]> uploadMessage;
    private String cameraPhotoPath;
    private long backPressedTime = 0;
    private String targetUrl;
    private long splashStartTime = 0;
    private static final long MIN_SPLASH_DURATION = 1500;
    private boolean isSplashDismissing = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashStartTime = System.currentTimeMillis();
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorWhite));
        }

        targetUrl = getString(R.string.web_url);

        // Check if opened from a notification deep link
        if (getIntent() != null && getIntent().hasExtra("target_url")) {
            String deepLink = getIntent().getStringExtra("target_url");
            if (deepLink != null && !deepLink.isEmpty()) {
                targetUrl = deepLink;
            }
        }

        webView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        offlineLayout = findViewById(R.id.offlineLayout);
        splashOverlay = findViewById(R.id.splashOverlay);
        btnRetry = findViewById(R.id.btnRetry);

        // Dynamically set version on splash overlay
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            TextView txtSplashVersion = findViewById(R.id.txtSplashVersion);
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

        createNotificationChannel();
        initSwipeRefresh();
        initWebView();
        checkPermissions();
        startSplashLogoAnimation();

        // Safety timeout to ensure splash doesn't get stuck indefinitely
        new Handler(Looper.getMainLooper()).postDelayed(this::dismissSplash, 6000);

        btnRetry.setOnClickListener(v -> {
            if (isNetworkConnected()) {
                offlineLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                if (splashOverlay != null) {
                    splashOverlay.setAlpha(1f);
                    splashOverlay.setVisibility(View.VISIBLE);
                    startSplashLogoAnimation();
                }
                webView.reload();
            } else {
                Toast.makeText(this, R.string.offline_title, Toast.LENGTH_SHORT).show();
            }
        });

        if (savedInstanceState == null) {
            loadTargetPage();
        } else {
            webView.restoreState(savedInstanceState);
        }

        checkForAppUpdate();
    }

    private void checkForAppUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL("https://globallifebd.com/api/app-version");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "GlobalLifeBDApp/1.0.0");
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    boolean forceUpdate = json.optBoolean("force_update", false);
                    int minVersionCode = json.optInt("min_version_code", 0);
                    int currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;

                    if ((forceUpdate || currentVersionCode < minVersionCode) && minVersionCode > currentVersionCode) {
                        String downloadUrl = json.optString("download_url", "https://globallifebd.com/download/app");
                        String latestVersion = json.optString("latest_version", "");
                        runOnUiThread(() -> showForceUpdateDialog(latestVersion, downloadUrl));
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showForceUpdateDialog(String newVersion, String downloadUrl) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("নতুন আপডেট উপলব্ধ (v" + newVersion + ")")
                .setMessage("আপনার অ্যাপটি পুরোনো ভার্সনে চলছে। ড্যাশবোর্ড ও সেবা সচল রাখতে এখনই নতুন ভার্সনে আপডেট করুন।")
                .setCancelable(false)
                .setPositiveButton("এখনই আপডেট করুন", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    startActivity(browserIntent);
                    finish();
                })
                .show();
    }

    private void startSplashLogoAnimation() {
        try {
            ImageView imgSplashLogo = findViewById(R.id.imgSplashLogo);
            TextView txtSplashTitle = findViewById(R.id.txtSplashTitle);
            TextView txtSplashVersion = findViewById(R.id.txtSplashVersion);

            if (imgSplashLogo != null) {
                imgSplashLogo.setAlpha(0f);
                imgSplashLogo.setScaleX(0.5f);
                imgSplashLogo.setScaleY(0.5f);

                imgSplashLogo.animate()
                        .alpha(1.0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(650)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .withEndAction(() -> startLogoBreathingLoop(imgSplashLogo))
                        .start();
            }

            if (txtSplashTitle != null) {
                txtSplashTitle.setAlpha(0f);
                txtSplashTitle.setTranslationY(20f);
                txtSplashTitle.animate()
                        .alpha(1.0f)
                        .translationY(0f)
                        .setDuration(500)
                        .setStartDelay(180)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }

            if (txtSplashVersion != null) {
                txtSplashVersion.setAlpha(0f);
                txtSplashVersion.animate()
                        .alpha(1.0f)
                        .setDuration(400)
                        .setStartDelay(280)
                        .start();
            }
        } catch (Exception ignored) {}
    }

    private void startLogoBreathingLoop(View logoView) {
        if (logoView == null || isFinishing() || splashOverlay == null || splashOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        logoView.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(900)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE && !isFinishing()) {
                        logoView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(900)
                                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                                .withEndAction(() -> startLogoBreathingLoop(logoView))
                                .start();
                    }
                })
                .start();
    }

    private void dismissSplash() {
        if (splashOverlay == null || splashOverlay.getVisibility() != View.VISIBLE || isSplashDismissing) {
            return;
        }
        long elapsedTime = System.currentTimeMillis() - splashStartTime;
        if (elapsedTime < MIN_SPLASH_DURATION) {
            long remaining = MIN_SPLASH_DURATION - elapsedTime;
            new Handler(Looper.getMainLooper()).postDelayed(this::performDismissSplash, remaining);
        } else {
            performDismissSplash();
        }
    }

    private void performDismissSplash() {
        if (splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE && !isSplashDismissing) {
            isSplashDismissing = true;
            ImageView imgSplashLogo = findViewById(R.id.imgSplashLogo);
            if (imgSplashLogo != null) {
                imgSplashLogo.animate().cancel();
                imgSplashLogo.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(0f)
                        .setDuration(250)
                        .start();
            }
            splashOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        splashOverlay.setVisibility(View.GONE);
                        isSplashDismissing = false;
                        if (imgSplashLogo != null) {
                            imgSplashLogo.animate().cancel();
                        }
                    });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("target_url")) {
            String deepLink = intent.getStringExtra("target_url");
            if (deepLink != null && !deepLink.isEmpty() && webView != null) {
                webView.loadUrl(deepLink);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Global Life BD নোটিফিকেশন";
            String description = "গুরুত্বপূর্ণ নোটিশ, অফার ও ইনকাম আপডেট";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void loadTargetPage() {
        if (isNetworkConnected()) {
            offlineLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(targetUrl);
        } else {
            offlineLayout.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
        }
    }

    private boolean isSwipeRefreshAllowed = true;

    public void setSwipeRefreshEnabled(final boolean enabled) {
        runOnUiThread(() -> {
            isSwipeRefreshAllowed = enabled;
            if (swipeRefreshLayout != null) {
                if (!enabled) {
                    swipeRefreshLayout.setEnabled(false);
                } else {
                    swipeRefreshLayout.setEnabled(webView != null && webView.getScrollY() == 0);
                }
            }
        });
    }

    private void initSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary, R.color.colorAccent);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isNetworkConnected()) {
                webView.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
                offlineLayout.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
            }
        });

        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
            if (!isSwipeRefreshAllowed) {
                return true;
            }
            return child.canScrollVertically(-1);
        });

        webView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (isSwipeRefreshAllowed) {
                swipeRefreshLayout.setEnabled(webView.getScrollY() == 0);
            } else {
                swipeRefreshLayout.setEnabled(false);
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        String customUserAgent = settings.getUserAgentString() + " GlobalLifeBDApp/1.0.0";
        settings.setUserAgentString(customUserAgent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                ServiceWorkerController swController = ServiceWorkerController.getInstance();
                swController.setServiceWorkerClient(new ServiceWorkerClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                        return super.shouldInterceptRequest(request);
                    }
                });
            } catch (Exception ignored) {}
        }

        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new CustomWebChromeClient());

        // Native Notification & App Bridge
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidNotification");
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidApp");
        webView.addJavascriptInterface(new FcmBridgeInterface(this), "AndroidFCM");

        // File download support
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                request.setTitle(fileName);
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this, "ডাউনলোড শুরু হয়েছে...", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "ডাউনলোড সম্পন্ন করা যায়নি", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showNotification(String title, String message, String url) {
            try {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

                Intent intent = new Intent(mContext, MainActivity.class);
                if (url != null && !url.isEmpty()) {
                    intent.putExtra("target_url", url);
                }
                PendingIntent pendingIntent = PendingIntent.getActivity(mContext, (int) System.currentTimeMillis(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
                builder.setContentIntent(pendingIntent);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify((int) System.currentTimeMillis(), builder.build());
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void setSwipeRefreshEnabled(boolean enabled) {
            MainActivity.this.setSwipeRefreshEnabled(enabled);
        }
    }

    public class FcmBridgeInterface {
        Context mContext;

        FcmBridgeInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public String getToken() {
            android.content.SharedPreferences prefs = mContext.getSharedPreferences(AppFirebaseMessagingService.PREF_NAME, MODE_PRIVATE);
            return prefs.getString(AppFirebaseMessagingService.KEY_FCM_TOKEN, "");
        }

        @JavascriptInterface
        public void syncToken() {
            android.content.SharedPreferences prefs = mContext.getSharedPreferences(AppFirebaseMessagingService.PREF_NAME, MODE_PRIVATE);
            String token = prefs.getString(AppFirebaseMessagingService.KEY_FCM_TOKEN, "");
            if (!token.isEmpty()) {
                AppFirebaseMessagingService.syncTokenToServer(mContext, token);
            }
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            return handleUrlNavigation(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrlNavigation(url);
        }

        private boolean handleUrlNavigation(String url) {
            if (url == null) return false;

            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")
                    || url.startsWith("whatsapp:") || url.contains("wa.me")
                    || url.startsWith("tg:") || url.contains("t.me")
                    || url.startsWith("intent:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "সংশ্লিষ্ট অ্যাপটি ফোনে নেই", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }

            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            swipeRefreshLayout.setRefreshing(false);
            dismissSplash();
            super.onPageFinished(view, url);

            // Sync FCM Token with session cookies when page finishes loading
            try {
                android.content.SharedPreferences prefs = getSharedPreferences(AppFirebaseMessagingService.PREF_NAME, MODE_PRIVATE);
                String token = prefs.getString(AppFirebaseMessagingService.KEY_FCM_TOKEN, null);
                if (token != null && !token.isEmpty()) {
                    AppFirebaseMessagingService.syncTokenToServer(MainActivity.this, token);
                }
            } catch (Exception ignored) {}

            // Inject scroll helper to prevent SwipeRefreshLayout from stealing scrolls inside sidebar or scrollable containers
            try {
                String js = "(function(){" +
                        "if(window.__glpScrollHelper)return;window.__glpScrollHelper=true;" +
                        "document.addEventListener('touchstart',function(e){" +
                        "  var el=e.target;" +
                        "  var isScrollable=false;" +
                        "  while(el && el!==document.body && el!==document.documentElement){" +
                        "    if(el.id==='sidebar'||el.id==='sidebar-menu-area'||(el.classList&&el.classList.contains('is-open'))){" +
                        "      isScrollable=true;break;" +
                        "    }" +
                        "    var style=window.getComputedStyle(el);" +
                        "    if((style.overflowY==='auto'||style.overflowY==='scroll')&&el.scrollHeight>el.clientHeight){" +
                        "      isScrollable=true;break;" +
                        "    }" +
                        "    el=el.parentElement;" +
                        "  }" +
                        "  if(window.AndroidApp&&window.AndroidApp.setSwipeRefreshEnabled){" +
                        "    window.AndroidApp.setSwipeRefreshEnabled(!isScrollable);" +
                        "  }else if(window.AndroidNotification&&window.AndroidNotification.setSwipeRefreshEnabled){" +
                        "    window.AndroidNotification.setSwipeRefreshEnabled(!isScrollable);" +
                        "  }" +
                        "},{passive:true});" +
                        "document.addEventListener('touchend',function(e){" +
                        "  var sb=document.getElementById('sidebar');" +
                        "  var isOpen=sb&&(sb.classList.contains('is-open')||sb.style.transform==='translateX(0px)'||sb.style.transform==='translateX(0)');" +
                        "  if(!isOpen && window.scrollY===0){" +
                        "    if(window.AndroidApp&&window.AndroidApp.setSwipeRefreshEnabled){window.AndroidApp.setSwipeRefreshEnabled(true);}" +
                        "    else if(window.AndroidNotification&&window.AndroidNotification.setSwipeRefreshEnabled){window.AndroidNotification.setSwipeRefreshEnabled(true);}" +
                        "  }" +
                        "},{passive:true});" +
                        "})();";
                view.evaluateJavascript(js, null);
            } catch (Exception ignored) {}
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                swipeRefreshLayout.setRefreshing(false);
                dismissSplash();
                if (!isNetworkConnected()) {
                    offlineLayout.setVisibility(View.VISIBLE);
                    webView.setVisibility(View.GONE);
                }
            }
            super.onReceivedError(view, request, error);
        }
    }

    private class CustomWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                request.grant(request.getResources());
            }
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (uploadMessage != null) {
                uploadMessage.onReceiveValue(null);
                uploadMessage = null;
            }
            uploadMessage = filePathCallback;

            // Media & Camera permission only asked on-demand when uploading a picture
            List<String> neededPerms = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    neededPerms.add(Manifest.permission.CAMERA);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                        neededPerms.add(Manifest.permission.READ_MEDIA_IMAGES);
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        neededPerms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                    }
                }
            }

            if (!neededPerms.isEmpty()) {
                ActivityCompat.requestPermissions(MainActivity.this, neededPerms.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                return true;
            }

            openFileChooser();
            return true;
        }
    }

    private void openFileChooser() {
        Intent takePictureIntent = null;
        try {
            File photoFile = createImageFile();
            if (photoFile != null) {
                cameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                        getApplicationContext().getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            }
        } catch (Exception e) {
            takePictureIntent = null;
        }

        Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentSelectionIntent.setType("*/*");
        contentSelectionIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});

        Intent[] intentArray;
        if (takePictureIntent != null) {
            intentArray = new Intent[]{takePictureIntent};
        } else {
            intentArray = new Intent[0];
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "ছবি বা ফাইল নির্বাচন করুন");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

        try {
            startActivityForResult(chooserIntent, FILE_CHOOSER_RESULT_CODE);
        } catch (ActivityNotFoundException e) {
            if (uploadMessage != null) {
                uploadMessage.onReceiveValue(null);
                uploadMessage = null;
            }
            Toast.makeText(MainActivity.this, "ফাইল সিলেক্টর ওপেন করা যায়নি", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (uploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }

            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data == null || data.getData() == null) {
                    if (cameraPhotoPath != null) {
                        results = new Uri[]{Uri.parse(cameraPhotoPath)};
                    }
                } else {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                super.onBackPressed();
            } else {
                Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show();
                backPressedTime = System.currentTimeMillis();
            }
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                return capabilities != null && (
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.isConnected();
            }
        }
        return false;
    }

    private void checkPermissions() {
        // App open er somoy ONLY Notification permission saibe (Media/Camera open er somoy saibe nah)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])) {
                    if (grantResults.length > i && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        try {
                            android.content.SharedPreferences prefs = getSharedPreferences(AppFirebaseMessagingService.PREF_NAME, MODE_PRIVATE);
                            String token = prefs.getString(AppFirebaseMessagingService.KEY_FCM_TOKEN, null);
                            if (token != null && !token.isEmpty()) {
                                AppFirebaseMessagingService.syncTokenToServer(this, token);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE) {
            // Media / Camera permission result when uploading a pic
            if (uploadMessage != null) {
                openFileChooser();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }
}
