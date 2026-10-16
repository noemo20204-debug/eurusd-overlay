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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverlayService extends Service {
    public static final String ACTION_REFRESH = "com.almuhandis.eurusdoverlay.REFRESH";
    public static final String SOURCE_URL = "https://sa.investing.com/currencies/eur-usd";

    private static final String CHANNEL_ID = "fx_overlay_channel";
    private static final int NOTIFICATION_ID = 10124;
    private static final String PREFS = "overlay_prefs";

    private static class PairConfig {
        final String prefKey;
        final String code;
        final String url;
        final double min;
        final double max;
        final boolean defaultEnabled;

        PairConfig(String prefKey, String code, String url,
                   double min, double max, boolean defaultEnabled) {
            this.prefKey = prefKey;
            this.code = code;
            this.url = url;
            this.min = min;
            this.max = max;
            this.defaultEnabled = defaultEnabled;
        }
    }

    private static class PairSource {
        final PairConfig pair;
        final WebView webView;
        long lastWebPriceAt = 0L;
        long lastHttpAttemptAt = 0L;

        PairSource(PairConfig pair, WebView webView) {
            this.pair = pair;
            this.webView = webView;
        }
    }

    private static final PairConfig[] ALL_PAIRS = new PairConfig[]{
            new PairConfig("show_eurusd", "EUR/USD",
                    "https://sa.investing.com/currencies/eur-usd", 0.70, 1.80, true),
            new PairConfig("show_usdegp", "USD/EGP",
                    "https://sa.investing.com/currencies/usd-egp", 10.0, 100.0, false),
            new PairConfig("show_euregp", "EUR/EGP",
                    "https://sa.investing.com/currencies/eur-egp", 10.0, 150.0, false),
            new PairConfig("show_usdils", "USD/ILS",
                    "https://sa.investing.com/currencies/usd-ils", 1.0, 10.0, false),
            new PairConfig("show_usdtry", "USD/TRY",
                    "https://sa.investing.com/currencies/usd-try", 5.0, 100.0, false)
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);

    private WindowManager wm;
    private WindowManager.LayoutParams overlayParams;
    private LinearLayout overlay;
    private boolean destroyed = false;

    private final Map<String, TextView> priceViews = new LinkedHashMap<>();
    private final Map<String, String> lastPrices = new LinkedHashMap<>();
    private final Map<String, PairSource> sources = new LinkedHashMap<>();
    private List<PairConfig> enabledPairs = new ArrayList<>();

    private final Runnable pollAll = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;

            long now = System.currentTimeMillis();
            for (PairSource source : new ArrayList<>(sources.values())) {
                if (source.webView == null) continue;
                try {
                    source.webView.evaluateJavascript(buildJs(source.pair), value -> {
                        String p = normalizeJsResult(value);
                        if (isPlausible(source.pair, p)) {
                            source.lastWebPriceAt = System.currentTimeMillis();
                            setPrice(source.pair, p);
                        }
                    });
                } catch (Exception ignored) {
                }

                if (now - source.lastWebPriceAt > 9000
                        && now - source.lastHttpAttemptAt > 10000) {
                    source.lastHttpAttemptAt = now;
                    fetchViaHttpFallback(source.pair);
                }
            }
            main.postDelayed(this, 900);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("جاري جلب الأسعار…"));

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        rebuildAll();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        if (intent != null && ACTION_REFRESH.equals(intent.getAction()) && wm != null) {
            rebuildAll();
        }
        return START_STICKY;
    }

    private void rebuildAll() {
        main.removeCallbacks(pollAll);
        removeCurrentViews();

        enabledPairs = getEnabledPairs();
        priceViews.clear();
        sources.clear();

        if (enabledPairs.isEmpty()) {
            updateNotification("لا يوجد زوج مفعّل");
            return;
        }

        createOverlay();
        createHiddenWebViews();
        main.postDelayed(pollAll, 2400);
    }

    private List<PairConfig> getEnabledPairs() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        List<PairConfig> list = new ArrayList<>();
        for (PairConfig pair : ALL_PAIRS) {
            if (p.getBoolean(pair.prefKey, pair.defaultEnabled)) list.add(pair);
        }
        return list;
    }

    private void createOverlay() {
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.setPadding(dp(9), dp(5), dp(9), dp(5));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(228, 13, 17, 22));
        bg.setCornerRadius(dp(13));
        bg.setStroke(dp(1), Color.argb(115, 255, 255, 255));
        overlay.setBackground(bg);
        overlay.setElevation(dp(8));

        for (PairConfig pair : enabledPairs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(2), dp(2), dp(2));

            TextView pairView = new TextView(this);
            pairView.setText(pair.code);
            pairView.setTextColor(Color.WHITE);
            pairView.setTextSize(12);
            pairView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            pairView.setIncludeFontPadding(false);
            row.addView(pairView);

            TextView spacer = new TextView(this);
            spacer.setText("   ");
            row.addView(spacer);

            TextView priceView = new TextView(this);
            String saved = lastPrices.get(pair.code);
            priceView.setText(saved == null ? "—.————" : saved);
            priceView.setTextColor(Color.rgb(235, 238, 244));
            priceView.setTextSize(14);
            priceView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            priceView.setIncludeFontPadding(false);
            row.addView(priceView);

            priceViews.put(pair.code, priceView);
            overlay.addView(row);
        }

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
                        try {
                            wm.updateViewLayout(overlay, overlayParams);
                        } catch (Exception ignored) {
                        }
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

    private void createHiddenWebViews() {
        int index = 0;
        for (PairConfig pair : enabledPairs) {
            WebView webView = new WebView(this);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setLoadsImagesAutomatically(false);
            s.setBlockNetworkImage(true);
            s.setUserAgentString(s.getUserAgentString() + " FXOverlay/2.0");

            CookieManager.getInstance().setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= 21) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            }

            webView.setWebChromeClient(new WebChromeClient());
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript(
                            "(function(){var b=[...document.querySelectorAll('button')].find(x=>/accept|agree|موافق|قبول/i.test(x.innerText||''));if(b)b.click();})()",
                            null
                    );
                }
            });

            WindowManager.LayoutParams webParams = new WindowManager.LayoutParams(
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
            webParams.x = index * 3;
            webParams.y = 0;
            webParams.alpha = 0.01f;

            wm.addView(webView, webParams);
            sources.put(pair.code, new PairSource(pair, webView));
            webView.loadUrl(pair.url);
            index++;
        }
    }

    private String buildJs(PairConfig pair) {
        return "(function(){"
                + "var min=" + pair.min + ",max=" + pair.max + ";"
                + "function ok(t){t=(t||'').trim().replace(/\\s/g,'');"
                + "if(!/^[0-9]{1,3}[.,][0-9]{2,6}$/.test(t))return '';"
                + "var n=parseFloat(t.replace(',','.'));return(n>min&&n<max)?t:'';}"
                + "var s=['[data-test=\"instrument-price-last\"]','[data-test=\"instrument-price-last-value\"]'];"
                + "for(var i=0;i<s.length;i++){var e=document.querySelector(s[i]);if(e){var z=ok(e.innerText||e.textContent);if(z)return z;}}"
                + "var all=document.querySelectorAll('span,div');"
                + "for(var j=0;j<all.length&&j<3000;j++){var t=ok(all[j].textContent);if(t){var r=all[j].getBoundingClientRect();"
                + "if(r.width>20&&r.height>10&&r.top<900)return t;}}"
                + "return '';})()";
    }

    private String normalizeJsResult(String v) {
        if (v == null || "null".equals(v)) return "";
        String p = v.trim();
        if (p.startsWith("\"") && p.endsWith("\"") && p.length() >= 2) {
            p = p.substring(1, p.length() - 1);
        }
        p = p.replace("\\n", "")
                .replace("\\u00a0", "")
                .replace(" ", "");
        return p.replace(',', '.');
    }

    private boolean isPlausible(PairConfig pair, String p) {
        if (p == null || !p.matches("[0-9]+\\.[0-9]{2,6}")) return false;
        try {
            double d = Double.parseDouble(p);
            return d > pair.min && d < pair.max;
        } catch (Exception e) {
            return false;
        }
    }

    private void setPrice(PairConfig pair, String p) {
        if (!isPlausible(pair, p)) return;
        main.post(() -> {
            TextView priceView = priceViews.get(pair.code);
            if (priceView == null) return;

            int color = Color.rgb(235, 238, 244);
            String oldPrice = lastPrices.get(pair.code);
            if (oldPrice != null) {
                try {
                    double oldV = Double.parseDouble(oldPrice);
                    double newV = Double.parseDouble(p);
                    if (newV > oldV) color = Color.rgb(62, 220, 120);
                    else if (newV < oldV) color = Color.rgb(255, 90, 90);
                } catch (Exception ignored) {
                }
            }

            lastPrices.put(pair.code, p);
            priceView.setText(p);
            priceView.setTextColor(color);
            updateNotification(buildNotificationText());
        });
    }

    private String buildNotificationText() {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (PairConfig pair : enabledPairs) {
            if (shown >= 2) break;
            String p = lastPrices.get(pair.code);
            if (p == null) continue;
            if (sb.length() > 0) sb.append("   ");
            sb.append(pair.code).append(" ").append(p);
            shown++;
        }
        if (sb.length() == 0) return "جاري جلب الأسعار…";
        if (enabledPairs.size() > shown) sb.append("   +").append(enabledPairs.size() - shown);
        return sb.toString();
    }

    private void fetchViaHttpFallback(PairConfig pair) {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(pair.url);
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
                c.setRequestProperty("Accept-Language", "ar,en-US;q=0.8,en;q=0.7");
                c.setRequestProperty("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                StringBuilder html = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null && html.length() < 1_800_000) {
                        html.append(line).append('\n');
                    }
                }
                String p = parsePriceFromHtml(pair, html.toString());
                if (p != null) setPrice(pair, p);
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private String parsePriceFromHtml(PairConfig pair, String html) {
        if (html == null || html.isEmpty()) return null;

        Pattern[] patterns = new Pattern[]{
                Pattern.compile("data-test=\\\"instrument-price-last\\\"[^>]*>\\s*([0-9]+[.,][0-9]{2,6})\\s*<",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("instrument-price-last[^>]*>\\s*([0-9]+[.,][0-9]{2,6})\\s*<",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\\"last\\\"\\s*:\\s*\\\"?([0-9]+[.,][0-9]{2,6})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\\"last_close\\\"\\s*:\\s*\\\"?([0-9]+[.,][0-9]{2,6})",
                        Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(html);
            if (m.find()) {
                String p = m.group(1).replace(',', '.');
                if (isPlausible(pair, p)) return p;
            }
        }
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID, "FX Floating Prices", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps selected floating currency prices active");
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

        return b.setContentTitle("FX Overlay")
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

    private void removeCurrentViews() {
        if (wm == null) return;

        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
            }
            overlay = null;
        }

        for (PairSource source : new ArrayList<>(sources.values())) {
            try {
                source.webView.stopLoading();
                wm.removeView(source.webView);
                source.webView.destroy();
            } catch (Exception ignored) {
            }
        }
        sources.clear();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        removeCurrentViews();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
