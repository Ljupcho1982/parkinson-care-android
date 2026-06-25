package com.ljupcho.parkinsoncare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/** Fires when a scheduled dose is due: starts the foreground alarm service and re-arms for tomorrow. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isSnooze = intent.getBooleanExtra("isSnooze", false);

        // Re-arm the same dose for tomorrow (snoozed alarms keep the original daily alarm intact).
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
    }
}
