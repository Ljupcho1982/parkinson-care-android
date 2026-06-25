package com.ljupcho.parkinsoncare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/** Fires when a scheduled dose is due: shows a full-screen alarm notification and re-arms for tomorrow. */
public class AlarmReceiver extends BroadcastReceiver {

    static final String CHANNEL_ID = "pill_alarm_v1";

    @Override
    public void onReceive(Context context, Intent intent) {
        String name = intent.getStringExtra("name");
        String dose = intent.getStringExtra("dose");
        boolean isSnooze = intent.getBooleanExtra("isSnooze", false);
        int notifId = intent.getIntExtra("notifId", 1);
        if (name == null) name = "Medication";

        createChannel(context);

        // Full-screen intent -> launches the alarm screen (covers the lock screen).
        Intent full = new Intent(context, AlarmActivity.class);
        full.putExtras(intent);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullPi = PendingIntent.getActivity(context, notifId, full, piFlags);

        String text = (dose != null && !dose.isEmpty() ? dose + " · " : "") + "Time to take it";
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("💊 Time for " + name)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notifId, b.build());

        // Also try to launch directly (works when allowed); harmless if the full-screen intent already did.
        try { context.startActivity(full); } catch (Exception ignored) {}

        // Re-arm the same dose for tomorrow (snoozed/test alarms keep the original daily alarm intact).
        if (!isSnooze) {
            AlarmScheduler.rescheduleNextDay(context, intent);
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Medication alarms", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Full-screen alarms when a dose is due");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm == null) alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(alarm, attrs);
        nm.createNotificationChannel(ch);
    }
}
