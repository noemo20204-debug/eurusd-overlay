package com.almuhandis.eurusdoverlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "overlay_prefs";
    private static final int BG = Color.rgb(7, 17, 32);
    private static final int PANEL = Color.rgb(15, 31, 52);
    private static final int LINE = Color.rgb(39, 66, 94);
    private static final int MUTED = Color.rgb(153, 174, 199);
    private static final int BLUE = Color.rgb(38, 112, 248);
    private static final int GREEN = Color.rgb(43, 225, 150);
    private boolean pendingStart;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ensureDefaults();
        buildUi();
    }

    @Override protected void onResume() {
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
            p.edit().putBoolean("show_eurusd", true)
                    .putBoolean("show_usdegp", false)
                    .putBoolean("show_euregp", false)
                    .putBoolean("show_usdils", false)
                    .putBoolean("show_usdtry", false).apply();
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int width) { return new LinearLayout.LayoutParams(width, -2); }

    private GradientDrawable bg(int color, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(18), dp(26), dp(18), dp(32));
        scroll.addView(root);

        TextView title = text("أسعار العملات", 29, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1));
        TextView sub = text("الشريط العائم", 17, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(-1));

        status = text("", 14, GREEN, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(20), dp(10), dp(20), dp(10));
        status.setBackground(bg(Color.rgb(9, 61, 50), 24, Color.rgb(24, 105, 79)));
        LinearLayout.LayoutParams statusLp = lp(-2);
        statusLp.gravity = Gravity.CENTER_HORIZONTAL;
        statusLp.topMargin = dp(16);
        root.addView(status, statusLp);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(17), dp(16), dp(17));
        preview.setBackground(bg(Color.rgb(10, 33, 58), 22, BLUE));
        preview.setElevation(dp(6));
        LinearLayout.LayoutParams previewLp = lp(-1);
        previewLp.topMargin = dp(24);
        root.addView(preview, previewLp);
        preview.addView(text("معاينة الشريط العائم", 18, Color.WHITE, true));
        TextView previewHint = text("يظهر فوق التطبيقات ويمكن سحبه لأي مكان", 12, MUTED, false);
        LinearLayout.LayoutParams ph = lp(-1);
        ph.topMargin = dp(5);
        preview.addView(previewHint, ph);
        TextView previewText = text("EUR/USD     ••••••\nUSD/EGP     ••••••\nUSD/ILS      ••••••", 17, Color.WHITE, true);
        previewText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        previewText.setTextDirection(View.TEXT_DIRECTION_LTR);
        previewText.setGravity(Gravity.CENTER);
        previewText.setLineSpacing(dp(9), 1f);
        previewText.setPadding(dp(10), dp(12), dp(10), dp(12));
        previewText.setBackground(bg(Color.rgb(6, 18, 33), 14, LINE));
        LinearLayout.LayoutParams pt = lp(-1);
        pt.topMargin = dp(13);
        preview.addView(previewText, pt);
        TextView note = text("المعاينة توضيحية؛ الأسعار الحية تظهر في الشريط الفعلي", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = lp(-1);
        noteLp.topMargin = dp(8);
        preview.addView(note, noteLp);

        TextView heading = text("الأزواج الظاهرة في الشاشة المنبثقة", 19, Color.WHITE, true);
        LinearLayout.LayoutParams headingLp = lp(-1);
        headingLp.topMargin = dp(27);
        root.addView(heading, headingLp);
        TextView info = text("اختر العملات التي تريد عرضها فوق التطبيقات", 13, MUTED, false);
        LinearLayout.LayoutParams infoLp = lp(-1);
        infoLp.topMargin = dp(6);
        root.addView(info, infoLp);

        LinearLayout pairs = new LinearLayout(this);
        pairs.setOrientation(LinearLayout.VERTICAL);
        pairs.setPadding(dp(8), dp(9), dp(8), dp(9));
        pairs.setBackground(bg(PANEL, 22, LINE));
        LinearLayout.LayoutParams pairsLp = lp(-1);
        pairsLp.topMargin = dp(15);
        root.addView(pairs, pairsLp);
        addPair(pairs, "EUR/USD", "يورو / دولار", "show_eurusd", true);
        addPair(pairs, "USD/EGP", "دولار / جنيه مصري", "show_usdegp", false);
        addPair(pairs, "EUR/EGP", "يورو / جنيه مصري", "show_euregp", false);
        addPair(pairs, "USD/ILS", "دولار / شيكل", "show_usdils", false);
        addPair(pairs, "USD/TRY", "دولار / ليرة تركية", "show_usdtry", false);

        Button start = button("▶  تشغيل / تحديث الشريط العائم", BLUE);
        LinearLayout.LayoutParams b1 = lp(-1);
        b1.topMargin = dp(22);
        root.addView(start, b1);
        start.setOnClickListener(v -> ensurePermissionAndStart());

        Button stop = button("■  إيقاف الشريط بالكامل", Color.rgb(31, 50, 72));
        LinearLayout.LayoutParams b2 = lp(-1);
        b2.topMargin = dp(9);
        root.addView(stop, b2);
        stop.setOnClickListener(v -> stopOverlay());

        Button battery = button("إعدادات البطارية — اختر غير مقيّد", PANEL);
        LinearLayout.LayoutParams b3 = lp(-1);
        b3.topMargin = dp(17);
        root.addView(battery, b3);
        battery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });

        Button source = button("فتح EUR/USD في Investing.com", PANEL);
        LinearLayout.LayoutParams b4 = lp(-1);
        b4.topMargin = dp(9);
        root.addView(source, b4);
        source.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://sa.investing.com/currencies/eur-usd"))));

        TextView footer = text("تنعكس تغييرات الأزواج مباشرة على الشريط أثناء تشغيله.\nاسحب الشريط بإصبعك لتغيير مكانه.", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = lp(-1);
        footerLp.topMargin = dp(21);
        root.addView(footer, footerLp);
        setContentView(scroll);
        refreshStatus();
    }

    private void addPair(LinearLayout parent, String code, String label, String key, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams rowLp = lp(-1);
        rowLp.topMargin = dp(3);
        parent.addView(row, rowLp);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        row.addView(names, new LinearLayout.LayoutParams(0, dp(54), 1f));
        TextView codeView = text(code, 17, Color.WHITE, true);
        codeView.setTextDirection(View.TEXT_DIRECTION_LTR);
        codeView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        names.addView(codeView);
        names.addView(text(label, 12, MUTED, false));
        Switch sw = new Switch(this);
        sw.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, defaultValue));
        sw.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, Color.rgb(176, 188, 204)}));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{BLUE, Color.rgb(79, 91, 108)}));
        row.addView(sw);
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        sw.setOnCheckedChangeListener((v, checked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(key, checked).apply();
            refreshRunningOverlay();
            refreshStatus();
        });
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setMinHeight(dp(56));
        return b;
    }

    private void refreshRunningOverlay() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("enabled", false) || !Settings.canDrawOverlays(this)) return;
        Intent s = new Intent(this, OverlayService.class);
        s.setAction(OverlayService.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
    }

    private int enabledPairCount() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int count = 0;
        if (p.getBoolean("show_eurusd", true)) count++;
        if (p.getBoolean("show_usdegp", false)) count++;
        if (p.getBoolean("show_euregp", false)) count++;
        if (p.getBoolean("show_usdils", false)) count++;
        if (p.getBoolean("show_usdtry", false)) count++;
        return count;
    }

    private void ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
        }
        if (enabledPairCount() == 0) {
            Toast.makeText(this, "فعّل زوج عملات واحدًا على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true;
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "اسمح بالظهور فوق التطبيقات الأخرى", Toast.LENGTH_LONG).show();
            return;
        }
        startOverlay();
    }

    private void startOverlay() {
        if (enabledPairCount() == 0) {
            Toast.makeText(this, "فعّل زوج عملات واحدًا على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
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
        int count = enabledPairCount();
        if (p.getBoolean("enabled", false) && Settings.canDrawOverlays(this)) {
            status.setText("● الشريط مفعّل — " + count + " زوج");
            status.setTextColor(GREEN);
        } else if (!Settings.canDrawOverlays(this)) {
            status.setText("● يلزم السماح بالظهور فوق التطبيقات");
            status.setTextColor(Color.rgb(255, 193, 90));
        } else {
            status.setText("● الشريط متوقف — " + count + " زوج محدد");
            status.setTextColor(MUTED);
        }
    }
}
