package com.almuhandis.eurusdoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (!enabled || !Settings.canDrawOverlays(context)) return;
        try {
            Intent service = new Intent(context, OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception ignored) {
            // Some Android versions/OEMs can restrict boot-time service starts.
            // START_STICKY and Samsung battery settings handle normal restarts.
        }
    }
}
