package com.lli.com.core;

import android.os.Handler;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;

public class ServerStatus {
    public interface PingCallback {
        void onResult(int ms, boolean ok);
    }

    public static boolean isUp(int timeoutMs) {
        return pingSync(timeoutMs) != null;
    }

    public static Integer pingSync(int timeoutMs) {
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(Db.DB_URL + "/healthz").openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            conn.disconnect();
            if (code >= 200 && code < 300) {
                return (int) (System.currentTimeMillis() - start);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void ping(final int timeoutMs, final PingCallback cb) {
        new Thread(() -> {
            final Integer ms = pingSync(timeoutMs);
            new Handler(Looper.getMainLooper()).post(() -> cb.onResult(ms == null ? -1 : ms, ms != null));
        }).start();
    }
}
