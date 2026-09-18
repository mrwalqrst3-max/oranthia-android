package com.lli.com.ui;

import android.os.Handler;
import android.os.Looper;

public class UI {
    private static final Handler H = new Handler(Looper.getMainLooper());

    public static void post(Runnable r) {
        if (r == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            H.post(r);
        }
    }

    public static void postDelayed(Runnable r, long ms) {
        if (r == null) return;
        H.postDelayed(r, ms);
    }
}
