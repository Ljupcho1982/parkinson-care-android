package com.ljupcho.parkinsoncare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

/** Fires when a scheduled dose is due: records it, starts the foreground alarm service, re-arms for tomorrow. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Record that the alarm fired (surfaced in the in-app status line for diagnostics).
        try {
            SharedPreferences p = context.getSharedPreferences("pk_native", Context.MODE_PRIVATE);
            p.edit()
                    .putLong("lastFiredAt", System.currentTimeMillis())
                    .putString("lastFiredName", intent.getStringExtra("name"))
                    .apply();
        } catch (Exception ignored) {}

        // Keep the CPU awake long enough to start the alarm.
        PowerManager.WakeLock wl = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "parkcare:alarm");
                wl.acquire(60_000L);
            }
        } catch (Exception ignored) {}

        boolean isSnooze = intent.getBooleanExtra("isSnooze", false);
        if (!isSnooze) {
            AlarmScheduler.rescheduleNextDay(context, intent);
        }

        Intent svc = new Intent(context, AlarmService.class).setAction(AlarmService.ACTION_FIRE);
        svc.putExtras(intent);
        try {
            ContextCompat.startForegroundService(context, svc);
        } catch (Exception e) {
            try { context.startService(svc); } catch (Exception ignored) {}
        }

        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}
    }
}
