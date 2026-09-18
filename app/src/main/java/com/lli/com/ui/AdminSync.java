package com.lli.com.ui;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;

import com.lli.com.GameApp;
import com.lli.com.MainActivity;
import com.lli.com.core.Db;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pulls pending admin commands from the server and applies them directly to the
 * player's on-device data file (the authoritative local DB). Also sends a
 * keep-alive ping every 10 minutes to stop the free Render server from sleeping.
 */
public class AdminSync {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static long lastKeepAlive = 0;
    private static final long KEEP_ALIVE_INTERVAL = 2 * 60 * 1000; // 2 minutes

    private static long lastPoll = 0;
    private static final long POLL_INTERVAL = 8000; // 8 seconds

    private AdminSync() {}

    /** Call from the LiveNotifier background thread. */
    public static void tick() {
        if (GameApp.player == null || GameApp.player.username == null || GameApp.player.username.isEmpty()) return;
        long now = System.currentTimeMillis();

        // Keep-alive every 10 minutes to prevent Render free-tier sleep.
        if (now - lastKeepAlive > KEEP_ALIVE_INTERVAL) {
            lastKeepAlive = now;
            Db.ping();
        }

        // Poll for admin commands.
        if (now - lastPoll < POLL_INTERVAL) return;
        lastPoll = now;
        pollAndApply();
        checkStaffReplies();
        syncAuthoritativeSanctions();
    }

