package com.ljupcho.parkinsoncare;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Capacitor requires custom plugins to be registered before super.onCreate.
        registerPlugin(PillAlarmPlugin.class);
        super.onCreate(savedInstanceState);

        // Ask for notification permission up front (Android 13+); needed for alarm notifications.
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
}
