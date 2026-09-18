package com.lli.com.core;

import android.content.Context;

import com.lli.com.GameApp;
import com.lli.com.ui.GameNav;
import com.lli.com.ui.UI;
import com.lli.com.ui.U;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Local-first storage. The player's full data file lives INSIDE the app on the
 * device and is the authoritative source of truth. A backup is pushed to the
 * server, but the game never depends on the server to open.
 */
public class SaveSystem {
    private static final String KEY = "player_data";
    private static final String DB_FILE = "player_db.json";
    private static final java.util.concurrent.ExecutorService SAVE_EXEC =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private static File dbFile() {
        return new File(GameApp.ctx.getFilesDir(), DB_FILE);
    }

    // Write to the internal DB file (durable local database) + SharedPreferences.
    public static void save() {
        if (GameApp.player == null) return;
        SAVE_EXEC.execute(() -> {
            try {
                JSONObject o = GameApp.player.toJSON();
                String s = o.toString();
                // primary: internal file
                try {
                    FileOutputStream fos = new FileOutputStream(dbFile());
                    fos.write(s.getBytes("UTF-8"));
                    fos.flush();
                    fos.getFD().sync();
                    fos.close();
                } catch (Exception ignored) {}
                // fallback/compat: SharedPreferences
                GameApp.prefs.edit().putString(KEY, s).apply();
            } catch (Exception ignored) {}
        });
    }

    // Try the internal DB file first, then the legacy SharedPreferences copy.
    private static String readRaw() {
        try {
            File f = dbFile();
            if (f.exists()) {
                FileInputStream fis = new FileInputStream(f);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
                fis.close();
                if (bos.size() > 0) return bos.toString("UTF-8");
            }
        } catch (Exception ignored) {}
        try {
            String s = GameApp.prefs.getString(KEY, "");
            return (s == null || s.isEmpty()) ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean load() {
        try {
            String s = readRaw();
            if (s == null || s.isEmpty()) return false;
            JSONObject o = new JSONObject(s);
            GameApp.player = PlayerData.fromJSON(o);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasLocalData() {
        try {
            return dbFile().exists() || (GameApp.prefs.getString(KEY, "") != null && !GameApp.prefs.getString(KEY, "").isEmpty());
        } catch (Exception e) {
            return false;
        }
    }

    public static void saveAndRefresh() {
        if (GameApp.player == null) return;
        int removed = GameApp.player.enforceBagLimits();
        GameApp.player.gameVersion = com.lli.com.BuildConfig.VERSION_NAME;
        if (removed > 0) {
            final int r = removed;
            UI.post(() -> U.toast(GameApp.T("تم حذف " + r + " عنصر زائد عن سعة الحقيبة (الحد: " + GameApp.player.bagLimitPerSection() + " لكل قسم). وسّع الحقيبة من الحقيبة والمعدات.",
                    "Deleted " + r + " items over the bag limit (" + GameApp.player.bagLimitPerSection() + " per section). Expand your bag from Bag & Equipment.")));
        }
        updatePower();
        GameApp.player.lastOnline = System.currentTimeMillis() / 1000;
        save();
        GameNav.updateInfo();
        GameApp.sound.playSnd("click.mp3");
        pushCloudAsync();
    }

    // Same as saveAndRefresh() but without the UI click sound — used for
    // automated/repeating saves (exploration steps, arena polling, etc.).
    public static void saveAndRefreshQuiet() {
        if (GameApp.player == null) return;
        GameApp.player.enforceBagLimits();
        updatePower();
        GameApp.player.lastOnline = System.currentTimeMillis() / 1000;
        save();
        GameNav.updateInfo();
        pushCloudAsync();
    }

    // Push a full backup to the server (best-effort, never blocking).
    public static void pushCloudAsync() {
        if (GameApp.player == null || GameApp.player.username.isEmpty()) return;
        GameApp.player.gameVersion = com.lli.com.BuildConfig.VERSION_NAME;
        final String path = Db.playerPath().substring(0, Db.playerPath().length() - 1);
        new Thread(() -> {
            try {
                updatePower();
                // Keep the online status fresh even while idle (the UI shows an
                // idle player as "offline" once last_online is older than ~2 min).
                GameApp.player.lastOnline = System.currentTimeMillis() / 1000;
                JSONObject data = GameApp.player.toJSON();
                if (Db.put(path, data) != null) {
                    GameApp.prefs.edit().putString("saved_id", GameApp.player.username).apply();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public static void updatePower() {
        if (GameApp.player == null) return;
        PlayerData p = GameApp.player;
        double levelBonus = 1 + (p.level * 0.02);
        double power = ((p.stats.strength * 10) + (p.stats.endurance * 5) + (p.stats.maxHp / 2)
                + (p.stats.agility * 5) + (p.stats.luck * 5)) * levelBonus;
        p.totalPower = (long) Math.floor(power);
    }

    // Synchronous backup push (used on critical saves).
    public static void pushCloud() {
        if (GameApp.player == null || GameApp.player.username.isEmpty()) return;
        updatePower();
        GameApp.player.gameVersion = com.lli.com.BuildConfig.VERSION_NAME;
        Db.put(Db.playerPath().substring(0, Db.playerPath().length() - 1), GameApp.player.toJSON());
        GameApp.prefs.edit().putString("saved_id", GameApp.player.username).apply();
    }

    // ============ Global-state backup for recovery (Render redeploys) ============
    // Every device holds a copy of the server-only global keys (tribes_system,
    // medals, world_boss). The server extracts `_server_backup` from the player
    // record into its `recovery/` area and restores any MISSING global key from
    // these backups — so a wiped container filesystem never kills tribes,
    // leaderboards or the world boss.
    private static volatile long lastServerBackupPush = 0;

    public static void maybeRefreshServerBackup() {
        if (GameApp.player == null || GameApp.player.username == null || GameApp.player.username.isEmpty()) return;
        final long now = System.currentTimeMillis();
        if (now - lastServerBackupPush < 5 * 60 * 1000L) return;
        lastServerBackupPush = now;
        refreshServerBackupAsync();
    }

    // Called right after login so the recovery copy is fresh while the client
    // is still on the login screen.
    public static void refreshServerBackupNow() {
        lastServerBackupPush = System.currentTimeMillis();
        refreshServerBackupAsync();
    }

    private static void refreshServerBackupAsync() {
        final String me = GameApp.player == null ? "" : GameApp.player.username;
        if (me.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject data = new JSONObject();
                try {
                    JSONObject tribes = Db.get("tribes_system");
                    if (tribes != null && tribes.length() > 0) data.put("tribes_system", tribes);
                } catch (Exception ignored) {}
                try {
                    JSONObject medals = Db.get("medals");
                    if (medals != null && medals.length() > 0) data.put("medals", medals);
                } catch (Exception ignored) {}
                try {
                    JSONObject boss = Db.get("world_boss");
                    if (boss != null && boss.length() > 0) data.put("world_boss", boss);
                } catch (Exception ignored) {}
                if (data.length() == 0) return;
                JSONObject backup = new JSONObject();
                backup.put("ts", System.currentTimeMillis() / 1000);
                backup.put("data", data);
                JSONObject patch = new JSONObject();
                patch.put("_server_backup", backup);
                Db.patch("players/" + Db.encode(me), patch);
            } catch (Exception ignored) {}
        }).start();
    }
}
