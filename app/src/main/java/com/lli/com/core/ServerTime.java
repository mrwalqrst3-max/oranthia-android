package com.lli.com.core;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServerTime {
    public interface Callback {
        void onResult(long unixTime, boolean ok);
    }

    // رابط خادمك على Render (نفس رابط Db.DB_URL)
    private static final String SERVER_URL = "https://oranthia-backend.onrender.com";

    private static volatile long cached = 0;

    public static long now() {
        if (cached != 0) return cached;
        return System.currentTimeMillis() / 1000;
    }

    public static void fetch(final Callback cb) {
        final long local = System.currentTimeMillis() / 1000;
        new Thread(() -> {
            long t = local;
            boolean ok = false;
            Long v = fetchFrom(SERVER_URL + "/time", 6000);
            if (v != null) {
                t = v;
                ok = true;
            } else {
                v = fetchFrom("https://timeapi.io/api/Time/current/zone?timeZone=UTC", 8000);
                if (v != null) {
                    t = v;
                    ok = true;
                }
            }
            cached = t;
            final long ft = t;
            final boolean fok = ok;
            new Handler(Looper.getMainLooper()).post(() -> cb.onResult(ft, fok));
        }).start();
    }

    private static Long fetchFrom(String urlStr, int timeoutMs) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                org.json.JSONObject o = new org.json.JSONObject(sb.toString());
                if (o.has("unixTime")) return (long) Math.floor(o.getDouble("unixTime"));
                if (o.has("unixtime")) return (long) Math.floor(o.getDouble("unixtime"));
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return null;
    }
}
