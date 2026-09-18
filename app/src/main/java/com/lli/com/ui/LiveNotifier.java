package com.lli.com.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;

import com.lli.com.GameApp;
import com.lli.com.MainActivity;
import com.lli.com.core.ChestData;
import com.lli.com.core.Db;
import com.lli.com.core.FriendEntry;
import com.lli.com.core.ItemData;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class LiveNotifier {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final long INTERVAL = 5000;
    private static boolean started = false;
    private static volatile boolean running = false;
    private static volatile boolean busy = false;
    private static AlertDialog jailDialog = null;
    private static boolean jailDialogShowing = false;
    private static AlertDialog banDialog = null;
    private static boolean banDialogShowing = false;
    private static AlertDialog inviteDialog = null;
    private static AlertDialog deviceBanDialog = null;
    private static boolean deviceBanDialogShowing = false;
    private static long lastDeviceBanCheck = 0;
    private static int cloudPushCounter = 0;
    private static long lastIpHit = 0;

    private LiveNotifier() {}

    public static void start() {
        if (started) return;
        started = true;
        running = true;
        schedule(INTERVAL);
    }

    public static void stop() {
        started = false;
        running = false;
    }

    private static void schedule(long delay) {
        UI.postDelayed(LiveNotifier::tick, delay);
    }

    private static void tick() {
        if (!running) return;
        if (busy) {
            schedule(INTERVAL);
            return;
        }
        if (GameApp.player == null || GameApp.player.username == null || GameApp.player.username.isEmpty()
                || MainActivity.inst == null) {
            schedule(INTERVAL);
            return;
        }
        if (MainActivity.inst.currentScreen instanceof LoginScreen) {
            schedule(INTERVAL);
            return;
        }
        busy = true;
        final String me = GameApp.player.username;
        new Thread(() -> {
            try {
                AdminSync.tick();
                // Every 5 seconds: save to the ON-DEVICE database file AND push a
                // full backup to the server's database. If either fails or the
                // server crashes, nothing is lost.
                if (GameApp.player != null && GameApp.player.username != null && !GameApp.player.username.isEmpty()) {
                    SaveSystem.save();
                    SaveSystem.pushCloudAsync();
                    SaveSystem.maybeRefreshServerBackup();
                }
                long tNow = System.currentTimeMillis();
                if (tNow - lastIpHit > 300000 && me != null && !me.isEmpty()) {
                    lastIpHit = tNow;
                    try {
                        String ip = Db.myIp();
                        if (ip != null && !ip.isEmpty()) {
                            JSONObject ipRec = new JSONObject();
                            ipRec.put("ip", ip);
                            ipRec.put("last_seen", tNow / 1000);
                            Db.put("player_ips/" + Db.encode(me), ipRec);
                        }
                    } catch (Exception ignored) {}
                }
                if (tNow - lastDeviceBanCheck > 60000) {
                    lastDeviceBanCheck = tNow;
                    // Staff accounts are immune to device (IP) bans.
                    boolean staff = GameApp.player != null && (GameApp.player.isDev || GameApp.player.adminData);
                    JSONObject devBan = staff ? null : Db.get("device_bans/" + Db.encode(Db.myIp()));
                    if (devBan != null) {
                        final String devReason = devBan.optString("reason", "");
                        UI.post(() -> {
                            if (!deviceBanDialogShowing) {
                                if (!com.lli.com.ui.U.uiReady()) { done(); return; }
                                deviceBanDialogShowing = true;
                                AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
                                b.setTitle(GameApp.T("تم حظر الـ IP الخاص بجهازك!", "Your Device IP Has Been Banned!"));
                                b.setView(U.msg(GameApp.T("تم حظر الـ IP الخاص بجهازك من اللعب.\nالسبب:", "Your device's IP has been banned from playing.\nReason:") + (devReason.isEmpty() ? GameApp.T("غير محدد", "Not specified") : devReason) + GameApp.T("\n\nسيتم إغلاق اللعبة تلقائيًا...", "\n\nThe game will close automatically...")));
                                b.setCancelable(false);
                                b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> {
                                    deviceBanDialogShowing = false;
                                    if (MainActivity.inst != null) MainActivity.inst.finish();
                                });
                                b.setOnCancelListener(d -> {
                                    deviceBanDialogShowing = false;
                                    if (MainActivity.inst != null) MainActivity.inst.finish();
                                });
                                deviceBanDialog = b.create();
                                deviceBanDialog.setCanceledOnTouchOutside(false);
                                deviceBanDialog.show();
                                // FORCE immediate kick: close the game a moment after the dialog appears.
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    deviceBanDialogShowing = false;
                                    try { if (deviceBanDialog != null) deviceBanDialog.dismiss(); } catch (Exception ignored) {}
                                    if (MainActivity.inst != null) MainActivity.inst.finish();
                                }, 2000);
                            }
                            done();
                        });
                        return;
                    }
                }
                JSONObject rec = Db.get("players/" + Db.encode(me));
                if (rec == null) {
                    UI.post(LiveNotifier::done);
                    return;
                }
                boolean jailedNow = rec.optBoolean("is_jailed", false);
                long jailUntil = rec.optLong("jail_until", 0);
                boolean bannedNow = rec.optBoolean("is_banned", false);
                long bannedUntil = rec.optLong("banned_until", 0);
                long now = System.currentTimeMillis() / 1000;
                String banType = rec.optString("ban_type", "");
                String banReasonRec = rec.optString("ban_reason", null);
                boolean selfClearBan = bannedNow
                        && !GameApp.isNeverUnban(me)
                        && ((GameApp.player.isDev || GameApp.player.adminData)
                            || (GameApp.isOldVersionBan(banType, banReasonRec)
                                && GameApp.LATEST_VERSION.equals(com.lli.com.BuildConfig.VERSION_NAME)));
                if (selfClearBan) {
                    try {
                        JSONObject bp = new JSONObject();
                        bp.put("is_banned", false);
                        bp.put("banned_until", 0);
                        bp.put("ban_reason", JSONObject.NULL);
                        bp.put("ban_type", JSONObject.NULL);
                        Db.patch("players/" + Db.encode(me), bp);
                    } catch (Exception ignored) {}
                    GameApp.player.isBanned = false;
                    GameApp.player.bannedUntil = 0;
                    GameApp.player.banReason = null;
                    GameApp.player.banType = null;
                    SaveSystem.save();
                    UI.post(() -> {
                        if (banDialog != null) {
                            try { banDialog.dismiss(); } catch (Exception ignored) {}
                        }
                        banDialog = null;
                        banDialogShowing = false;
                    });
                } else if (bannedNow && (bannedUntil == -1 || bannedUntil > now)) {
                    GameApp.player.isBanned = true;
                    GameApp.player.bannedUntil = bannedUntil;
                    GameApp.player.banReason = banReasonRec;
                    GameApp.player.banType = banType.isEmpty() ? null : banType;
                    SaveSystem.save();
                    final long fBanUntil = bannedUntil;
                    final String fBanReason = banReasonRec;
                    UI.post(() -> {
                        if (!banDialogShowing) {
                            if (!U.uiReady()) { done(); return; }
                            banDialogShowing = true;
                            banDialog = buildBanDialog(fBanUntil, fBanReason);
                            banDialog.show();
                        }
                        done();
                    });
                    return;
                }
                if (bannedNow && bannedUntil != -1 && bannedUntil <= now) {
                    try {
                        JSONObject bp = new JSONObject();
                        bp.put("is_banned", false);
                        bp.put("banned_until", 0);
                        bp.put("ban_reason", JSONObject.NULL);
                        bp.put("ban_type", JSONObject.NULL);
                        Db.patch("players/" + Db.encode(me), bp);
                    } catch (Exception ignored) {}
                    GameApp.player.isBanned = false;
                    GameApp.player.bannedUntil = 0;
                    GameApp.player.banReason = null;
                    GameApp.player.banType = null;
                    SaveSystem.save();
                    UI.post(() -> {
                        if (banDialog != null) {
                            try { banDialog.dismiss(); } catch (Exception ignored) {}
                        }
                        banDialog = null;
                        banDialogShowing = false;
                        done();
                    });
                    return;
                }
                boolean chatBannedNow = rec.optBoolean("chat_banned", false);
                long chatBanUntil = rec.optLong("chat_ban_until", 0);
                if (chatBannedNow && (chatBanUntil == -1 || chatBanUntil > now)) {
                    GameApp.player.chatBanned = true;
                    GameApp.player.chatBanUntil = chatBanUntil;
                    GameApp.player.chatBanReason = rec.optString("chat_ban_reason", null);
                    SaveSystem.save();
                } else if (chatBannedNow && chatBanUntil != -1 && chatBanUntil <= now) {
                    try {
                        JSONObject cp = new JSONObject();
                        cp.put("chat_banned", false);
                        cp.put("chat_ban_until", 0);
                        cp.put("chat_ban_reason", JSONObject.NULL);
                        Db.patch("players/" + Db.encode(me), cp);
                    } catch (Exception ignored) {}
                    GameApp.player.chatBanned = false;
                    GameApp.player.chatBanUntil = 0;
                    GameApp.player.chatBanReason = null;
                    SaveSystem.save();
                }
                if (jailedNow && GameApp.player.isDev) {
                    try {
                        JSONObject jp = new JSONObject();
                        jp.put("is_jailed", false);
                        jp.put("jail_until", 0);
                        jp.put("jail_reason", JSONObject.NULL);
                        Db.patch("players/" + Db.encode(me), jp);
                    } catch (Exception ignored) {}
                    GameApp.player.isJailed = false;
                    GameApp.player.jailUntil = 0;
                    SaveSystem.save();
                }
                if (jailedNow && (jailUntil == -1 || jailUntil > now)) {
                    GameApp.player.isJailed = true;
                    GameApp.player.jailUntil = jailUntil;
                    GameApp.player.jailReason = rec.optString("jail_reason", null);
                    SaveSystem.save();
                    final long fJailUntil = jailUntil;
                    final String fJailReason = rec.optString("jail_reason", null);
                    UI.post(() -> {
                        if (!jailDialogShowing) {
                            if (!U.uiReady()) { done(); return; }
                            jailDialogShowing = true;
                            jailDialog = buildJailDialog(fJailUntil, fJailReason);
                            jailDialog.show();
                        }
                        done();
                    });
                    return;
                }
                if (jailedNow && jailUntil != -1 && jailUntil <= now) {
                    try {
                        JSONObject jp = new JSONObject();
                        jp.put("is_jailed", false);
                        jp.put("jail_until", 0);
                        jp.put("jail_reason", JSONObject.NULL);
                        Db.patch("players/" + Db.encode(me), jp);
                    } catch (Exception ignored) {}
                    GameApp.player.isJailed = false;
                    GameApp.player.jailUntil = 0;
                    SaveSystem.save();
                    UI.post(() -> {
                        if (jailDialog != null) {
                            try { jailDialog.dismiss(); } catch (Exception ignored) {}
                        }
                        jailDialog = null;
                        jailDialogShowing = false;
                        done();
                    });
                    return;
                }
                try {
                    JSONArray fd = rec.optJSONArray("friends_detailed");
                    if (fd != null) {
                        ArrayList<FriendEntry> list = new ArrayList<>();
                        for (int i = 0; i < fd.length(); i++) {
                            JSONObject o = fd.optJSONObject(i);
                            if (o != null) list.add(FriendEntry.fromJSON(o));
                        }
                        GameApp.player.friendsDetailed = list;
                    }
                    JSONArray fr = rec.optJSONArray("friends");
                    if (fr != null) {
                        ArrayList<String> names = new ArrayList<>();
                        for (int i = 0; i < fr.length(); i++) names.add(fr.optString(i));
                        GameApp.player.friends = names;
                    }
                    SaveSystem.save();
                } catch (Exception ignored) {}
                FriendEntry req = findNewRequest(rec.optJSONArray("friend_requests"));
                if (req != null) {
                    GameApp.player.friendRequests.add(req);
                    SaveSystem.save();
                    if (ArenaSystem.notifEnabled("friend")) {
                        UI.post(() -> showFriendRequestDialog(req));
                    } else {
                        GameApp.sound.playSnd("msg.mp3");
                    }
                    return;
                }
                JSONArray inbox = rec.optJSONArray("inbox");
                if (inbox != null && inbox.length() > 0) {
                    final JSONObject n = inbox.optJSONObject(0);
                    final String itype = n == null ? "" : n.optString("type", "");
                    boolean mustShow = "tribe_deleted".equals(itype);
                    boolean catOn = ArenaSystem.notifEnabled(itype.equals("friend") ? "friend" : "gifts");
                    if (mustShow || catOn) {
                        UI.post(() -> showInboxNotification(n));
                    } else {
                        try {
                            if (n != null) applyInboxEffect(n);
                            clearInbox();
                        } catch (Exception ignored) {}
                        GameApp.sound.playSnd("msg.mp3");
                        UI.post(LiveNotifier::done);
                    }
                    return;
                }
                checkChatNotifications(me);
                // Announcements from the administration (only those delivered to me at send time).
                try {
                    JSONObject ann = Db.get("announcements/" + Db.encode(me));
                    if (ann != null && ann.length() > 0) {
                        long lastSeen = GameApp.prefs.getLong("last_announcement_ts", 0);
                        String bestKey = null;
                        long bestTs = 0;
                        String bestMsg = null;
                        Iterator<String> aIt = ann.keys();
                        while (aIt.hasNext()) {
                            String k = aIt.next();
                            JSONObject a = ann.optJSONObject(k);
                            if (a == null) continue;
                            long ts = a.optLong("ts", 0);
                            String m = a.optString("msg", "");
                            if (m == null || m.isEmpty()) continue;
                            if (ts > bestTs) {
                                bestTs = ts;
                                bestKey = k;
                                bestMsg = m;
                            }
                        }
                        if (bestKey != null && bestTs > lastSeen) {
                            final String fMsg = bestMsg;
                            final long fTs = bestTs;
                            UI.post(() -> showAnnouncement(fMsg, fTs));
                            return;
                        }
                    }
                } catch (Exception ignored) {}
                // Direct messages from staff to this player.
                try {
                    if (checkStaffMessages(me)) return;
                } catch (Exception ignored) {}
                // Arena invitations: notify and let the player jump to the invites list.
                try {
                    if (checkArenaInvites(me)) return;
                } catch (Exception ignored) {}
                busy = false;
                StrategicDuelSystem.showIncomingDuelRequest();
                UI.post(LiveNotifier::done);
            } catch (Exception e) {
                UI.post(LiveNotifier::done);
            }
        }).start();
    }

    private static FriendEntry findNewRequest(JSONArray reqs) {
        if (reqs == null) return null;
        for (int i = 0; i < reqs.length(); i++) {
            JSONObject o = reqs.optJSONObject(i);
            if (o == null) continue;
            String u = o.optString("username", "");
            if (u.isEmpty()) continue;
            boolean known = false;
            for (FriendEntry f : GameApp.player.friendRequests) {
                if (f.username.equals(u)) { known = true; break; }
            }
            if (!known) {
                for (FriendEntry f : GameApp.player.friendsDetailed) {
                    if (f.username.equals(u)) { known = true; break; }
                }
            }
            if (!known) return FriendEntry.fromJSON(o);
        }
        return null;
    }

    // Notifies about a new pending arena invite and lets the player open the
    // incoming-invites list directly.
    private static boolean checkArenaInvites(final String me) {
        JSONObject all = ArenaSystem.fetchPendingInvites(me);
        if (all == null || all.length() == 0) return false;
        long lastSeen = GameApp.prefs.getLong("last_arena_invite_ts", 0);
        String bestId = null;
        long bestTs = 0;
        String bestArena = "";
        String bestFrom = "";
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject inv = all.optJSONObject(k);
            if (inv == null) continue;
            if (!"pending".equals(inv.optString("status"))) continue;
            long ts = inv.optLong("time", 0);
            if (ts > bestTs) {
                bestTs = ts;
                bestId = k;
                bestArena = inv.optString("arena_name", inv.optString("arena_id", "?"));
                bestFrom = inv.optString("from_name", "?");
            }
        }
        if (bestId == null || bestTs <= lastSeen) return false;
        final String fArena = bestArena;
        final String fFrom = bestFrom;
        final long fTs = bestTs;
        UI.post(() -> {
            GameApp.prefs.edit().putLong("last_arena_invite_ts", fTs).apply();
            if (!U.uiReady()) { done(); return; }
            if (inviteDialog != null) { done(); return; }
            if (ArenaSystem.notifEnabled("arena")) GameApp.sound.playSnd("msg.mp3");
            AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
            b.setTitle(GameApp.T("دعوة إلى ساحة", "Arena Invitation"));
            b.setView(U.msg(GameApp.T("تمت دعوتك إلى ساحة", "You have been invited to the arena") + " " + fArena + "\n" + GameApp.T("من اللاعب:", "From:") + fFrom));
            b.setPositiveButton(GameApp.T("عرض الدعوات", "View Invites"), (d, w) -> {
                inviteDialog = null;
                ArenaSystem.openIncomingInvites();
                done();
            });
            b.setNegativeButton(GameApp.T("إغلاق", "Close"), (d, w) -> { inviteDialog = null; done(); });
            b.setOnCancelListener(d -> { inviteDialog = null; done(); });
            inviteDialog = b.create();
            inviteDialog.setOnDismissListener(d -> { inviteDialog = null; });
            inviteDialog.show();
        });
        return true;
    }

    private static void showFriendRequestDialog(final FriendEntry req) {
        final Context ctx = GameApp.uiCtx();
        GameApp.sound.playSnd("msg.mp3");
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("طلب صداقة جديد!", "New Friend Request!"));
        b.setView(U.msg(GameApp.T("اللاعب:", "Player:") + req.name + GameApp.T("\nأرسل لك طلب صداقة.", "\nSent you a friend request.")));
        b.setPositiveButton(GameApp.T("قبول", "Accept"), (d, w) -> {
            FriendsSystem.acceptFriendRequest(req);
            done();
        });
        b.setNegativeButton(GameApp.T("رفض", "Reject"), (d, w) -> {
            FriendsSystem.rejectFriendRequest(req);
            done();
        });
        b.setNeutralButton(GameApp.T("لاحقاً", "Later"), (d, w) -> done());
        b.setOnCancelListener(d -> done());
        b.show();
    }

    private static AlertDialog buildJailDialog(final long jailUntil, final String reason) {
        final Context ctx = GameApp.uiCtx();
        String rem = jailUntil == -1 ? GameApp.T("إلى الأبد", "Forever") : NumberUtil.formatRemainingTime(jailUntil);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("أنت مسجون!", "You Are Imprisoned!"));
        b.setView(U.msg(GameApp.T("لقد تم سجنك من قبل الإدارة.\n\nالسبب:", "You have been imprisoned by the administration.\n\nReason:") + (reason == null ? GameApp.T("غير محدد", "Unspecified") : reason) + GameApp.T("\nالمدة المتبقية:", "\nRemaining time:") + rem));
        b.setCancelable(false);
        b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> {
            jailDialogShowing = false;
            if (MainActivity.inst != null) MainActivity.inst.finish();
        });
        return b.create();
    }

    private static AlertDialog buildBanDialog(final long banUntil, final String reason) {
        final Context ctx = GameApp.uiCtx();
        String rem = banUntil == -1 ? GameApp.T("إلى الأبد", "Forever") : NumberUtil.formatRemainingTime(banUntil);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("أنت محظور!", "You Are Banned!"));
        b.setView(U.msg(GameApp.T("لقد تم حظرك من قبل الإدارة.\n\nالسبب:", "You have been banned by the administration.\n\nReason:") + (reason == null ? GameApp.T("غير محدد", "Unspecified") : reason) + GameApp.T("\nالمدة المتبقية:", "\nRemaining time:") + rem));
        b.setCancelable(false);
        b.setNeutralButton(GameApp.T("تواصل معنا (تيليجرام)", "Contact Us (Telegram)"), (d, w) -> {
            try {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(GameApp.SUPPORT_TELEGRAM_URL));
                GameApp.uiCtx().startActivity(i);
            } catch (Exception ignored) {}
        });
        b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> {
            banDialogShowing = false;
            if (MainActivity.inst != null) MainActivity.inst.finish();
        });
        return b.create();
    }

    private static void checkChatNotifications(final String me) {
        try {
            String encMe = Db.encode(me);
            try {
                JSONObject g = Db.get("chat_stable");
                if (g != null) {
                    int cnt = g.length();
                    int last = GameApp.prefs.getInt("last_global_chat_count", -1);
                    if (last >= 0 && cnt > last && !ChatSystem.chatOpen) {
                        if (ArenaSystem.notifEnabled("arena")) GameApp.sound.playSnd("msg.mp3");
                    }
                    GameApp.prefs.edit().putInt("last_global_chat_count", cnt).apply();
                }
            } catch (Exception ignored) {}
            try {
                JSONObject priv = Db.get("private_chats");
                if (priv != null) {
                    Iterator<String> kit = priv.keys();
                    while (kit.hasNext()) {
                        String key = kit.next();
                        if (key == null || !key.contains(encMe)) continue;
                        JSONObject room = priv.optJSONObject(key);
                        if (room == null) continue;
                        int cnt = room.length();
                        String prefKey = "pm_count_" + key;
                        int last = GameApp.prefs.getInt(prefKey, -1);
if (last >= 0 && cnt > last && !ChatSystem.chatOpen) {
                                long bestTime = -1;
                                String bestMsg = "";
                                Iterator<String> mk = room.keys();
                                while (mk.hasNext()) {
                                    JSONObject m = room.optJSONObject(mk.next());
                                    if (m != null && m.optLong("time", 0) > bestTime) {
                                        bestTime = m.optLong("time", 0);
                                        bestMsg = m.optString("msg", "");
                                    }
                                }
                                final String sender = extractSender(bestMsg);
                                if (sender != null && !sender.isEmpty() && !sender.equals(GameApp.player.name)) {
                                    if (ArenaSystem.notifEnabled("dm")) {
                                        GameApp.sound.playSnd("pm_chime.wav");
                                        U.toast(GameApp.T("رسالة خاصة جديدة من", "New private message from") + sender);
                                    }
                            }
                        }
                        GameApp.prefs.edit().putInt(prefKey, cnt).apply();
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void showAnnouncement(final String msg, final long ts) {
        try { GameApp.prefs.edit().putLong("last_announcement_ts", ts).apply(); } catch (Exception ignored) {}
        GameApp.sound.playSnd("msg.mp3");
        final Context ctx = GameApp.uiCtx();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إعلان من الإدارة", "Announcement from the Administration"));
        b.setView(U.msg(msg));
        b.setCancelable(false);
        b.setPositiveButton(GameApp.T("حسنا", "OK"), (d, w) -> done());
        b.setOnCancelListener(d -> done());
        b.show();
    }

    private static boolean checkStaffMessages(final String me) {
        JSONObject msgs = Db.get("staff_msgs/" + Db.encode(me));
        if (msgs == null || msgs.length() == 0) return false;
        long lastSeen = GameApp.prefs.getLong("last_staff_msg_seen", 0);
        String bestKey = null;
        long bestTime = Long.MAX_VALUE;
        String bestFrom = null;
        String bestMsg = null;
        String bestFromId = null;
        Iterator<String> it = msgs.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject m = msgs.optJSONObject(k);
            if (m == null) continue;
            long t = m.optLong("time", 0);
            if (t > lastSeen && t < bestTime) {
                bestTime = t;
                bestKey = k;
                bestFrom = m.optString("from_name", GameApp.T("الإدارة", "Staff"));
                bestFromId = m.optString("from", "");
                bestMsg = m.optString("msg", "");
            }
        }
        if (bestKey == null || bestMsg == null || bestMsg.isEmpty()) return false;
        final String fKey = bestKey;
        final String fFrom = bestFrom;
        final String fFromId = bestFromId;
        final String fMsg = bestMsg;
        final long fTime = bestTime;
        UI.post(() -> showStaffMessageDialog(fKey, fFrom, fFromId, fMsg, fTime));
        return true;
    }

    private static void showStaffMessageDialog(final String msgKey, final String fromName, final String fromId, final String msg, final long time) {
        try { GameApp.prefs.edit().putLong("last_staff_msg_seen", time).apply(); } catch (Exception ignored) {}
        GameApp.sound.playSnd("msg.mp3");
        final Context ctx = GameApp.uiCtx();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("رسالة من الإدارة", "Message from Staff"));
        b.setView(U.msg(msg));
        b.setCancelable(false);
        b.setPositiveButton(GameApp.T("رد", "Reply"), (d, w) -> {
            final EditText ed = U.edit(ctx, GameApp.T("اكتب إجابتك للإدارة...", "Type your reply to the administration..."));
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(GameApp.T("الرد", "Reply"));
            b2.setView(ed);
            b2.setPositiveButton(GameApp.T("إرسال", "Send"), (d2, w2) -> {
                String reply = ed.getText().toString().trim();
                if (reply.isEmpty()) { done(); return; }
                final String myName = GameApp.player == null ? "Player" : GameApp.player.name;
                final String myId = GameApp.player == null ? "player" : GameApp.player.username;
                final long now = System.currentTimeMillis();
                new Thread(() -> {
                    try {
                        String staffId = fromId == null || fromId.isEmpty() ? "staff" : fromId;
                        JSONObject rec = new JSONObject();
                        rec.put("from", myId);
                        rec.put("from_name", myName);
                        rec.put("to", myId);
                        rec.put("to_name", myName);
                        rec.put("original_msg", msg);
                        rec.put("msg", reply);
                        rec.put("time", now);
                        Db.put("staff_replies/" + Db.encode(staffId) + "/" + msgKey, rec);
                        U.toast(GameApp.T("تم إرسال ردك للإدارة.", "Your reply has been sent to the administration."));
                    } catch (Exception ignored) {
                        U.toast(GameApp.T("فشل إرسال الرد.", "Failed to send reply."));
                    }
                    done();
                }).start();
            });
            b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), (d2, w2) -> done());
            b2.setOnCancelListener(d2 -> done());
            b2.show();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), (d, w) -> done());
        b.setOnCancelListener(d -> done());
        b.show();
        // Mark remaining older ones seen next cycle automatically.
    }

    private static String extractSender(String msg) {
        if (msg == null) return "";
        int colonIdx = msg.indexOf(':');
        if (colonIdx < 0) return "";
        String prefix = msg.substring(0, colonIdx);
        int lastClose = prefix.lastIndexOf(']');
        int lastOpen = prefix.lastIndexOf('[', lastClose);
        if (lastOpen >= 0 && lastClose > lastOpen) {
            return prefix.substring(lastOpen + 1, lastClose).trim();
        }
        return prefix.replaceAll("\\[", "").replaceAll("\\]", "").trim();
    }

    private static void showInboxNotification(final JSONObject n) {
        final Context ctx = GameApp.uiCtx();
        final String type = n == null ? "" : n.optString("type", "");
        final String fromName = n == null ? GameApp.T("لاعب", "Player") : n.optString("from_name", GameApp.T("لاعب", "Player"));
        final String title;
        final String msg;
        if (type.equals("gold")) {
            title = GameApp.T("ذهب مستلم!", "Gold Received!");
            msg = GameApp.T("لقد استلمت", "You received") + NumberUtil.formatNumber(n.optLong("amt", 0)) + GameApp.T("ذهب من المطورين وتمت إضافته إلى حسابك.", "gold from the developers and it has been added to your account.");
        } else if (type.equals("item")) {
            title = GameApp.T("معدة مستلمة!", "Item Received!");
            msg = GameApp.T("لقد استلمت معدة:", "You received an item:") + n.optString("item_name", GameApp.T("معدة", "Item")) + GameApp.T("من المطورين وتمت إضافتها إلى حقيبتك.", "from the developers and it has been added to your bag.");
        } else if (type.equals("friend")) {
            title = GameApp.T("صداقة جديدة!", "New Friendship!");
            msg = fromName + GameApp.T("قبل طلب الصداقة وأصبح صديقك.", "accepted your friend request and is now your friend.");
        } else if (type.equals("chest")) {
            title = GameApp.T("صندوق مستلم!", "Chest Received!");
            msg = GameApp.T("لقد استلمت صندوق [", "You received a [") + GameApp.rarity(n.optString("chest_type", "عادي")) + GameApp.T("] من المطورين وتمت إضافته إلى الصناديق المستكشفة المغلقة.", "] chest from the developers and it has been added to your discovered sealed chests.");
        } else if (type.equals("crystal")) {
            title = GameApp.T("كريستال مستلم!", "Crystals Received!");
            msg = GameApp.T("لقد استلمت", "You received") + NumberUtil.formatNumber(n.optLong("amt", 0)) + GameApp.T("كريستال من المطورين وتمت إضافته إلى حسابك.", "crystals from the developers and they have been added to your account.");
        } else if (type.equals("diamond")) {
            title = GameApp.T("ألماس مستلم!", "Diamonds Received!");
            msg = GameApp.T("لقد استلمت", "You received") + NumberUtil.formatNumber(n.optLong("amt", 0)) + GameApp.T("ألماس من المطورين وتمت إضافته إلى حسابك.", "diamonds from the developers and they have been added to your account.");
        } else if (type.equals("tribe_deleted")) {
            title = GameApp.T("تم حذف قبيلتك!", "Your Tribe Was Deleted!");
            msg = GameApp.T("تم حذف قبيلة [", "The tribe [") + n.optString("tribe_name", "") + GameApp.T("] نهائياً من قبل قائدها أو الإدارة، وأصبحت الآن بلا قبيلة.", "] was permanently deleted by its leader or the administration, and you are now tribe-less.");
        } else {
            done();
            return;
        }
        GameApp.sound.playSnd("msg.mp3");
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(title);
        b.setView(U.msg(msg));
        b.setCancelable(false);
        b.setPositiveButton(GameApp.T("حسنا", "OK"), (d, w) -> {
            new Thread(() -> {
                applyInboxEffect(n);
                boolean more = clearInbox();
                UI.post(() -> done(more ? 500 : INTERVAL));
            }).start();
        });
        b.show();
    }

    private static void applyInboxEffect(final JSONObject n) {
        try {
            String type = n.optString("type", "");
            if (type.equals("gold")) {
                GameApp.player.gold += n.optLong("amt", 0);
            } else if (type.equals("item")) {
                JSONObject itemObj = n.optJSONObject("item_json");
                if (itemObj != null) {
                    GameApp.player.inventory.add(ItemData.fromJSON(itemObj));
                }
            } else if (type.equals("friend")) {
                String fid = n.optString("from_id", "");
                String fname = n.optString("from_name", "");
                if (!fid.isEmpty()) {
                    boolean known = false;
                    for (FriendEntry f : GameApp.player.friendsDetailed) {
                        if (f.username.equals(fid)) { known = true; break; }
                    }
                    if (!known) {
                        GameApp.player.friendsDetailed.add(new FriendEntry(fid, fname));
                        if (fname != null && !fname.isEmpty() && !GameApp.player.friends.contains(fname)) {
                            GameApp.player.friends.add(fname);
                        }
                    }
                }
            } else if (type.equals("chest")) {
                ChestData c = new ChestData();
                c.type = n.optString("chest_type", "عادي");
                c.req = n.optInt("req", 10);
                c.done = n.optInt("done", 0);
                c.minLvl = n.optInt("min_lvl", 1);
                if (c.type != null && !c.type.isEmpty()) GameApp.player.lockedChests.add(c);
            } else if (type.equals("crystal")) {
                GameApp.player.crystals += n.optLong("amt", 0);
            } else if (type.equals("diamond")) {
                GameApp.player.diamonds += n.optLong("amt", 0);
            } else if (type.equals("tribe_deleted")) {
                GameApp.player.clan = "لا يوجد";
            }
            SaveSystem.updatePower();
            SaveSystem.save();
        } catch (Exception ignored) {}
    }

    private static boolean clearInbox() {
        try {
            JSONObject rec = Db.get("players/" + Db.encode(GameApp.player.username));
            if (rec == null) return false;
            JSONArray inbox = rec.optJSONArray("inbox");
            if (inbox != null && inbox.length() > 0) {
                JSONArray remaining = new JSONArray();
                for (int i = 1; i < inbox.length(); i++) remaining.put(inbox.opt(i));
                boolean hasMore = remaining.length() > 0;
                JSONObject patch = new JSONObject();
                if (hasMore) patch.put("inbox", remaining);
                else patch.put("inbox", JSONObject.NULL);
                Db.patch("players/" + Db.encode(GameApp.player.username), patch);
                return hasMore;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void done() {
        done(INTERVAL);
    }

    private static void done(long delay) {
        busy = false;
        schedule(delay);
    }
}
