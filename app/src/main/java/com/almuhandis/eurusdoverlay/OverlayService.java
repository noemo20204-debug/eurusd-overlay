package com.almuhandis.eurusdoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverlayService extends Service {
    public static final String SOURCE_URL = "https://sa.investing.com/currencies/eur-usd";

    private static final String CHANNEL_ID = "eurusd_overlay_channel";
    private static final int NOTIFICATION_ID = 10124;
    private static final String PREFS = "overlay_prefs";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private WindowManager wm;
    private WindowManager.LayoutParams overlayParams;
    private WindowManager.LayoutParams webParams;
    private LinearLayout overlay;
    private TextView priceView;
    private WebView webView;
    private String lastPrice = null;
    private long lastWebPriceAt = 0L;
    private long lastHttpAttemptAt = 0L;
    private boolean destroyed = false;

    private final Runnable pollJs = new Runnable() {
        @Override public void run() {
            if (destroyed || webView == null) return;
            try {
                String js = "(function(){" +
                        "var s=['[data-test=\\\"instrument-price-last\\\"]','[data-test=\\\"instrument-price-last-value\\\"]'];" +
                        "for(var i=0;i<s.length;i++){var e=document.querySelector(s[i]);if(e){var t=(e.innerText||e.textContent||'').trim();if(/^[0-9]+[.,][0-9]{3,6}$/.test(t))return t;}}" +
                        "var all=document.querySelectorAll('span,div');" +
                        "for(var j=0;j<all.length && j<2500;j++){var t2=(all[j].textContent||'').trim();if(/^1\\.[0-9]{3,6}$/.test(t2)){var r=all[j].getBoundingClientRect();if(r.width>20&&r.height>10&&r.top<900)return t2;}}" +
                        "return '';})()";
                webView.evaluateJavascript(js, value -> {
                    String p = normalizeJsResult(value);
                    if (isPlausibleEurUsd(p)) {
                        lastWebPriceAt = System.currentTimeMillis();
                        setPrice(p);
                    }
                });
            } catch (Exception ignored) {}

            long now = System.currentTimeMillis();
            if (now - lastWebPriceAt > 9000 && now - lastHttpAttemptAt > 10000) {
                lastHttpAttemptAt = now;
                fetchViaHttpFallback();
            }
            main.postDelayed(this, 800);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("جاري جلب السعر…"));

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlay();
        createHiddenWebView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        return START_STICKY;
    }

    private void createOverlay() {
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.setPadding(dp(10), dp(5), dp(10), dp(5));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 13, 17, 22));
        bg.setCornerRadius(dp(13));
        bg.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        overlay.setBackground(bg);
        overlay.setElevation(dp(8));

        TextView pair = new TextView(this);
        pair.setText("EUR/USD");
        pair.setTextColor(Color.WHITE);
        pair.setTextSize(13);
        pair.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pair.setIncludeFontPadding(false);
        overlay.addView(pair);

        TextView spacer = new TextView(this);
        spacer.setText("  ");
        overlay.addView(spacer);

        priceView = new TextView(this);
        priceView.setText("—.————");
        priceView.setTextColor(Color.rgb(235, 238, 244));
        priceView.setTextSize(15);
        priceView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        priceView.setIncludeFontPadding(false);
        overlay.addView(priceView);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int savedX = p.getInt("x", dp(12));
        int savedY = p.getInt("y", dp(52));

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = savedX;
        overlayParams.y = savedY;

        wm.addView(overlay, overlayParams);
        installDragHandler();
    }

    private void installDragHandler() {
        overlay.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = overlayParams.x;
                        startY = overlayParams.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        overlayParams.x = startX + Math.round(event.getRawX() - downX);
                        overlayParams.y = startY + Math.round(event.getRawY() - downY);
                        try { wm.updateViewLayout(overlay, overlayParams); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putInt("x", overlayParams.x)
                                .putInt("y", overlayParams.y)
                                .apply();
                        return true;
                }
                return false;
            }
        });
    }

    private void createHiddenWebView() {
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(false);
        s.setBlockNetworkImage(true);
        s.setUserAgentString(s.getUserAgentString() + " EURUSDOverlay/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                // Try to dismiss common consent dialogs without affecting the source page.
                view.evaluateJavascript("(function(){var b=[...document.querySelectorAll('button')].find(x=>/accept|agree|موافق|قبول/i.test(x.innerText||''));if(b)b.click();})()", null);
            }
        });

        webParams = new WindowManager.LayoutParams(
                2, 2,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        webParams.gravity = Gravity.TOP | Gravity.START;
        webParams.x = 0;
        webParams.y = 0;
        webParams.alpha = 0.01f;
        wm.addView(webView, webParams);

        webView.loadUrl(SOURCE_URL);
        main.postDelayed(pollJs, 2500);
    }

    private String normalizeJsResult(String v) {
        if (v == null || "null".equals(v)) return "";
        String p = v.trim();
        if (p.startsWith("\"") && p.endsWith("\"") && p.length() >= 2) {
            p = p.substring(1, p.length() - 1);
        }
        p = p.replace("\\n", "").replace("\\u00a0", "").replace(" ", "");
        return p.replace(',', '.');
    }

    private boolean isPlausibleEurUsd(String p) {
        if (p == null || !p.matches("[0-9]+\\.[0-9]{3,6}")) return false;
        try {
            double d = Double.parseDouble(p);
            return d > 0.70 && d < 1.80;
        } catch (Exception e) {
            return false;
        }
    }

    private void setPrice(String p) {
        if (!isPlausibleEurUsd(p)) return;
        main.post(() -> {
            if (priceView == null) return;
            int color = Color.rgb(235, 238, 244);
            if (lastPrice != null) {
                try {
                    double oldV = Double.parseDouble(lastPrice);
                    double newV = Double.parseDouble(p);
                    if (newV > oldV) color = Color.rgb(62, 220, 120);
                    else if (newV < oldV) color = Color.rgb(255, 90, 90);
                } catch (Exception ignored) {}
            }
            lastPrice = p;
            priceView.setText(p);
            priceView.setTextColor(color);
            updateNotification("EUR/USD  " + p);
        });
    }

    private void fetchViaHttpFallback() {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(SOURCE_URL);
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
                c.setRequestProperty("Accept-Language", "ar,en-US;q=0.8,en;q=0.7");
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                StringBuilder html = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null && html.length() < 1_800_000) html.append(line).append('\n');
                }
                String p = parsePriceFromHtml(html.toString());
                if (p != null) setPrice(p);
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private String parsePriceFromHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("data-test=\\\"instrument-price-last\\\"[^>]*>\\s*([0-9]+[.,][0-9]{3,6})\\s*<", Pattern.CASE_INSENSITIVE),
                Pattern.compile("instrument-price-last[^>]*>\\s*([0-9]+[.,][0-9]{3,6})\\s*<", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\\"last\\\"\\s*:\\s*\\\"?([0-9]+[.,][0-9]{3,6})", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\\"last_close\\\"\\s*:\\s*\\\"?([0-9]+[.,][0-9]{3,6})", Pattern.CASE_INSENSITIVE)
        };
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(html);
            if (m.find()) {
                String p = m.group(1).replace(',', '.');
                if (isPlausibleEurUsd(p)) return p;
            }
        }
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID, "EUR/USD Overlay", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps the floating EUR/USD price active");
            c.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(c);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("EUR/USD Overlay")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (webView != null) {
            try { webView.stopLoading(); webView.destroy(); } catch (Exception ignored) {}
        }
        if (wm != null) {
            try { if (overlay != null) wm.removeView(overlay); } catch (Exception ignored) {}
            try { if (webView != null) wm.removeView(webView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
