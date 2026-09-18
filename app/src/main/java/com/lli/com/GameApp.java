package com.lli.com;

import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import com.lli.com.core.Db;
import com.lli.com.core.PlayerData;
import com.lli.com.core.Sound;
import com.lli.com.core.Tts;
import com.lli.com.ui.U;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class GameApp extends Application {
    public static Context ctx;
    public static SharedPreferences prefs;
    public static PlayerData player;
    public static boolean isMuted = false;
    public static boolean ttsEnabled = true;
    public static float ttsRate = 1.0f;
    public static String ttsEngine = "";
    public static boolean isEn = false;
    public static Sound sound;
    public static Tts tts;
    public static Db db;

    public static final String MASTER_CODE = "GODOFWAR2026";
    public static final String PROTECTED_USERNAME = "User_147397";
    public static final String[] DEV_USERNAMES = {"User_147397", "User_Egyptian"};
    public static final String ADMIN_TOKEN = "";  // optional; matches server ADMIN_TOKEN when set
    // "1.Ahmed" — this account may be punished but never unbanned.
    public static final String NEVER_UNBAN_USERNAME = "User_445946";
    public static final String NEVER_UNBAN_NAME = "1.Ahmed";
    // Official support channel used by the "Contact us" button on old-version bans.
    public static final String SUPPORT_TELEGRAM_URL = "https://t.me/ClashOfLegends2";
    // The version shipped here; old-version bans lift once the client matches this.
    public static final String LATEST_VERSION = "1.0.0";

    public static boolean isDevUsername(String u) {
        if (u == null) return false;
        for (String d : DEV_USERNAMES) {
            if (d.equals(u)) return true;
        }
        return false;
    }

    // Accounts that must never be unbanned (permanent punishment locks).
    public static boolean isNeverUnban(String u) {
        return u != null && NEVER_UNBAN_USERNAME.equals(u);
    }

    // Accounts that are immune to punishment altogether (dev/staff + locked bans).
    public static boolean isProtectedUsername(String u) {
        return isDevUsername(u) || isNeverUnban(u);
    }

    // Accounts that must never receive an automatic "outdated version" ban.
    public static boolean isVersionBanExempt(String u) {
        if (u == null) return false;
        if ("User_Egyptian".equals(u)) return true;
        return isDevUsername(u) || isNeverUnban(u);
    }

    // True when a permanent ban was issued for using an outdated client. Such bans
    // clear automatically once the player updates to the current version.
    public static boolean isOldVersionBan(String banType, String reason) {
        if ("old_version".equals(banType)) return true;
        if (reason == null) return false;
        String r = reason.toLowerCase();
        return r.contains("نسخة") || r.contains("اصدار") || r.contains("إصدار")
                || r.contains("تحديث") || r.contains("قديمة")
                || r.contains("version") || r.contains("update") || r.contains("outdated");
    }

    public static void speakTts(String text) {
        if (tts != null) tts.speak(text);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile long lastMainTick = 0L;
    private static boolean watchdogStarted = false;
    private static final StringBuilder eventsBuffer = new StringBuilder();

    public static void recordEvent(String e) {
        synchronized (eventsBuffer) {
            if (eventsBuffer.length() > 6000) eventsBuffer.setLength(0);
            eventsBuffer.append(System.currentTimeMillis()).append("|").append(e).append("\n");
        }
    }

    public static String eventsText() {
        synchronized (eventsBuffer) {
            return eventsBuffer.toString();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = getApplicationContext();
        prefs = ctx.getSharedPreferences("Legends_Permanent_Save", Context.MODE_PRIVATE);
        isMuted = prefs.getBoolean("is_muted", false);
        ttsEnabled = prefs.getBoolean("tts_enabled", true);
        ttsRate = prefs.getFloat("tts_rate", 1.0f);
        ttsEngine = prefs.getString("tts_engine", "");
        String lang = Locale.getDefault().getLanguage();
        isEn = prefs.getBoolean("is_en", "en".equals(lang));
        sound = new Sound();
        tts = new Tts(ctx);
        db = new Db();
        player = new PlayerData();
        startWatchdog();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new java.io.PrintWriter(sw));
                String header = "=== خطأ" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                        + "| الخيط:" + thread.getName() + "===\n";
                String full = header + sw.toString();
                writeLog("crash_log.txt", full);
                writeLog("anr_log.txt", full + "\n\n(الخيط الرئيسي انهار، لذلك تم إنهاء التطبيق وإعادة فتحه)\n");
            } catch (Exception ignored) {}
            boolean isMainThread = thread == Looper.getMainLooper().getThread();
            if (isMainThread) {
                new Thread(() -> {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                    android.os.Process.killProcess(android.os.Process.myPid());
                }).start();
                return;
            }
            // Background-thread failures (network/WebRTC/voice callbacks, etc.) are
            // logged above but must NOT take down the whole game. Android normally
            // terminates only the offending thread here; killing the process turned
            // any transient voice/network hiccup into a "sudden exit"/kick.
        });
    }

    private void startWatchdog() {
        if (watchdogStarted) return;
        watchdogStarted = true;
        lastMainTick = System.currentTimeMillis();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                lastMainTick = System.currentTimeMillis();
                MAIN.postDelayed(this, 500);
            }
        });
        Thread wd = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
                if (System.currentTimeMillis() - lastMainTick > 4000) {
                    try {
                        StringBuilder sb = new StringBuilder();
                        sb.append("=== تجمّد").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("===\n\n");
                        sb.append("--- آخر الأحداث قبل التجمّد ---\n").append(eventsText()).append("\n");
                        // Dump the main thread FIRST (it is the source of the freeze),
                        // then every other thread.
                        Thread mainThread = Looper.getMainLooper().getThread();
                        java.util.Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
                        StackTraceElement[] mainStack = all.get(mainThread);
                        if (mainStack != null) {
                            sb.append("---main<<< الخيط الرئيسي---\n");
                            for (StackTraceElement st : mainStack) sb.append(st.toString()).append("\n");
                            sb.append("\n");
                        }
                        for (Map.Entry<Thread, StackTraceElement[]> en : all.entrySet()) {
                            if (en.getKey() == Thread.currentThread() || en.getKey() == mainThread) continue;
                            String n = en.getKey().getName();
                            sb.append("---").append(n).append("---\n");
                            for (StackTraceElement st : en.getValue()) sb.append(st.toString()).append("\n");
                            sb.append("\n");
                        }
                        if (!isMainThreadAlive()) {
                            sb.append("\n*** الخيط الرئيسي غير موجود = انهار التطبيق. هذا هو الخطأ الأصلي:***\n");
                            sb.append(readCrashLog()).append("\n");
                        }
                        writeLog("anr_log.txt", sb.toString());
                        lastMainTick = System.currentTimeMillis();
                    } catch (Exception ignored) {}
                }
            }
        });
        wd.setDaemon(true);
        wd.start();
    }

    public static String readAnrLog() {
        try {
            FileInputStream fis = ctx.openFileInput("anr_log.txt");
            byte[] buf = new byte[fis.available()];
            int read = fis.read(buf);
            fis.close();
            if (read > 0) return new String(buf, 0, read, "UTF-8");
            return T("لا يوجد سجل تجمّد بعد.", "No freeze log yet.");
        } catch (Exception e) {
            return T("لا يوجد سجل تجمّد بعد.", "No freeze log yet.");
        }
    }

    public static String readCrashLog() {
        try {
            FileInputStream fis = ctx.openFileInput("crash_log.txt");
            byte[] buf = new byte[fis.available()];
            int read = fis.read(buf);
            fis.close();
            if (read > 0) return new String(buf, 0, read, "UTF-8");
            return T("لا يوجد سجل أخطاء بعد.", "No error log yet.");
        } catch (Exception e) {
            return T("لا يوجد سجل أخطاء بعد.", "No error log yet.");
        }
    }

    public static String getLogFolderPath() {
        try {
            File dir = new File(ctx.getExternalFilesDir(null), "logs");
            return dir.getAbsolutePath();
        } catch (Exception e) {
            return "Download/ClashLegends";
        }
    }

    public static void writeLog(String fileName, String content) {
        try {
            FileOutputStream fos = ctx.openFileOutput(fileName, Context.MODE_PRIVATE);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {}
        try {
            File dir = new File(ctx.getExternalFilesDir(null), "logs");
            dir.mkdirs();
            FileOutputStream fos = new FileOutputStream(new File(dir, fileName));
            fos.write(content.getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClashLegends");
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(content.getBytes("UTF-8"));
                        os.close();
                    }
                }
            } catch (Exception ignored) {}
        } else {
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ClashLegends");
                dir.mkdirs();
                FileOutputStream fos = new FileOutputStream(new File(dir, fileName));
                fos.write(content.getBytes("UTF-8"));
                fos.close();
            } catch (Exception ignored) {}
        }
    }

    public static String externalLogFileName() {
        return "anr_log.txt";
    }

    public static void ensureLogFolder() {
        String info = T("هذا المجلد يخص لعبة أترايثيا — عالم الأساطير.\n"
                + "عند حدوث تجمّد أو خطأ يظهر هنا ملفان:\n"
                + "anr_log.txt = سبب تجمّد الأزرار (موضع الخيط الرئيسي)\n"
                + "crash_log.txt = تفاصيل أي انهيار\n"
                + "إن ظهر أحدهما انسخ محتواه وأرسله للمطور.\n",
                "This folder belongs to the game Oranthia — World of Legends.\n"
                        + "When a freeze or error occurs, two files appear here:\n"
                        + "anr_log.txt = reason for frozen buttons (main thread location)\n"
                        + "crash_log.txt = details of any crash\n"
                        + "If either appears, copy its content and send it to the developer.\n");
        writeLog("معلومات_المجلد.txt", info);
    }

    public static String readAnrMainStack() {
        String log = readAnrLog();
        if (log == null) return null;
        int start = log.indexOf("--- main");
        if (start < 0) {
            if (log.contains("لا يوجد سجل") || log.contains("No freeze log") || log.contains("No error log")) return null;
            return log;
        }
        int end = log.indexOf("\n---", start + 6);
        if (end < 0) end = log.length();
        int cap = Math.min(end, start + 1800);
        return log.substring(start, cap);
    }

    public static String readDiagnostics() {
        String crash = readCrashLog();
        if (!crash.contains("لا يوجد سجل أخطاء") && !crash.contains("No error log")) {
            return crash;
        }
        String anr = readAnrMainStack();
        if (anr == null) return T("لا توجد تفاصيل محفوظة بعد.", "No saved details yet.");
        return anr;
    }

    public static Context uiCtx() {
        try {
            if (MainActivity.inst != null) {
                android.view.View decor = MainActivity.inst.getWindow().getDecorView();
                if (decor != null && decor.getWindowToken() != null) {
                    return MainActivity.inst;
                }
            }
        } catch (Exception ignored) {}
        return ctx;
    }

    public static boolean isMainThreadAlive() {
        boolean found = false;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("main".equals(t.getName())) {
                found = true;
                break;
            }
        }
        return found;
    }

    public static String T(String ar, String en) {
        return isEn ? en : ar;
    }

    public static String rarity(String ar) {
        if (ar == null) return ar;
        switch (ar) {
            case "عادي": return T(ar, "Common");
            case "غير عادي": return T(ar, "Uncommon");
            case "نادر": return T(ar, "Rare");
            case "ملحمي": return T(ar, "Epic");
            case "أسطوري": return T(ar, "Legendary");
            case "خرافي": return T(ar, "Mythical");
            case "خرافي مطلق": return T(ar, "Ultimate Mythical");
        }
        return ar;
    }

    public static void setLanguage(boolean en) {
        isEn = en;
        if (prefs != null) prefs.edit().putBoolean("is_en", en).apply();
        try {
            com.lli.com.ui.StrategicDuelSystem.regenerate();
        } catch (Throwable ignored) {}
    }

    public static boolean hasRecentReport(long withinMs) {
        try {
            File anr = new File(ctx.getFilesDir(), "anr_log.txt");
            File crash = new File(ctx.getFilesDir(), "crash_log.txt");
            long now = System.currentTimeMillis();
            return (anr.exists() && now - anr.lastModified() < withinMs)
                    || (crash.exists() && now - crash.lastModified() < withinMs);
        } catch (Exception e) {
            return false;
        }
    }
}
