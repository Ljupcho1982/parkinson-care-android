package com.ljupcho.parkinsoncare;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;

import java.util.Locale;

/** Full-screen alarm: big letters, looping alarm sound, vibration and a spoken voice reminder. */
public class AlarmActivity extends Activity {

    private MediaPlayer player;
    private Vibrator vibrator;
    private TextToSpeech tts;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String name, dose, lang;
    private boolean wantVoice, wantSound, wantVibrate;
    private int notifId, baseReqCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over the lock screen and turn the screen on.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_alarm);

        name = getString(getIntent(), "name", "Medication");
        dose = getString(getIntent(), "dose", "");
        lang = getString(getIntent(), "lang", "auto");
        wantVoice = getIntent().getBooleanExtra("voice", true);
        wantSound = getIntent().getBooleanExtra("sound", true);
        wantVibrate = getIntent().getBooleanExtra("vibrate", true);
        notifId = getIntent().getIntExtra("notifId", 1);
        baseReqCode = getIntent().getIntExtra("reqCode", notifId);

        ((TextView) findViewById(R.id.txtName)).setText(name);
        TextView doseView = findViewById(R.id.txtDose);
        doseView.setText(dose == null ? "" : dose);

        // Remove the notification now that the screen is showing.
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notifId);

        Button took = findViewById(R.id.btnTook);
        Button snooze = findViewById(R.id.btnSnooze);
        took.setOnClickListener(v -> { stopAll(); finish(); });
        snooze.setOnClickListener(v -> {
            int minutes = prefSnoozeMinutes();
            AlarmScheduler.scheduleSnooze(this, name, dose, lang, wantVoice, wantSound, wantVibrate, baseReqCode, minutes);
            stopAll();
            finish();
        });

        if (wantSound) startSound();
        if (wantVibrate) startVibrate();
        if (wantVoice) startVoice();

        // Auto-stop after 2 minutes so it doesn't ring forever.
        handler.postDelayed(this::safeAutoStop, 120_000L);
    }

    private void safeAutoStop() {
        stopSoundAndVibrate();
    }

    private int prefSnoozeMinutes() {
        SharedPreferences p = getSharedPreferences("pk_native", MODE_PRIVATE);
        int m = p.getInt("snooze", 10);
        return m < 1 ? 10 : m;
    }

    private void startSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
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
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void startVoice() {
        final String phrase = "mk".equals(lang)
                ? "Време е да го земете лекот. " + name + ". " + (dose == null ? "" : dose)
                : "Time to take your medication. " + name + ". " + (dose == null ? "" : dose);
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            Locale loc;
            if ("mk".equals(lang)) loc = new Locale("mk", "MK");
            else if ("en".equals(lang)) loc = Locale.US;
            else loc = Locale.getDefault();
            int res = tts.setLanguage(loc);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(0.92f);
            // speak it three times with pauses so it's hard to miss
            tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "pk1");
            tts.playSilentUtterance(1200, TextToSpeech.QUEUE_ADD, "gap1");
            tts.speak(phrase, TextToSpeech.QUEUE_ADD, null, "pk2");
            tts.playSilentUtterance(1200, TextToSpeech.QUEUE_ADD, "gap2");
            tts.speak(phrase, TextToSpeech.QUEUE_ADD, null, "pk3");
        });
    }

    private void stopSoundAndVibrate() {
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) {}
        try { if (vibrator != null) { vibrator.cancel(); vibrator = null; } } catch (Exception ignored) {}
    }

    private void stopAll() {
        handler.removeCallbacksAndMessages(null);
        stopSoundAndVibrate();
        try { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        stopAll();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Require an explicit choice (Took it / Snooze).
    }

    private static String getString(android.content.Intent i, String key, String def) {
        String v = i.getStringExtra(key);
        return v == null ? def : v;
    }
}
