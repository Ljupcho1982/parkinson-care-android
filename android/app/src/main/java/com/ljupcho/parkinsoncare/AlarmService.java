package com.ljupcho.parkinsoncare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Foreground service that actually rings: looping alarm sound + vibration + spoken voice.
 * Running as a foreground service is what lets the alarm play even when the app is closed/locked.
 */
public class AlarmService extends Service {

    static final String CHANNEL_ID = "pill_alarm_fg";
    static final String ACTION_FIRE = "FIRE";
    static final String ACTION_STOP = "STOP";
    static final String ACTION_SNOOZE = "SNOOZE";

    private MediaPlayer player;
    private Vibrator vibrator;
    private TextToSpeech tts;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_FIRE;
        if (ACTION_STOP.equals(action)) { stopEverything(); stopSelf(); return START_NOT_STICKY; }
        if (ACTION_SNOOZE.equals(action)) {
            int base = intent.getIntExtra("reqCode", intent.getIntExtra("notifId", 1));
            int minutes = getSharedPreferences("pk_native", MODE_PRIVATE).getInt("snooze", 10);
            AlarmScheduler.scheduleSnooze(this,
                    intent.getStringExtra("name"), intent.getStringExtra("dose"), intent.getStringExtra("lang"),
                    intent.getBooleanExtra("voice", true), intent.getBooleanExtra("sound", true),
                    intent.getBooleanExtra("vibrate", true), base, minutes < 1 ? 10 : minutes);
            stopEverything(); stopSelf(); return START_NOT_STICKY;
        }

        // FIRE
        String name = intent != null ? intent.getStringExtra("name") : "Medication";
        String dose = intent != null ? intent.getStringExtra("dose") : "";
        String lang = intent != null ? intent.getStringExtra("lang") : "auto";
        boolean wantVoice = intent == null || intent.getBooleanExtra("voice", true);
        boolean wantSound = intent == null || intent.getBooleanExtra("sound", true);
        boolean wantVibrate = intent == null || intent.getBooleanExtra("vibrate", true);
        int notifId = intent != null ? intent.getIntExtra("notifId", 1) : 1;
        if (name == null) name = "Medication";

        startForeground(notifId, buildNotification(name, dose, intent));

        if (wantSound) startSound();
        if (wantVibrate) startVibrate();
        if (wantVoice) startVoice(name, dose, lang);

        // Best-effort: also bring up the full-screen alarm screen.
        try {
            Intent act = new Intent(this, AlarmActivity.class);
            if (intent != null) act.putExtras(intent);
            act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(act);
        } catch (Exception ignored) {}

        // stop after 2 minutes if untouched
        handler.postDelayed(() -> { stopEverything(); stopSelf(); }, 120_000L);
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String name, String dose, Intent src) {
        createChannel();
        Intent full = new Intent(this, AlarmActivity.class);
        if (src != null) full.putExtras(src);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullPi = PendingIntent.getActivity(this, 1, full, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL_ID);
        else b = new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("💊 Time for " + name)
                .setContentText((dose != null && !dose.isEmpty() ? dose + " · " : "") + "Time to take it")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        // Silent channel — this service plays its own looping alarm sound, so avoid a double sound.
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Medication alarm", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Active medication alarm");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private void startSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) {}
    }

    private void startVibrate() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 600, 400, 600, 400, 600, 1000};
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else vibrator.vibrate(pattern, 0);
        } catch (Exception ignored) {}
    }

    private void startVoice(String name, String dose, String lang) {
        final String phrase = "mk".equals(lang)
                ? "Време е да го земете лекот. " + name + ". " + (dose == null ? "" : dose)
                : "Time to take your medication. " + name + ". " + (dose == null ? "" : dose);
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            Locale loc = "mk".equals(lang) ? new Locale("mk", "MK")
                    : ("en".equals(lang) ? Locale.US : Locale.getDefault());
            int r = tts.setLanguage(loc);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault());
            tts.setSpeechRate(0.92f);
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
            for (int i = 1; i <= 4; i++) {
                tts.speak(phrase, i == 1 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params, "pk" + i);
                tts.playSilentUtterance(1400, TextToSpeech.QUEUE_ADD, "gap" + i);
            }
        });
    }

    private void stopEverything() {
        handler.removeCallbacksAndMessages(null);
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) {}
        try { if (vibrator != null) { vibrator.cancel(); vibrator = null; } } catch (Exception ignored) {}
        try { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } } catch (Exception ignored) {}
        try { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() { stopEverything(); super.onDestroy(); }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
