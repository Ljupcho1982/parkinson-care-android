package com.ljupcho.parkinsoncare;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Schedules exact daily medication alarms via AlarmManager and persists them across reboots. */
public class AlarmScheduler {

    private static final String PREFS = "pk_native";
    private static final String K_ITEMS = "items";
    private static final String K_LANG = "lang";
    private static final String K_VOICE = "voice";
    private static final String K_SOUND = "sound";
    private static final String K_VIBRATE = "vibrate";
    private static final String K_SNOOZE = "snooze";

    static final int SNOOZE_OFFSET = 700000;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveAndSchedule(Context ctx, String itemsJson, String lang,
                                       boolean voice, boolean sound, boolean vibrate, int snooze) {
        cancelAll(ctx); // remove previously scheduled alarms first
        prefs(ctx).edit()
                .putString(K_ITEMS, itemsJson == null ? "[]" : itemsJson)
                .putString(K_LANG, lang)
                .putBoolean(K_VOICE, voice)
                .putBoolean(K_SOUND, sound)
                .putBoolean(K_VIBRATE, vibrate)
                .putInt(K_SNOOZE, snooze)
                .apply();
        scheduleAllFromPrefs(ctx);
    }

    public static void scheduleAllFromPrefs(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String itemsJson = p.getString(K_ITEMS, "[]");
        String lang = p.getString(K_LANG, "auto");
        boolean voice = p.getBoolean(K_VOICE, true);
        boolean sound = p.getBoolean(K_SOUND, true);
        boolean vibrate = p.getBoolean(K_VIBRATE, true);
        try {
            JSONArray arr = new JSONArray(itemsJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "item" + i);
                String name = o.optString("name", "Medication");
                String dose = o.optString("dose", "");
                int hour = o.optInt("hour", 8);
                int minute = o.optInt("minute", 0);
                int reqCode = Math.abs(id.hashCode());
                long when = nextTrigger(hour, minute, 0);
                setExact(ctx, when, buildPI(ctx, reqCode, reqCode, name, dose, hour, minute, lang, voice, sound, vibrate, false));
            }
        } catch (Exception ignored) {}
    }

    public static void cancelAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        String itemsJson = prefs(ctx).getString(K_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(itemsJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "item" + i);
                int reqCode = Math.abs(id.hashCode());
                Intent intent = new Intent(ctx, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(ctx, reqCode, intent, piFlags(true));
                if (am != null) am.cancel(pi);
            }
        } catch (Exception ignored) {}
    }

    /** Called by the receiver after an alarm fires so it repeats the next day. */
    public static void rescheduleNextDay(Context ctx, Intent fired) {
        int reqCode = fired.getIntExtra("reqCode", 0);
        int hour = fired.getIntExtra("hour", 8);
        int minute = fired.getIntExtra("minute", 0);
        long when = nextTrigger(hour, minute, 1); // strictly tomorrow
        PendingIntent pi = buildPI(ctx, reqCode, reqCode,
                fired.getStringExtra("name"), fired.getStringExtra("dose"),
                hour, minute, fired.getStringExtra("lang"),
                fired.getBooleanExtra("voice", true), fired.getBooleanExtra("sound", true),
                fired.getBooleanExtra("vibrate", true), false);
        setExact(ctx, when, pi);
    }

    /** Schedules a one-off REAL alarm a few seconds out, so the user can lock the phone and test the actual path. */
    public static void scheduleTestInSeconds(Context ctx, int seconds, String lang,
                                             boolean voice, boolean sound, boolean vibrate) {
        long when = System.currentTimeMillis() + (long) seconds * 1000L;
        int reqCode = 888001;
        PendingIntent pi = buildPI(ctx, reqCode, reqCode, "TEST — Levodopa", "100/25 mg · 1 tablet",
                -1, -1, lang, voice, sound, vibrate, true); // isSnooze=true so it doesn't re-arm for "tomorrow"
        setExact(ctx, when, pi);
    }

    public static void scheduleSnooze(Context ctx, String name, String dose, String lang,
                                      boolean voice, boolean sound, boolean vibrate,
                                      int baseReqCode, int minutes) {
        long when = System.currentTimeMillis() + (long) minutes * 60_000L;
        int reqCode = baseReqCode + SNOOZE_OFFSET;
        PendingIntent pi = buildPI(ctx, reqCode, reqCode, name, dose, -1, -1, lang, voice, sound, vibrate, true);
        setExact(ctx, when, pi);
    }

    // ---- helpers ----

    private static long nextTrigger(int hour, int minute, int minDaysAhead) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (minDaysAhead > 0) {
            c.add(Calendar.DAY_OF_YEAR, minDaysAhead);
        } else if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    private static PendingIntent buildPI(Context ctx, int piRequestCode, int reqCode,
                                         String name, String dose, int hour, int minute,
                                         String lang, boolean voice, boolean sound, boolean vibrate,
                                         boolean isSnooze) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra("reqCode", reqCode);
        intent.putExtra("notifId", reqCode);
        intent.putExtra("name", name);
        intent.putExtra("dose", dose);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("lang", lang);
        intent.putExtra("voice", voice);
        intent.putExtra("sound", sound);
        intent.putExtra("vibrate", vibrate);
        intent.putExtra("isSnooze", isSnooze);
        return PendingIntent.getBroadcast(ctx, piRequestCode, intent, piFlags(false));
    }

    private static int piFlags(boolean forCancelLookup) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private static void setExact(Context ctx, long whenMillis, PendingIntent pi) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                // The real "alarm-clock" API: fires even in deep doze, needs no special permission,
                // and grants a temporary allowlist so we CAN start the alarm UI/audio from the background.
                Intent showIntent = new Intent(ctx, MainActivity.class);
                showIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                int sf = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) sf |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent show = PendingIntent.getActivity(ctx, 424242, showIntent, sf);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(whenMillis, show), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            }
        } catch (Exception e) {
            try {
                if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
                else am.set(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            } catch (Exception ignored) {}
        }
    }
}
