package com.ljupcho.parkinsoncare;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
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
        Intent svc = new Intent(getContext(), AlarmService.class).setAction(AlarmService.ACTION_FIRE);
        svc.putExtra("name", call.getString("name", "Medication"));
        svc.putExtra("dose", call.getString("dose", ""));
        svc.putExtra("lang", call.getString("lang", "auto"));
        svc.putExtra("voice", Boolean.TRUE.equals(call.getBoolean("voice", true)));
        svc.putExtra("sound", Boolean.TRUE.equals(call.getBoolean("sound", true)));
        svc.putExtra("vibrate", Boolean.TRUE.equals(call.getBoolean("vibrate", true)));
        svc.putExtra("notifId", 999000);
        svc.putExtra("reqCode", 999000);
        try { ContextCompat.startForegroundService(getContext(), svc); }
        catch (Exception e) { try { getContext().startService(svc); } catch (Exception ignored) {} }
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
        r.put("scheduledCount", count);
        r.put("nextAt", next == Long.MAX_VALUE ? 0 : next);
        return r;
    }
}
