package com.ljupcho.parkinsoncare;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
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
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

/** Full-screen alarm: big letters + looping alarm sound + vibration + spoken voice. Self-contained. */
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

        Intent in = getIntent();
        name = strExtra(in, "name", "Medication");
        dose = strExtra(in, "dose", "");
        lang = strExtra(in, "lang", "auto");
        wantVoice = in.getBooleanExtra("voice", true);
        wantSound = in.getBooleanExtra("sound", true);
        wantVibrate = in.getBooleanExtra("vibrate", true);
        notifId = in.getIntExtra("notifId", 1);
        baseReqCode = in.getIntExtra("reqCode", notifId);

        ((TextView) findViewById(R.id.txtName)).setText(name);
        ((TextView) findViewById(R.id.txtDose)).setText(dose);

        // Remove the triggering notification (and silence its channel sound) now the screen is up.
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notifId);

        ((Button) findViewById(R.id.btnTook)).setOnClickListener(v -> { stopAll(); finish(); });
        ((Button) findViewById(R.id.btnSnooze)).setOnClickListener(v -> {
            int minutes = getSharedPreferences("pk_native", MODE_PRIVATE).getInt("snooze", 10);
            AlarmScheduler.scheduleSnooze(this, name, dose, lang, wantVoice, wantSound, wantVibrate, baseReqCode, minutes < 1 ? 10 : minutes);
            stopAll();
            finish();
        });

        if (wantSound) startSound();
        if (wantVibrate) startVibrate();
        if (wantVoice) startVoice();

        handler.postDelayed(this::stopRingAndVibrate, 120_000L);
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

    private void startVoice() {
        // NOTE: onInit can fire before the `tts` field finishes assigning. Defer the actual speaking
        // to the main-thread message queue so `tts` is guaranteed non-null by then (fixes silent voice).
        tts = new TextToSpeech(this, status -> {
            ttsStatus = status;
            handler.post(this::speakNow);
        });
    }

    private int ttsStatus = -99;

    private void speakNow() {
        if (ttsStatus != TextToSpeech.SUCCESS || tts == null) return;
        {
            final String mkText = "Време е да го земете лекот. " + name + ". " + (dose == null ? "" : dose);
            final String enText = "Time to take your medication. " + name + ". " + (dose == null ? "" : dose);
            final Locale mkLoc = new Locale("mk", "MK");

            boolean haveMk = isAvail(mkLoc);
            boolean haveEn = isAvail(Locale.US) || isAvail(Locale.ENGLISH);

            String text;
            // Prefer Macedonian if the user chose it (or device is set to MK) and a MK voice exists;
            // otherwise English; otherwise whatever voice the device actually has (so SOMETHING speaks).
            boolean preferMk = "mk".equals(lang) || (("auto".equals(lang) || lang == null)
                    && Locale.getDefault().getLanguage().equals("mk"));
            if (preferMk && haveMk) { tts.setLanguage(mkLoc); text = mkText; }
            else if (haveEn) { tts.setLanguage(isAvail(Locale.US) ? Locale.US : Locale.ENGLISH); text = enText; }
            else if (haveMk) { tts.setLanguage(mkLoc); text = mkText; }
            else { text = enText; /* keep engine default voice — it produced the example, so it can speak */ }

            tts.setSpeechRate(0.92f);
            // Route voice to the alarm stream the modern way (so it's audible at alarm volume).
            try {
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            } catch (Exception ignored) {}

            // Duck (mute) the alarm ring while speaking so the voice is clearly audible.
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { if (id != null && id.startsWith("pk")) duckRing(true); }
                @Override public void onDone(String id) { if ("end".equals(id)) duckRing(false); }
                @Override public void onError(String id) { duckRing(false); }
            });

            // ring a moment first, then announce 3x with the ring muted (default audio routing — no deprecated stream param)
            tts.playSilentUtterance(1200, TextToSpeech.QUEUE_FLUSH, "lead");
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "pk1");
            tts.playSilentUtterance(900, TextToSpeech.QUEUE_ADD, "g1");
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "pk2");
            tts.playSilentUtterance(900, TextToSpeech.QUEUE_ADD, "g2");
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "end");
        }
    }

    private void duckRing(boolean quiet) {
        try { if (player != null) player.setVolume(quiet ? 0f : 1f, quiet ? 0f : 1f); } catch (Exception ignored) {}
    }

    private boolean isAvail(Locale l) {
        try { return tts != null && tts.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE; }
        catch (Exception e) { return false; }
    }

    private void stopRingAndVibrate() {
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) {}
        try { if (vibrator != null) { vibrator.cancel(); vibrator = null; } } catch (Exception ignored) {}
    }

    private void stopAll() {
        handler.removeCallbacksAndMessages(null);
        stopRingAndVibrate();
        try { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() { stopAll(); super.onDestroy(); }

    @Override public void onBackPressed() { /* require Took it / Snooze */ }

    private static String strExtra(Intent i, String key, String def) {
        if (i == null) return def;
        String v = i.getStringExtra(key);
        return v == null ? def : v;
    }
}
