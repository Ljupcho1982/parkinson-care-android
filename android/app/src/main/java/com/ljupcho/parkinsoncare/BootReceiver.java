package com.ljupcho.parkinsoncare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-schedules all medication alarms after the phone reboots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)
                || action.equals("android.intent.action.QUICKBOOT_POWERON")) {
            AlarmScheduler.scheduleAllFromPrefs(context);
        }
    }
}
