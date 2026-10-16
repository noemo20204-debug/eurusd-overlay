package com.almuhandis.eurusdoverlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "overlay_prefs";
    private boolean pendingStart = false;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureDefaults();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (pendingStart && Settings.canDrawOverlays(this)) {
            pendingStart = false;
            startOverlay();
        }
    }

    private void ensureDefaults() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.contains("show_eurusd")) {
            p.edit()
                    .putBoolean("show_eurusd", true)
                    .putBoolean("show_usdegp", false)
                    .putBoolean("show_euregp", false)
                    .putBoolean("show_usdils", false)
                    .putBoolean("show_usdtry", false)
                    .apply();
        }
    }

    private void buildUi() {
        int pad = dp(22);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(11, 15, 20));

        TextView title = new TextView(this);
        title.setText("أسعار العملات — الشريط العائم");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(LinearLayout.LayoutParams.MATCH_PARENT));

        TextView desc = new TextView(this);
        desc.setText("اختر الأزواج التي تريد ظهورها فوق واتساب وباقي التطبيقات.\nالمصدر: Investing.com");
        desc.setTextColor(Color.rgb(190, 198, 210));
        desc.setTextSize(15);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        descLp.topMargin = dp(12);
        root.addView(desc, descLp);

        status = new TextView(this);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams statusLp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        statusLp.topMargin = dp(18);
        root.addView(status, statusLp);

        TextView choose = new TextView(this);
        choose.setText("الأزواج الظاهرة في الشاشة المنبثقة");
        choose.setTextColor(Color.WHITE);
        choose.setTextSize(17);
        choose.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams chooseLp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        chooseLp.topMargin = dp(24);
        root.addView(choose, chooseLp);

        addPairSwitch(root, "EUR/USD   يورو / دولار", "show_eurusd", true);
        addPairSwitch(root, "USD/EGP   دولار / جنيه مصري", "show_usdegp", false);
        addPairSwitch(root, "EUR/EGP   يورو / جنيه مصري", "show_euregp", false);
        addPairSwitch(root, "USD/ILS   دولار / شيكل", "show_usdils", false);
        addPairSwitch(root, "USD/TRY   دولار / ليرة تركية", "show_usdtry", false);

        Button start = button("تشغيل / تحديث الشريط العائم");
        LinearLayout.LayoutParams b1 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b1.topMargin = dp(24);
        root.addView(start, b1);
        start.setOnClickListener(v -> ensurePermissionAndStart());

        Button stop = button("إيقاف الشريط بالكامل");
        LinearLayout.LayoutParams b2 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b2.topMargin = dp(9);
        root.addView(stop, b2);
        stop.setOnClickListener(v -> stopOverlay());

        Button battery = button("إعدادات البطارية — اختر غير مقيّد");
        LinearLayout.LayoutParams b3 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b3.topMargin = dp(9);
        root.addView(battery, b3);
        battery.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        Button source = button("فتح EUR/USD في Investing.com");
        LinearLayout.LayoutParams b4 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b4.topMargin = dp(9);
        root.addView(source, b4);
        source.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://sa.investing.com/currencies/eur-usd"))));

        TextView hint = new TextView(this);
        hint.setText("أي تغيير في المفاتيح ينعكس مباشرة على الشريط إذا كان شغالًا. اسحب الشريط كاملًا بإصبعك لتغيير مكانه.");
        hint.setTextColor(Color.rgb(145, 155, 170));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        hintLp.topMargin = dp(22);
        root.addView(hint, hintLp);

        setContentView(root);
        refreshStatus();
    }

    private void addPairSwitch(LinearLayout root, String label, String key, boolean defaultValue) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.rgb(235, 238, 244));
        sw.setTextSize(16);
        sw.setChecked(p.getBoolean(key, defaultValue));
        sw.setPadding(dp(4), dp(6), dp(4), dp(6));

        LinearLayout.LayoutParams lp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        lp.topMargin = dp(5);
        root.addView(sw, lp);

        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(key, isChecked).apply();
            refreshRunningOverlay();
            refreshStatus();
        });
    }

    private void refreshRunningOverlay() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("enabled", false) || !Settings.canDrawOverlays(this)) return;

        Intent s = new Intent(this, OverlayService.class);
        s.setAction(OverlayService.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
    }

    private boolean hasAnyPairEnabled() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getBoolean("show_eurusd", true)
                || p.getBoolean("show_usdegp", false)
                || p.getBoolean("show_euregp", false)
                || p.getBoolean("show_usdils", false)
                || p.getBoolean("show_usdtry", false);
    }

    private int enabledPairCount() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int n = 0;
        if (p.getBoolean("show_eurusd", true)) n++;
        if (p.getBoolean("show_usdegp", false)) n++;
        if (p.getBoolean("show_euregp", false)) n++;
        if (p.getBoolean("show_usdils", false)) n++;
        if (p.getBoolean("show_usdtry", false)) n++;
        return n;
    }

    private void ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
        }

        if (!hasAnyPairEnabled()) {
            Toast.makeText(this, "فعّل زوج عملات واحدًا على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "فعّل: السماح بالظهور فوق التطبيقات الأخرى", Toast.LENGTH_LONG).show();
            return;
        }
        startOverlay();
    }

    private void startOverlay() {
        if (!hasAnyPairEnabled()) {
            Toast.makeText(this, "فعّل زوج عملات واحدًا على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        Intent s = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        Toast.makeText(this, "تم تشغيل الشريط العائم", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void stopOverlay() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "تم إيقاف الشريط", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean enabled = p.getBoolean("enabled", false);
        boolean permission = Settings.canDrawOverlays(this);
        int count = enabledPairCount();

        if (enabled && permission) {
            status.setText("● الشريط مفعّل — " + count + " زوج");
            status.setTextColor(Color.rgb(72, 220, 120));
        } else if (!permission) {
            status.setText("يلزم السماح بالظهور فوق التطبيقات");
            status.setTextColor(Color.rgb(255, 190, 70));
        } else {
            status.setText("الشريط متوقف — " + count + " زوج محدد");
            status.setTextColor(Color.rgb(170, 175, 185));
        }
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        return b;
    }

    private LinearLayout.LayoutParams matchWrap(int width) {
        return new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
