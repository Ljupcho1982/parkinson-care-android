package com.ljupcho.parkinsoncare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

/**
 * Fires when a dose is due. Posts a high-priority notification whose CHANNEL carries the alarm
 * sound + vibration, so Android itself rings even with the app fully in the background (no service
 * needed). A full-screen intent shows the alarm screen when the phone is locked.
 */
public class AlarmReceiver extends BroadcastReceiver {

    static final String CHANNEL_ID = "pill_alarm_ring_v2";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Record fire time (shown in the in-app status line for diagnostics).
        try {
            SharedPreferences p = context.getSharedPreferences("pk_native", Context.MODE_PRIVATE);
            p.edit().putLong("lastFiredAt", System.currentTimeMillis())
                    .putString("lastFiredName", intent.getStringExtra("name")).apply();
        } catch (Exception ignored) {}

        PowerManager.WakeLock wl = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) { wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "parkcare:alarm"); wl.acquire(60_000L); }
        } catch (Exception ignored) {}

        boolean isSnooze = intent.getBooleanExtra("isSnooze", false);
        if (!isSnooze) AlarmScheduler.rescheduleNextDay(context, intent);

        String name = intent.getStringExtra("name");
        String dose = intent.getStringExtra("dose");
        int notifId = intent.getIntExtra("notifId", 1);
        if (name == null) name = "Medication";

        createChannel(context);

        Intent full = new Intent(context, AlarmActivity.class);
        full.putExtras(intent);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullPi = PendingIntent.getActivity(context, notifId, full, piFlags);

        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm == null) alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // "I took it" / "Snooze" buttons directly on the notification (usable from the lock screen).
        Intent tookIntent = new Intent(context, AlarmActionReceiver.class)
                .setAction(AlarmActionReceiver.ACTION_TOOK).putExtra("notifId", notifId);
        PendingIntent tookPi = PendingIntent.getBroadcast(context, notifId * 10 + 1, tookIntent, piFlags);
        Intent snoozeIntent = new Intent(context, AlarmActionReceiver.class)
                .setAction(AlarmActionReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtras(intent);
        snoozeIntent.putExtra("notifId", notifId);
        PendingIntent snoozePi = PendingIntent.getBroadcast(context, notifId * 10 + 2, snoozeIntent, piFlags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("💊 Time for " + name)
                .setContentText((dose != null && !dose.isEmpty() ? dose + " · " : "") + "Time to take it")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .addAction(0, "✓ I took it", tookPi)
                .addAction(0, "⏰ Snooze", snoozePi);
        // Pre-Android-8 the sound/vibration live on the notification itself.
        if (Build.VERSION.SDK_INT < 26) {
            b.setSound(alarm).setVibrate(new long[]{0, 600, 300, 600, 300, 600});
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notifId, b.build());

        // Directly bring up the full-screen alarm. The setAlarmClock() that triggered us grants a
        // temporary allowlist that permits this background activity start (even when unlocked).
        try {
            Intent act = new Intent(context, AlarmActivity.class);
            act.putExtras(intent);
            act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            context.startActivity(act);
        } catch (Exception ignored) {}

        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Medication alarms", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Rings when a dose is due");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm == null) alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ch.setSound(alarm, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        nm.createNotificationChannel(ch);
    }
}
