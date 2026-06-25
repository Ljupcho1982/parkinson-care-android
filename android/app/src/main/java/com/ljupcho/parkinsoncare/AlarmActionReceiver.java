package com.ljupcho.parkinsoncare;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the "I took it" / "Snooze" buttons on the alarm notification (works from the lock screen). */
public class AlarmActionReceiver extends BroadcastReceiver {

    static final String ACTION_TOOK = "com.ljupcho.parkinsoncare.TOOK";
    static final String ACTION_SNOOZE = "com.ljupcho.parkinsoncare.SNOOZE_NOTIF";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int notifId = intent.getIntExtra("notifId", 1);

        if (ACTION_SNOOZE.equals(intent.getAction())) {
            int base = intent.getIntExtra("reqCode", notifId);
            int minutes = ctx.getSharedPreferences("pk_native", Context.MODE_PRIVATE).getInt("snooze", 10);
            AlarmScheduler.scheduleSnooze(ctx,
                    intent.getStringExtra("name"), intent.getStringExtra("dose"), intent.getStringExtra("lang"),
                    intent.getBooleanExtra("voice", true), intent.getBooleanExtra("sound", true),
                    intent.getBooleanExtra("vibrate", true), base, minutes < 1 ? 10 : minutes);
        }

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notifId);
    }
}
