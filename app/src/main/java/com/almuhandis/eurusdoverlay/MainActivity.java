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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "overlay_prefs";
    private boolean pendingStart = false;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    private void buildUi() {
        int pad = dp(22);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(11, 15, 20));

        TextView title = new TextView(this);
        title.setText("EUR/USD — السعر العائم");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(LinearLayout.LayoutParams.MATCH_PARENT));

        TextView desc = new TextView(this);
        desc.setText("شريط صغير يبقى فوق واتساب وكل التطبيقات.\nمصدر السعر مباشرة: sa.investing.com/currencies/eur-usd");
        desc.setTextColor(Color.rgb(190, 198, 210));
        desc.setTextSize(16);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        descLp.topMargin = dp(14);
        root.addView(desc, descLp);

        status = new TextView(this);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams statusLp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        statusLp.topMargin = dp(24);
        root.addView(status, statusLp);

        Button start = button("تشغيل الشريط العائم");
        LinearLayout.LayoutParams b1 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b1.topMargin = dp(26);
        root.addView(start, b1);
        start.setOnClickListener(v -> ensurePermissionAndStart());

        Button stop = button("إيقاف الشريط");
        LinearLayout.LayoutParams b2 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b2.topMargin = dp(10);
        root.addView(stop, b2);
        stop.setOnClickListener(v -> stopOverlay());

        Button battery = button("فتح إعدادات البطارية — اختر غير مقيّد");
        LinearLayout.LayoutParams b3 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b3.topMargin = dp(10);
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

        Button source = button("فتح صفحة EUR/USD في Investing.com");
        LinearLayout.LayoutParams b4 = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        b4.topMargin = dp(10);
        root.addView(source, b4);
        source.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(OverlayService.SOURCE_URL))));

        TextView hint = new TextView(this);
        hint.setText("بعد التشغيل: اسحب الشريط بإصبعك وضعه في أي مكان. السعر يحافظ على الكسر كما تعرضه صفحة Investing.com، ولا يتم تقريبه إلى 1.12.");
        hint.setTextColor(Color.rgb(145, 155, 170));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = matchWrap(LinearLayout.LayoutParams.MATCH_PARENT);
        hintLp.topMargin = dp(24);
        root.addView(hint, hintLp);

        setContentView(root);
        refreshStatus();
    }

    private void ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
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
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        Intent s = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        Toast.makeText(this, "تم تشغيل شريط EUR/USD", Toast.LENGTH_SHORT).show();
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
        if (enabled && permission) {
            status.setText("● الشريط مفعّل");
            status.setTextColor(Color.rgb(72, 220, 120));
        } else if (!permission) {
            status.setText("يلزم السماح بالظهور فوق التطبيقات");
            status.setTextColor(Color.rgb(255, 190, 70));
        } else {
            status.setText("الشريط متوقف");
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
