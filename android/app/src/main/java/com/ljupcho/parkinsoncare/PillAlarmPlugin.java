package com.ljupcho.parkinsoncare;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

@CapacitorPlugin(name = "PillAlarm")
public class PillAlarmPlugin extends Plugin {

    @PluginMethod
    public void ensurePermissions(PluginCall call) {
        Context ctx = getContext();
        String pkg = ctx.getPackageName();
        // 1) Notifications off? Open the app's notification settings.
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            try {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                call.resolve();
                return;
            } catch (Exception ignored) {}
        }
        // 2) "Appear on top" / draw-over-other-apps off? This is what lets the alarm screen
        //    pop up from the background. Open the system toggle.
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                call.resolve();
                return;
            } catch (Exception ignored) {}
        }
        // 3) Exact alarms off (Android 12+)? Open the system toggle.
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(Uri.parse("package:" + pkg));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    call.resolve();
                    return;
                } catch (Exception ignored) {}
            }
        }
        // 4) Battery optimization on? Open the battery-optimization settings list (no restricted permission).
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
                try {
                    Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception ignored) {}
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void scheduleTest(PluginCall call) {
        AlarmScheduler.scheduleTestInSeconds(getContext(),
                call.getInt("seconds", 15),
                call.getString("lang", "auto"),
                Boolean.TRUE.equals(call.getBoolean("voice", true)),
                Boolean.TRUE.equals(call.getBoolean("sound", true)),
                Boolean.TRUE.equals(call.getBoolean("vibrate", true)));
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
        call.resolve(status());
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        AlarmScheduler.cancelAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void test(PluginCall call) {
        // App is in the foreground here, so launch the full-screen alarm directly (always allowed).
        Context ctx = getContext();
        Intent ui = new Intent(ctx, AlarmActivity.class);
        ui.putExtra("name", call.getString("name", "Medication"));
        ui.putExtra("dose", call.getString("dose", ""));
        ui.putExtra("lang", call.getString("lang", "auto"));
        ui.putExtra("voice", Boolean.TRUE.equals(call.getBoolean("voice", true)));
        ui.putExtra("sound", Boolean.TRUE.equals(call.getBoolean("sound", true)));
        ui.putExtra("vibrate", Boolean.TRUE.equals(call.getBoolean("vibrate", true)));
        ui.putExtra("notifId", 999000);
        ui.putExtra("reqCode", 999000);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { ctx.startActivity(ui); } catch (Exception ignored) {}
        call.resolve();
    }

    private JSObject status() {
        Context ctx = getContext();
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            exact = am != null && am.canScheduleExactAlarms();
        }
        boolean notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled();
        boolean battery = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            battery = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        }
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);

        SharedPreferences p = ctx.getSharedPreferences("pk_native", Context.MODE_PRIVATE);
        int count = 0;
        long next = Long.MAX_VALUE;
        try {
            JSONArray arr = new JSONArray(p.getString("items", "[]"));
            count = arr.length();
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, o.optInt("hour", 8));
                c.set(Calendar.MINUTE, o.optInt("minute", 0));
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
                if (c.getTimeInMillis() < next) next = c.getTimeInMillis();
            }
        } catch (Exception ignored) {}

        JSObject r = new JSObject();
        r.put("exactAllowed", exact);
        r.put("notificationsAllowed", notif);
        r.put("batteryUnrestricted", battery);
        r.put("overlayAllowed", overlay);
        r.put("scheduledCount", count);
        r.put("nextAt", next == Long.MAX_VALUE ? 0 : next);
        r.put("lastFiredAt", p.getLong("lastFiredAt", 0));
        r.put("lastFiredName", p.getString("lastFiredName", ""));
        return r;
    }
}
