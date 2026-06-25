package com.ljupcho.parkinsoncare;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/** Full-screen alarm UI (big letters). Sound/vibration/voice are owned by AlarmService. */
public class AlarmActivity extends Activity {

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

        final Intent src = getIntent();
        String name = strExtra(src, "name", "Medication");
        String dose = strExtra(src, "dose", "");

        ((TextView) findViewById(R.id.txtName)).setText(name);
        ((TextView) findViewById(R.id.txtDose)).setText(dose);

        Button took = findViewById(R.id.btnTook);
        Button snooze = findViewById(R.id.btnSnooze);

        took.setOnClickListener(v -> {
            Intent i = new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP);
            startService(i);
            finish();
        });
        snooze.setOnClickListener(v -> {
            Intent i = new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_SNOOZE);
            if (src != null) i.putExtras(src);
            startService(i);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Require an explicit choice (Took it / Snooze).
    }

    private static String strExtra(Intent i, String key, String def) {
        if (i == null) return def;
        String v = i.getStringExtra(key);
        return v == null ? def : v;
    }
}
