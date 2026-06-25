package com.ljupcho.parkinsoncare;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PillAlarm")
public class PillAlarmPlugin extends Plugin {

    @PluginMethod
    public void ensurePermissions(PluginCall call) {
        Context ctx = getContext();
        // Exact alarms (Android 12+): if not allowed, send the user to the system toggle.
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception ignored) {}
            }
        }
        // Full-screen intent (Android 14+): request the special access so the alarm can cover the screen.
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                i.setData(Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
        call.resolve();
    }

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray items = call.getArray("items", new JSArray());
        String lang = call.getString("lang", "auto");
        boolean voice = Boolean.TRUE.equals(call.getBoolean("voice", true));
        boolean sound = Boolean.TRUE.equals(call.getBoolean("sound", true));
        boolean vibrate = Boolean.TRUE.equals(call.getBoolean("vibrate", true));
        int snooze = call.getInt("snoozeMinutes", 10);
        AlarmScheduler.saveAndSchedule(getContext(), items.toString(), lang, voice, sound, vibrate, snooze);
        call.resolve();
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        AlarmScheduler.cancelAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void test(PluginCall call) {
        Intent i = new Intent(getContext(), AlarmActivity.class);
        i.putExtra("name", call.getString("name", "Medication"));
        i.putExtra("dose", call.getString("dose", ""));
        i.putExtra("lang", call.getString("lang", "auto"));
        i.putExtra("voice", Boolean.TRUE.equals(call.getBoolean("voice", true)));
        i.putExtra("sound", Boolean.TRUE.equals(call.getBoolean("sound", true)));
        i.putExtra("vibrate", Boolean.TRUE.equals(call.getBoolean("vibrate", true)));
        i.putExtra("notifId", 999000);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        getContext().startActivity(i);
        call.resolve();
    }
}
