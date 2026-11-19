package com.iflgamecast.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps one alarm per upcoming game. The list is stored so alarms can be restored after a reboot. */
final class GameAlarms {
    static final String ACTION = "com.iflgamecast.app.GAME_START";
    private static final String PREFS = "ifl_alarms";
    private static final String KEY_BASE = "base";
    private static final String KEY_LIST = "list";

    private GameAlarms() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String base(Context c) {
        return prefs(c).getString(KEY_BASE, "https://iflgamecast.colebolebole.xyz");
    }

    /** Replace all alarms with the given games: [{id, ms, label}]. */
    static void apply(Context c, String base, JSONArray games) {
        cancelAll(c);
        prefs(c).edit().putString(KEY_BASE, base).putString(KEY_LIST, games.toString()).apply();
        scheduleStored(c);
    }

    static void scheduleStored(Context c) {
        try {
            JSONArray list = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                long ms = o.getLong("ms");
                if (ms > now - 60_000) schedule(c, o.getString("id"), o.optString("label", ""), ms);
            }
        } catch (Exception ignored) { }
    }

    static void cancelAll(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            JSONArray list = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                am.cancel(pending(c, o.getString("id"), o.optString("label", ""), o.getLong("ms")));
            }
        } catch (Exception ignored) { }
        prefs(c).edit().putString(KEY_LIST, "[]").apply();
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    static void schedule(Context c, String id, String label, long ms) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c, id, label, ms);
        if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);   // inexact: may arrive a few minutes late
    }

    private static PendingIntent pending(Context c, String id, String label, long ms) {
        Intent i = new Intent(c, GameAlarmReceiver.class).setAction(ACTION);
        i.putExtra("id", id).putExtra("label", label).putExtra("ms", ms);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
