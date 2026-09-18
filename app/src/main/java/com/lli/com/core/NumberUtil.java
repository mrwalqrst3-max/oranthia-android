package com.lli.com.core;

import com.lli.com.GameApp;

import java.util.Locale;

public class NumberUtil {
    public static String formatNumber(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) return "0";
        long l = (long) Math.floor(n);
        String s = String.valueOf(l);
        StringBuilder sb = new StringBuilder();
        boolean neg = s.startsWith("-");
        if (neg) s = s.substring(1);
        int count = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) sb.append(',');
        }
        String out = sb.reverse().toString();
        return neg ? "-" + out : out;
    }

    public static String formatRemainingTime(long timestamp) {
        long remaining = timestamp - System.currentTimeMillis() / 1000;
        if (remaining <= 0) return GameApp.T("انتهت المدة", "Time's up");
        long days = remaining / (24 * 3600);
        long hours = (remaining % (24 * 3600)) / 3600;
        long minutes = (remaining % 3600) / 60;
        long seconds = remaining % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(GameApp.T("يوم", "day"));
        if (hours > 0) sb.append(hours).append(GameApp.T("ساعة", "hour"));
        if (minutes > 0) sb.append(minutes).append(GameApp.T("دقيقة", "min"));
        if (seconds > 0 && days == 0 && hours == 0 && minutes == 0) sb.append(seconds).append(GameApp.T("ثانية", "sec"));
        String s = sb.toString();
        if (s.endsWith("")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static int rand(int min, int max) {
        if (max < min) return min;
        return min + (int) (Math.random() * (max - min + 1));
    }

    public static int rand100() {
        return rand(1, 100);
    }

    public static String fmt(String format, Object... args) {
        return String.format(Locale.US, format, args);
    }
}
