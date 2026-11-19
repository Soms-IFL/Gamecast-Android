package com.iflgamecast.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Fires at a game's start time, double-checks with the server, and posts the notification. */
public class GameAlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "game_start";

    @Override
    public void onReceive(final Context ctx, final Intent intent) {
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                handle(ctx.getApplicationContext(), intent);
            } catch (Exception ignored) {
            } finally {
                result.finish();
            }
        }).start();
    }

    private void handle(Context c, Intent intent) throws Exception {
        String id = intent.getStringExtra("id");
        String label = intent.getStringExtra("label");
        long ms = intent.getLongExtra("ms", 0);
        if (id == null) return;

        String title = "Game starting: " + (label == null || label.isEmpty() ? id : label);
        String text = "Kickoff — tap to watch live";

        try {
            JSONObject g = fetch(GameAlarms.base(c) + "/api/v1/games/" + id).getJSONObject("game");
            String status = g.optString("status");
            if ("final".equals(status)) return;                                   // already over
            if ("scheduled".equals(status)) {
                if (g.isNull("startsAtMs")) return;                              // schedule paused / unset
                long startsAt = g.getLong("startsAtMs");
                if (startsAt > System.currentTimeMillis() + 120_000) {           // moved later: try again then
                    GameAlarms.schedule(c, id, label == null ? "" : label, startsAt);
                    return;
                }
            }
            JSONObject home = g.getJSONObject("home"), away = g.getJSONObject("away");
            title = "Game starting: " + away.optString("abbr") + " @ " + home.optString("abbr");
            text = away.optString("name") + " at " + home.optString("name") + " — live now";
        } catch (Exception offline) {
            // no network: fall back to what we scheduled with
        }
        notifyUser(c, id, title, text);
    }

    private JSONObject fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("Accept", "application/json");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } finally {
            con.disconnect();
        }
    }

    private void notifyUser(Context c, String id, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.areNotificationsEnabled()) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Game start alerts", NotificationManager.IMPORTANCE_HIGH));

        Intent open = new Intent(c, MainActivity.class)
                .putExtra("route", "#/game/" + id)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_football)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build();
        nm.notify(id.hashCode(), n);
    }
}