    private static void checkStaffReplies() {
        final PlayerData p = GameApp.player;
        if (p == null) return;
        boolean staff = p.isDev || p.adminData;
        if (!staff) return;
        final String me = p.username;
        new Thread(() -> {
            try {
                JSONObject replies = Db.get("staff_replies/" + Db.encode(me));
                if (replies == null || replies.length() == 0) return;
                long last = GameApp.prefs.getLong("last_staff_reply_seen", 0);
                long best = last;
                Iterator<String> it = replies.keys();
                while (it.hasNext()) {
                    JSONObject r = replies.optJSONObject(it.next());
                    if (r == null) continue;
                    long t = r.optLong("time", 0);
                    if (t > best) best = t;
                }
                if (best > last) {
                    final long newSeen = best;
                    GameApp.prefs.edit().putLong("last_staff_reply_seen", newSeen).apply();
                    UI.post(() -> {
                        GameApp.sound.playSnd("msg.mp3");
                        U.toast(GameApp.T("لديك رد جديد من لاعب!", "You have a new reply from a player!"));
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    // Admin sanctions (bans / jails / chat bans) live in dedicated collections that
    // the player's own cloud save never overwrites. Re-apply them on every poll so
    // a ban or an unban takes effect even if the one-shot command was missed.
    private static void syncAuthoritativeSanctions() {
        final PlayerData p = GameApp.player;
        if (p == null || p.username == null || p.username.isEmpty()) return;
        new Thread(() -> {
            try {
                final String me = p.username;
                boolean changed = false;
                long now = System.currentTimeMillis() / 1000;

                JSONObject ban = Db.get("bans/" + Db.encode(me));
                if (ban != null) {
                    long until = ban.optLong("until", -1);
                    boolean active = ban.optBoolean("active", false) && (until == -1 || until > now);
                    if (active) {
                        if (!p.isBanned || p.bannedUntil != until) {
                            p.isBanned = true;
                            p.bannedUntil = until;
                            p.banReason = ban.optString("reason", "");
                            p.banType = ban.optString("type", "");
                            if ("".equals(p.banType)) p.banType = null;
                            changed = true;
                            final String reason = p.banReason;
                            UI.post(() -> showBanDialog(reason));
                        }
                    } else if (!ban.optBoolean("active", true)
                            && p.isBanned && !GameApp.isNeverUnban(me)
                            && !GameApp.isOldVersionBan(p.banType, p.banReason)) {
                        p.isBanned = false;
                        p.bannedUntil = 0;
                        p.banReason = null;
                        p.banType = null;
                        changed = true;
                    }
                }

                JSONObject jail = Db.get("jails/" + Db.encode(me));
                if (jail != null) {
                    long until = jail.optLong("until", -1);
                    boolean active = jail.optBoolean("active", false) && (until == -1 || until > now);
                    if (active) {
                        if (!p.isJailed || p.jailUntil != until) {
                            p.isJailed = true;
                            p.jailUntil = until;
                            p.jailReason = jail.optString("reason", "");
                            changed = true;
                            final String reason = p.jailReason;
                            UI.post(() -> showJailDialog(reason));
                        }
                    } else if (!jail.optBoolean("active", true) && p.isJailed) {
                        p.isJailed = false;
                        p.jailUntil = 0;
                        p.jailReason = null;
                        changed = true;
                    }
                }

                JSONObject cb = Db.get("chat_bans/" + Db.encode(me));
                if (cb != null) {
                    long until = cb.optLong("until", -1);
                    boolean active = cb.optBoolean("active", false) && (until == -1 || until > now);
                    if (active) {
                        if (!p.chatBanned || p.chatBanUntil != until) {
                            p.chatBanned = true;
                            p.chatBanUntil = until;
                            p.chatBanReason = cb.optString("reason", "");
                            changed = true;
                            final long fUntil = until;
                            final String fr = p.chatBanReason;
                            UI.post(() -> ChatSystem.forceChatBanKick(fUntil, fr));
                        }
                    } else if (!cb.optBoolean("active", true) && p.chatBanned) {
                        p.chatBanned = false;
                        p.chatBanUntil = 0;
                        p.chatBanReason = null;
                        changed = true;
                    }
                }

                if (changed) SaveSystem.save();
            } catch (Exception ignored) {}
        }).start();
    }

    private static void pollAndApply() {
        final String me = GameApp.player.username;
        new Thread(() -> {
            try {
                JSONObject cmds = Db.getCommandsShallow(me);
                if (cmds == null) return;
                List<String> seqs = new ArrayList<>();
                Iterator<String> it = cmds.keys();
                while (it.hasNext()) seqs.add(it.next());
                if (seqs.isEmpty()) return;
                java.util.Collections.sort(seqs, (a, b) -> {
                    try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
                    catch (Exception e) { return a.compareTo(b); }
                });
                for (final String seq : seqs) {
                    JSONObject cmd = Db.get("admin_commands/" + Db.encode(me) + "/" + Db.encode(seq));
                    if (cmd == null) continue;
                    applyCommand(cmd);
                    Db.acknowledgeCommand(me, seq);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private static void applyCommand(JSONObject cmd) {
        if (cmd == null) return;
        final String type = cmd.optString("type", "");
        PlayerData p = GameApp.player;
        if (p == null) return;
        long now = System.currentTimeMillis() / 1000;

        switch (type) {
            case "ban": {
                String banType = cmd.optString("ban_type", "");
                String reason = cmd.optString("ban_reason", cmd.optString("reason", "غير محدد"));
                // An "old version" ban must never apply to a client already running
                // the current build: clear it locally and tell the server to drop it.
                if (!GameApp.isNeverUnban(p.username)
                        && GameApp.isOldVersionBan(banType, reason)
                        && GameApp.LATEST_VERSION.equals(com.lli.com.BuildConfig.VERSION_NAME)) {
                    p.isBanned = false;
                    p.bannedUntil = 0;
                    p.banReason = null;
                    p.banType = null;
                    SaveSystem.save();
                    try {
                        JSONObject patch = new JSONObject();
                        patch.put("is_banned", false);
                        patch.put("banned_until", 0);
                        patch.put("ban_reason", JSONObject.NULL);
                        patch.put("ban_type", JSONObject.NULL);
                        Db.patch("players/" + Db.encode(p.username), patch);
                    } catch (Exception ignored) {}
                    break;
                }
                long until = cmd.optLong("banned_until", -1);
                p.isBanned = true;
                p.bannedUntil = until;
                p.banType = banType.isEmpty() ? null : banType;
                p.banReason = reason;
                SaveSystem.save();
                if (p.bannedUntil == -1 || p.bannedUntil > now) {
                    UI.post(() -> showBanDialog(p.banReason));
                }
                break;
            }
            case "unban": {
                // The never-unban account stays banned regardless of server commands.
                if (GameApp.isNeverUnban(p.username)) break;
                p.isBanned = false;
                p.bannedUntil = 0;
                p.banReason = null;
                p.banType = null;
                SaveSystem.save();
                break;
            }
            case "jail": {
                long until = cmd.optLong("jail_until", -1);
                p.isJailed = true;
                p.jailUntil = until;
                p.jailReason = cmd.optString("jail_reason", cmd.optString("reason", "غير محدد"));
                SaveSystem.save();
                if (p.jailUntil == -1 || p.jailUntil > now) {
                    UI.post(() -> showJailDialog(p.jailReason));
                }
                break;
            }
            case "unjail": {
                p.isJailed = false;
                p.jailUntil = 0;
                p.jailReason = null;
                SaveSystem.save();
                break;
            }
            case "chat_ban": {
                long until = cmd.optLong("chat_ban_until", -1);
                p.chatBanned = true;
                p.chatBanUntil = until;
                p.chatBanReason = cmd.optString("chat_ban_reason", cmd.optString("reason", "غير محدد"));
                SaveSystem.save();
                if (p.chatBanUntil == -1 || p.chatBanUntil > now) {
                    UI.post(() -> ChatSystem.forceChatBanKick(p.chatBanUntil, p.chatBanReason));
                }
                break;
            }
            case "chat_unban": {
                p.chatBanned = false;
                p.chatBanUntil = 0;
                p.chatBanReason = null;
                SaveSystem.save();
                break;
            }
            case "rename": {
                String nn = cmd.optString("new_name", cmd.optString("name", ""));
                if (!nn.isEmpty()) {
                    p.name = nn;
                    SaveSystem.save();
                    UI.post(() -> showDevNotice(GameApp.T("تم تغيير اسمك من قبل المطورين إلى:", "The developers changed your name to:") + nn));
                }
                break;
            }
            case "set_data": {
                // Merge selected scalar fields into the local player.
                applySetData(cmd);
                SaveSystem.save();
                break;
            }
            case "sync_data": {
                // Full replacement of the player data from an admin edit.
                JSONObject data = cmd.optJSONObject("data");
                if (data != null && applySyncData(data)) {
                    UI.post(() -> showDevNotice(GameApp.T("قام المطورون بتعديل بياناتك على الخادم وتم تطبيقها الآن على جهازك!", "The developers modified your data on the server, and it has now been applied to your device!")));
                }
                SaveSystem.save();
                break;
            }
            case "device_ban": {
                // Device (IP) ban is enforced server-side via device_bans; kick the
                // player off the game immediately (no option to postpone).
                String reason = cmd.optString("reason", "غير محدد");
                final long fUntil = cmd.optLong("banned_until", -1);
                // Staff accounts are immune to device (IP) bans.
                boolean staff = GameApp.player != null && (GameApp.player.isDev || GameApp.player.adminData);
                JSONObject devBan = staff ? null : Db.get("device_bans/" + Db.encode(Db.myIp()));
                final String finalReason;
                if (devBan != null) finalReason = devBan.optString("reason", reason);
                else finalReason = reason;
                if (devBan != null || fUntil == -1 || fUntil > now) {
                    UI.post(() -> showDeviceBanDialog(finalReason));
                }
                break;
            }
            case "delete_account": {
                UI.post(() -> {
                    try {
                        AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
                        b.setTitle(GameApp.T("تم حذف حسابك", "Your Account Was Deleted"));
                        b.setView(U.msg(GameApp.T("تم حذف حسابك من قبل الإدارة.", "Your account was deleted by the administration.")));
                        b.setCancelable(false);
                        b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> {
                            if (MainActivity.inst != null) MainActivity.inst.finish();
                        });
                        b.show();
                    } catch (Exception ignored) {}
                });
                break;
            }
            default:
                break;
        }
    }

    private static void applySetData(JSONObject cmd) {
        PlayerData p = GameApp.player;
        if (p == null) return;
        final List<String> changes = new ArrayList<>();
        Iterator<String> it = cmd.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.equals("type") || k.equals("seq") || k.equals("ts")) continue;
            try {
                switch (k) {
                    case "name": {
                        String v = cmd.getString(k);
                        if (!p.name.equals(v)) {
                            p.name = v;
                            changes.add(GameApp.T("تم تغيير اسمك من قبل المطورين إلى:", "The developers changed your name to:") + v);
                        }
                        break;
                    }
                    case "level": {
                        int v = cmd.getInt(k);
                        if (p.level != v) {
                            p.level = v;
                            p.maxExp = 100 + (v - 1) * 100;
                            changes.add(GameApp.T("تم تعديل مستواك من قبل المطورين إلى:", "The developers modified your level to:") + v);
                        }
                        break;
                    }
                    case "exp": {
                        double v = cmd.getDouble(k);
                        if (p.exp != v) {
                            p.exp = v;
                            changes.add(GameApp.T("تم تعديل خبرتك من قبل المطورين إلى:", "The developers modified your experience to:") + (long) Math.floor(v) + "%");
                        }
                        break;
                    }
                    case "points": {
                        int v = cmd.getInt(k);
                        if (p.points != v) {
                            p.points = v;
                            changes.add(GameApp.T("تم تعديل نقاط تطويرك من قبل المطورين إلى:", "The developers modified your upgrade points to:") + v);
                        }
                        break;
                    }
                    case "gold": {
                        long v = cmd.getLong(k);
                        if (p.gold != v) {
                            p.gold = v;
                            changes.add(GameApp.T("تم تعديل ذهبك من قبل المطورين إلى:", "The developers modified your gold to:") + NumberUtil.formatNumber(v));
                        }
                        break;
                    }
                    case "bank_gold": {
                        long v = cmd.getLong(k);
                        if (p.bankGold != v) {
                            p.bankGold = v;
                            changes.add(GameApp.T("تم تعديل ذهب البنك الخاص بك من قبل المطورين إلى:", "The developers modified your bank gold to:") + NumberUtil.formatNumber(v));
                        }
                        break;
                    }
                    case "crystals": {
                        long v = cmd.getLong(k);
                        if (p.crystals != v) {
                            p.crystals = v;
                            changes.add(GameApp.T("تم تعديل كريستالك من قبل المطورين إلى:", "The developers modified your crystals to:") + NumberUtil.formatNumber(v));
                        }
                        break;
                    }
                    case "diamonds": {
                        long v = cmd.getLong(k);
                        if (p.diamonds != v) {
                            p.diamonds = v;
                            changes.add(GameApp.T("تم تعديل ألماسك من قبل المطورين إلى:", "The developers modified your diamonds to:") + NumberUtil.formatNumber(v));
                        }
                        break;
                    }
                    case "admin_data": {
                        boolean v = cmd.getBoolean(k);
                        if (p.adminData != v) {
                            p.adminData = v;
                            changes.add(GameApp.T("تم تعديل صلاحيات الإدارة الخاصة بك من قبل المطورين.", "The developers modified your administration permissions."));
                        }
                        break;
                    }
                    case "is_dev": {
                        boolean v = cmd.getBoolean(k);
                        if (p.isDev != v) {
                            p.isDev = v;
                            changes.add(GameApp.T("تم تعديل صلاحيات المطور الخاصة بك من قبل المطورين.", "The developers modified your developer permissions."));
                        }
                        break;
                    }
                    case "can_manage_shop": {
                        boolean v = cmd.getBoolean(k);
                        if (p.canManageShop != v) {
                            p.canManageShop = v;
                            changes.add(GameApp.T("تم تعديل صلاحية إدارة المتجر الخاصة بك من قبل المطورين.", "The developers modified your shop-management permission."));
                        }
                        break;
                    }
                    case "can_edit_data": {
                        boolean v = cmd.getBoolean(k);
                        if (p.canEditData != v) {
                            p.canEditData = v;
                            changes.add(GameApp.T("تم تعديل صلاحية إدارة البيانات الخاصة بك من قبل المطورين.", "The developers modified your data-management permission."));
                        }
                        break;
                    }
                    default:
                        break;
                }
            } catch (Exception ignored) {}
        }
        if (!changes.isEmpty()) {
            final List<String> fChanges = new ArrayList<>(changes);
            UI.post(() -> {
                StringBuilder sb = new StringBuilder();
                for (String c : fChanges) sb.append(c).append("\n");
                try { GameApp.sound.playSnd("msg.mp3"); } catch (Exception ignored) {}
                showDevNotice(sb.toString().trim());
            });
        }
    }

    private static boolean applySyncData(JSONObject data) {
        PlayerData in = PlayerData.fromJSON(data);
        PlayerData p = GameApp.player;
        if (in == null || p == null || in.username == null || in.username.isEmpty()) return false;
        p.username = in.username;
        p.name = in.name;
        p.level = in.level;
        p.exp = in.exp;
        p.maxExp = in.maxExp;
        p.points = in.points;
        p.gold = in.gold;
        p.bankGold = in.bankGold;
        p.crystals = in.crystals;
        p.diamonds = in.diamonds;
        p.stats = in.stats;
        p.tempStats = in.tempStats;
        p.inventory = in.inventory;
        p.equipped = in.equipped;
        p.lockedChests = in.lockedChests;
        p.clan = in.clan;
        p.prestigeLevel = in.prestigeLevel;
        p.prestigeTitle = in.prestigeTitle;
        p.totalPower = in.totalPower;
        p.news = in.news;
        p.towerFloor = in.towerFloor;
        p.currentQuest = in.currentQuest;
        p.dragonLevel = in.dragonLevel;
        p.killsInArea = in.killsInArea;
        p.location = in.location;
        p.steps = in.steps;
        p.targetSteps = in.targetSteps;
        p.pet = in.pet;
        p.zombieProgress = in.zombieProgress;
        p.adminData = in.adminData;
        p.isDev = in.isDev;
        p.canManageShop = in.canManageShop;
        p.canEditData = in.canEditData;
        p.isBanned = in.isBanned;
        p.bannedUntil = in.bannedUntil;
        p.banReason = in.banReason;
        p.chatBanned = in.chatBanned;
        p.chatBanUntil = in.chatBanUntil;
        p.chatBanReason = in.chatBanReason;
        p.isJailed = in.isJailed;
        p.jailUntil = in.jailUntil;
        p.jailReason = in.jailReason;
        p.lastDailyReset = in.lastDailyReset;
        p.dailyInvasionFree = in.dailyInvasionFree;
        p.dailyInvasionPaid = in.dailyInvasionPaid;
        p.lastInvasionTime = in.lastInvasionTime;
        p.chestSlotsExtra = in.chestSlotsExtra;
        p.towerMilestoneClaimed = in.towerMilestoneClaimed;
        p.socialRewardClaimed = in.socialRewardClaimed;
        p.lastOnline = in.lastOnline;
        p.essence = in.essence;
        p.bossClaimed = in.bossClaimed;
        p.loginStreak = in.loginStreak;
        p.lastGiftTime = in.lastGiftTime;
        p.lastBuildingReward = in.lastBuildingReward;
        p.lastDiamondShopTime = in.lastDiamondShopTime;
        p.dailyWheelCount = in.dailyWheelCount;
        p.dailyMysteryCount = in.dailyMysteryCount;
        p.meditationTime = in.meditationTime;
        p.pvpHourStart = in.pvpHourStart;
        p.pvpMatchesUsed = in.pvpMatchesUsed;
        p.friendsDetailed = in.friendsDetailed;
        p.friends = in.friends;
        p.friendRequests = in.friendRequests;
        p.achStats = in.achStats;
        p.achClaimed = in.achClaimed;
        p.battleLog = in.battleLog;
        p.autoStart = in.autoStart;
        p.autoDuration = in.autoDuration;
        PlayerData.applySpecialPermissions(p);
        SaveSystem.updatePower();
        return true;
    }

    private static void showDevNotice(String message) {
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
            b.setTitle(GameApp.T("إشعار من المطورين", "Notice from the Developers"));
            b.setView(U.msg(message));
            b.setCancelable(false);
            b.setPositiveButton(GameApp.T("حسنا", "OK"), null);
            b.show();
        } catch (Exception ignored) {}
    }

    private static void showBanDialog(String reason) {
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
            b.setTitle(GameApp.T("أنت محظور!", "You Are Banned!"));
            b.setView(U.msg(GameApp.T("تم حظر حسابك.\nالسبب:", "Your account has been banned.\nReason:") + reason));
            b.setCancelable(false);
            b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> {
                if (MainActivity.inst != null) MainActivity.inst.finish();
            });
            b.show();
        } catch (Exception ignored) {}
    }

    private static void showJailDialog(String reason) {
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
            b.setTitle(GameApp.T("أنت مسجون!", "You Are Imprisoned!"));
            b.setView(U.msg(GameApp.T("لقد تم سجنك من قبل الإدارة.\nالسبب:", "You have been jailed by the administration.\nReason:") + reason));
            b.setCancelable(false);
            b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> {
                if (MainActivity.inst != null) MainActivity.inst.finish();
            });
            b.show();
        } catch (Exception ignored) {}
    }

    private static void showDeviceBanDialog(String reason) {
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
            b.setTitle(GameApp.T("تم حظر الـ IP الخاص بجهازك!", "Your Device IP Has Been Banned!"));
            b.setView(U.msg(GameApp.T("تم حظر الـ IP الخاص بجهازك من اللعب.\nالسبب:", "Your device's IP has been banned from playing.\nReason:") + reason + GameApp.T("\n\nسيتم إغلاق اللعبة تلقائيًا...", "\n\nThe game will close automatically...")));
            b.setCancelable(false);
            b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> {
                if (MainActivity.inst != null) MainActivity.inst.finish();
            });
            b.show();
            // Force immediate kick: close after a moment, no way to postpone.
            UI.postDelayed(() -> {
                if (MainActivity.inst != null) MainActivity.inst.finish();
            }, 2000);
        } catch (Exception ignored) {}
    }
}
