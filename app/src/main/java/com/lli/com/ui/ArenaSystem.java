package com.lli.com.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.ItemData;
import com.lli.com.core.ItemGenerator;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import com.lli.com.core.VoiceChatController;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dueling Arenas (ساحات المبارزة الأونلاين):
 * - Create public/private/survival arenas (10 gold per player slot, unused slots refunded).
 * - Direct host invites from the online players list.
 * - Roles: Host / Player (capped by slots) / Spectator (unlimited, switch anytime).
 * - Disconnect persistence: rooms stay open; temporary host promotion + reclaim ownership.
 * - Survival mode (King of the Hill): level brackets down to a single champion.
 * - Champion gains 50% creation fee + entry bets and an Arena Medal (profile + leaderboard).
 * - In-room text chat + gift/inbox engine + granular notification toggles.
 * - Voice controls: self mute, per-user local mute, host voice ban (state layer — real audio
 *   streaming requires a separate media server, out of scope of this HTTP/JSON backend).
 */
public class ArenaSystem {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String ARENAS = "arenas/";
    private static final String INVITES = "arena_invites/";
    private static final String CHAT = "arena_chat/";
    private static final String GIFTS = "inbox/";
private static final String MEDALS   = "medals/";
    private static final String VOICE    = "arena_voice/";
    private static final String USER = "players/";

    // Live voice feel: short clips + fast poll cadence.
    private static final int VOICE_RECORD_MS = 850;
    private static final int VOICE_POLL_MS = 600;

    // Stealth voice monitoring (staff): invisible listener that never joins the room.
    private static AlertDialog voiceMonDialog;
    private static Handler voiceMonPoller;
    private static long voiceMonLastSeen = 0;
    private static String voiceMonArenaId;
    private static LinearLayout voiceMonSpeakerBox;
    private static TextView voiceMonInfo;
    private static final Map<String, Long> voiceMonSpeakers = new LinkedHashMap<>();
    private static boolean voiceMonActive;

    private static final String[] NOTIF_KEYS = {"arena", "dm", "gifts", "friend"};
    private static final int SLOT_FEE = 10;

    private static AlertDialog roomDialog;
    private static String roomArenaId;
    private static LinearLayout memberBox;
    private static LinearLayout chatBox;
    private static TextView roomInfo;
    private static Handler roomPoller;
    private static Handler chatPoller;
    private static int lastChatCount = 0;

    private static final Map<String, Boolean> localMutes = new HashMap<>();
    private static long medalCacheTime = 0;
    private static long medalCacheCount = 0;
    private static Button reclaimBtn, inviteBtn, startSurvBtn, muteAllBtn;
    private static Button voiceTalkBtn;
    private static volatile boolean arenaBattleStarting = false;
    private static boolean talkingVoice = false;
    private static MediaRecorder voiceRecorder;
    private static MediaPlayer voicePlayer;
    private static final java.util.List<byte[]> voicePlayQueue = new java.util.ArrayList<>();
    private static Handler voicePoller;
    private static long lastVoiceSeen = 0;

    // ------------------------------------------------------------------i18n
    private static String[] notifLabels() {
        return new String[]{
                GameApp.T("إعلانات الساحات", "Arena Announcements"),
                GameApp.T("الرسائل المباشرة", "Direct Messages"),
                GameApp.T("الهدايا والموارد", "Gifts & Resources"),
                GameApp.T("طلبات الأصدقاء", "Friend Requests")
        };
    }

    public static boolean notifEnabled(String cat) {
        if (GameApp.player == null) return true;
        Boolean v = GameApp.player.notifPrefs.get(cat);
        return v == null || v;
    }

    public static void notifyToast(String cat, String title, String msg) {
        if (!notifEnabled(cat)) return;
        FinalToast.show(GameApp.uiCtx(), (title == null ? "" : title + "\n") + msg);
        GameApp.sound.playSnd("msg.mp3");
    }

    /** Tiny non-intrusive top banner toast with chime (safe: no BadToken, no activity dependency). */
    private static class FinalToast {
        static void show(final Context ctx, final String msg) {
            U.toast(msg);
        }
    }

    // ------------------------------------------------------- medals helpers
    public static long myMedals() {
        return medalCacheCount;
    }

    /** Refresh the cached arena medal count from the server (non-blocking UI call). */
    public static void refreshMedalCache() {
        if (GameApp.player == null || GameApp.player.username.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - medalCacheTime < 30000) return;
        medalCacheTime = now;
        new Thread(() -> {
            try {
                JSONObject rec = Db.get(MEDALS + Db.encode(GameApp.player.username));
                long c = rec == null ? 0 : rec.optLong("count", 0);
                if (c != medalCacheCount) {
                    medalCacheCount = c;
                    UI.post(GameNav::updateInfo);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    // ------------------------------------------------------------- main menu
    public static void openArenasMenu() {
        Context ctx = GameApp.uiCtx();
        String[] opts = {
                GameApp.T("إنشاء ساحة", "Create Arena"),
                GameApp.T("الساحات العامة المفتوحة", "Open Public Arenas"),
                GameApp.T("الدعوات الواردة", "Incoming Invites"),
                GameApp.T("صندوق الهدايا", "Gift Inbox"),
                GameApp.T("الانضمام بكود ساحة", "Join by Arena Code")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("ساحات المبارزة", "Dueling Arenas"));
        b.setItems(opts, (d, i) -> {
            if (i == 0) createArenaMenu();
            else if (i == 1) listPublicArenas();
            else if (i == 2) showInvites();
            else if (i == 3) openInbox();
            else joinByCode();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    // ------------------------------------------------------ create arena
    private static void createArenaMenu() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        final EditText edName = U.edit(ctx, GameApp.T("اسم الساحة...", "Arena name..."));
        lay.addView(edName);
        final String[] modes = {GameApp.T("عامة", "Public"), GameApp.T("خاصة", "Private"), GameApp.T("نجاة (ملك التل)", "Survival (King of the Hill)")};
        final int[] selMode = {0};
        final int[] selSlots = {2};
        final EditText edBet = U.edit(ctx, GameApp.T("رهان الدخول (ذهب) - للنجاة فقط", "Entry bet (gold) - Survival only"));
        lay.addView(edBet);
        Button bMode = U.btn(ctx, modes[0]);
        bMode.setOnClickListener(v -> {
            AlertDialog.Builder sb = new AlertDialog.Builder(ctx);
            sb.setTitle(GameApp.T("نوع الساحة", "Arena Type"));
            sb.setSingleChoiceItems(modes, selMode[0], (d2, w2) -> {
                selMode[0] = w2;
                bMode.setText(modes[w2]);
                d2.dismiss();
            });
            sb.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            sb.show();
        });
        lay.addView(bMode);
        Button bSlots = U.btn(ctx, "2");
        bSlots.setOnClickListener(v -> {
            String[] sc = {"2", "3", "4", "5", "6"};
            String[] scEn = {"2", "3", "4", "5", "6"};
            AlertDialog.Builder sb = new AlertDialog.Builder(ctx);
            sb.setTitle(GameApp.T("عدد مقاعد اللاعبين", "Player Slots"));
            sb.setSingleChoiceItems(sc, selSlots[0] - 2, (d2, w2) -> {
                selSlots[0] = w2 + 2;
                String[] arLbl = {"اثنان", "ثلاثة", "أربعة", "خمسة", "ستة"};
                String[] enLbl = {"Two", "Three", "Four", "Five", "Six"};
                bSlots.setText(GameApp.T(arLbl[w2], enLbl[w2]));
                d2.dismiss();
            });
            sb.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            sb.show();
        });
        lay.addView(bSlots);
        Button createBtn = U.btn(ctx, GameApp.T("إنشاء (يكلف ", "Create (costs ") + (SLOT_FEE * selSlots[0]) + GameApp.T(" ذهب)", " gold)"));
        createBtn.setOnClickListener(v -> {
            String name = edName.getText().toString().trim();
            if (name.isEmpty()) name = GameApp.T("ساحة بلا اسم", "Nameless Arena") + "-" + NumberUtil.rand(1000, 9999);
            long cost = (long) SLOT_FEE * selSlots[0];
            long bet = 0;
            if (selMode[0] == 2) {
                try { bet = Long.parseLong(edBet.getText().toString().trim()); } catch (Exception ignored) {}
                if (bet < 10) bet = 10;
            }
            if (p.gold < cost + bet) {
                U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً!", "You don't have enough gold!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            createArena(name, selMode[0], selSlots[0], bet, cost);
        });
        createBtn.setText(GameApp.T("إنشاء (يكلف ", "Create (costs ") + (SLOT_FEE * selSlots[0]) + GameApp.T(" ذهب)", " gold)"));
        lay.addView(createBtn);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إنشاء ساحة مبارزة", "Create a Dueling Arena"));
        b.setView(U.scroll(ctx, lay));
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void createArena(final String name, final int mode, final int slots, final long bet, final long cost) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("إنشاء", "Creating"), GameApp.T("جاري إنشاء الساحة...", "Creating the arena..."), true);
        new Thread(() -> {
            try {
                String id = "" + System.currentTimeMillis() + "_" + NumberUtil.rand(100, 999);
                JSONObject arena = new JSONObject();
                arena.put("id", id);
                arena.put("name", name);
                arena.put("mode", mode == 1 ? "private" : (mode == 2 ? "survival" : "public"));
                arena.put("slots", slots);
                arena.put("entry_gold", bet);
                arena.put("pot", bet);
                arena.put("host", p.username);
                arena.put("host_original", p.username);
                arena.put("temp_host", false);
                arena.put("status", "open");
                arena.put("created_at", System.currentTimeMillis());
                arena.put("champion", "");
                arena.put("reward", 0);
                JSONObject members = new JSONObject();
                JSONObject me = new JSONObject();
                me.put("name", p.name);
                me.put("role", "host");
                me.put("last_seen", System.currentTimeMillis() / 1000);
                me.put("muted", false);
                me.put("voice_banned", false);
                members.put(Db.encode(p.username), me);
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(id), arena);
                p.gold -= cost + bet;
                SaveSystem.saveAndRefresh();
                UI.post(() -> {
                    progress.dismiss();
                    enterRoom(id);
                });
            } catch (Exception e) {
                UI.post(() -> {
                    progress.dismiss();
                    U.alert(ctx, null, GameApp.T("فشل إنشاء الساحة!", "Failed to create the arena!"), GameApp.T("حسنا", "OK"), null);
                });
            }
        }).start();
    }

    // ------------------------------------------------------- list / join
    private static void listPublicArenas() {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري جلب الساحات العامة...", "Fetching public arenas..."), true);
        new Thread(() -> {
            final JSONObject all = Db.get(ARENAS.substring(0, ARENAS.length() - 1));
            UI.post(() -> {
                progress.dismiss();
                final List<String> ids = new ArrayList<>();
                final List<String> labels = new ArrayList<>();
                if (all != null) {
                    Iterator<String> it = all.keys();
                    while (it.hasNext()) {
                        String id = it.next();
                        JSONObject a = all.optJSONObject(id);
                        if (a == null) continue;
                        if (!"open".equals(a.optString("status"))) continue;
                        if (!"public".equals(a.optString("mode"))) continue;
                        int players = countPlayers(a.optJSONObject("members"));
                        int slots = a.optInt("slots", 2);
                        String hostKey = a.optString("host", "-");
                        String hostDisplayName = hostKey;
                        JSONObject hostMem = a.optJSONObject("members");
                        if (hostMem != null) {
                            JSONObject hm = hostMem.optJSONObject(Db.encode(hostKey));
                            if (hm != null) hostDisplayName = hm.optString("name", hostKey);
                        }
                        String lbl = a.optString("name", id) + " | " + GameApp.T("المضيف:", "Host:") + hostDisplayName + " | " + players + "/" + slots;
                        ids.add(id);
                        labels.add(lbl);
                    }
                }
                if (labels.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا توجد ساحات عامة مفتوحة حالياً.", "No open public arenas right now."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("الساحات العامة", "Public Arenas"));
                b.setItems(labels.toArray(new String[0]), (d, idx) -> joinArena(ids.get(idx), "player"));
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
            });
        }).start();
    }

    private static void joinByCode() {
        final Context ctx = GameApp.uiCtx();
        final EditText ed = U.edit(ctx, GameApp.T("اكتب كود الساحة...", "Arena code (ID)..."));
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(30, 30, 30, 30);
        lay.addView(ed);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الانضمام بكود", "Join by Code"));
        b.setView(lay);
        b.setPositiveButton(GameApp.T("دخول", "Enter"), (d, w) -> joinArena(ed.getText().toString().trim(), "player"));
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static int countPlayers(JSONObject members) {
        if (members == null) return 0;
        int c = 0;
        Iterator<String> it = members.keys();
        while (it.hasNext()) {
            JSONObject m = members.optJSONObject(it.next());
            if (m == null) continue;
            String r = m.optString("role", "spectator");
            if ("player".equals(r) || "host".equals(r)) c++;
        }
        return c;
    }

    public static void joinArena(final String arenaId, final String desiredRole) {
        if (arenaId == null || arenaId.isEmpty()) return;
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("انضمام", "Joining"), GameApp.T("جاري الدخول إلى الساحة...", "Entering the arena..."), true);
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null || !"open".equals(arena.optString("status"))) {
                    UI.post(() -> {
                        progress.dismiss();
                        U.alert(ctx, null, GameApp.T("الساحة غير متاحة أو انتهت.", "The arena is unavailable or finished."), GameApp.T("حسنا", "OK"), null);
                    });
                    return;
                }
                JSONObject members = arena.optJSONObject("members");
                if (members == null) members = new JSONObject();
                String meKey = Db.encode(p.username);
                if (members.has(meKey)) {
                    UI.post(() -> {
                        progress.dismiss();
                        enterRoom(arenaId);
                    });
                    return;
                }
                String mode = arena.optString("mode", "public");
                if ("private".equals(mode) && !arena.optString("host_original").equals(p.username)) {
                    boolean allowed = false;
                    JSONObject invites = Db.get(INVITES + Db.encode(p.username));
                    if (invites != null) {
                        Iterator<String> it = invites.keys();
                        while (it.hasNext()) {
                            JSONObject inv = invites.optJSONObject(it.next());
                            if (inv != null && arenaId.equals(inv.optString("arena_id"))) { allowed = true; break; }
                        }
                    }
                    if (!allowed) {
                        UI.post(() -> {
                            progress.dismiss();
                            U.toast(GameApp.T("هذه ساحة خاصة — تحتاج دعوة.", "This is a private arena - you need an invite."));
                        });
                        return;
                    }
                }
                int players = countPlayers(members);
                int slots = arena.optInt("slots", 2);
                String role = "player".equals(desiredRole) && players < slots ? "player" : "spectator";
                long bet = arena.optLong("entry_gold", 0);
                long pot = arena.optLong("pot", 0);
                if ("survival".equals(mode) && "player".equals(role)) {
                    if (p.gold < bet) {
                        UI.post(() -> {
                            progress.dismiss();
                            U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً لرهان الدخول!", "You don't have enough gold for the entry bet!"), GameApp.T("حسنا", "OK"), null);
                        });
                        return;
                    }
                    p.gold -= bet;
                    pot += bet;
                    arena.put("pot", pot);
                    SaveSystem.saveAndRefresh();
                }
                JSONObject me = new JSONObject();
                me.put("name", p.name);
                me.put("role", role);
                me.put("last_seen", System.currentTimeMillis() / 1000);
                me.put("muted", false);
                me.put("voice_banned", false);
                members.put(meKey, me);
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                UI.post(() -> {
                    progress.dismiss();
                    enterRoom(arenaId);
                });
            } catch (Exception e) {
                UI.post(() -> {
                    progress.dismiss();
                    U.alert(ctx, null, GameApp.T("فشل الانضمام!", "Failed to join!"), GameApp.T("حسنا", "OK"), null);
                });
            }
        }).start();
    }

    // ------------------------------------------------------------- room UI
    public static void enterRoom(final String arenaId) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        stopRoomPollers();
        roomArenaId = arenaId;
        lastChatCount = 0;
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        roomInfo = U.text(ctx, "", 14, U.GOLD, true);
        lay.addView(roomInfo);
        TextView mv = U.text(ctx, GameApp.T("الأعضاء:", "Members:"), 14, U.WHITE, true);
        mv.setPadding(0, 10, 0, 0);
        lay.addView(mv);
        memberBox = U.linear(ctx, true);
        memberBox.setPadding(10, 10, 10, 10);
        ScrollView mSc = U.scroll(ctx, memberBox);
        lay.addView(mSc);
        TextView cv = U.text(ctx, GameApp.T("الدردشة:", "Chat:"), 14, U.WHITE, true);
        cv.setPadding(0, 10, 0, 0);
        lay.addView(cv);
        chatBox = U.linear(ctx, true);
        chatBox.setPadding(10, 10, 10, 10);
        ScrollView cSc = U.scroll(ctx, chatBox);
        lay.addView(cSc);
        final EditText edMsg = U.edit(ctx, GameApp.T("اكتب رسالة...", "Type a message..."));
        lay.addView(edMsg);
        Button sendBtn = U.btn(ctx, GameApp.T("إرسال", "Send"), v -> {
            String m = edMsg.getText().toString().trim();
            if (m.isEmpty()) return;
            sendChat(arenaId, m);
            edMsg.setText("");
        });
        lay.addView(sendBtn);
        Button muteBtn = U.btn(ctx, GameApp.T("كتم صوتي / فتح", "Mute / Unmute Mic"));
        muteBtn.setOnClickListener(v -> toggleSelfMute(arenaId));
        lay.addView(muteBtn);
        Button specBtn = U.btn(ctx, GameApp.T("التحول إلى مشاهد", "Become a Spectator"));
        specBtn.setOnClickListener(v -> switchSpectator(arenaId));
        lay.addView(specBtn);
        reclaimBtn = U.btn(ctx, GameApp.T("استعادة ملكية الساحة", "Reclaim Arena Ownership"));
        reclaimBtn.setOnClickListener(v -> reclaimOwnership(arenaId));
        reclaimBtn.setVisibility(View.GONE);
        lay.addView(reclaimBtn);
        inviteBtn = U.btn(ctx, GameApp.T("دعوة لاعب أونلاين", "Invite an Online Player"));
        inviteBtn.setOnClickListener(v -> inviteFromOnline(arenaId));
        inviteBtn.setVisibility(View.GONE);
        lay.addView(inviteBtn);
        startSurvBtn = U.btn(ctx, GameApp.T("بدء المعركة", "Start Battle"));
        startSurvBtn.setOnClickListener(v -> startSurvival(arenaId));
        startSurvBtn.setVisibility(View.GONE);
        lay.addView(startSurvBtn);
        muteAllBtn = U.btn(ctx, GameApp.T("كتم صوت الجميع / فتح", "Mute All Voices / Unmute"));
        muteAllBtn.setOnClickListener(v -> toggleMuteAll(arenaId));
        muteAllBtn.setVisibility(View.GONE);
        lay.addView(muteAllBtn);
        Button leaveBtn = U.btn(ctx, GameApp.T("مغادرة الساحة", "Leave Arena"));
        leaveBtn.setOnClickListener(v -> leaveRoom(arenaId));
        lay.addView(leaveBtn);
        voiceTalkBtn = U.btn(ctx, GameApp.T("تحدث مباشرة (ميكروفون)", "Talk Live (Mic)"));
        voiceTalkBtn.setOnClickListener(v -> toggleVoiceTalk(arenaId));
        lay.addView(voiceTalkBtn);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("ساحة المبارزة", "Dueling Arena"));
        b.setView(U.scroll(ctx, lay));
        b.setCancelable(false);
        roomDialog = b.create();
        roomDialog.show();
        roomPoller = new Handler(Looper.getMainLooper());
        chatPoller = new Handler(Looper.getMainLooper());
        lastVoiceSeen = System.currentTimeMillis();
        roomPoll(arenaId);
        chatPoll(arenaId);
        roomPoller.postDelayed(roomPollRunnable(arenaId), 5000);
        chatPoller.postDelayed(chatPollRunnable(arenaId), 3000);
    }

    private static Runnable roomPollRunnable(final String arenaId) {
        return () -> {
            if (roomDialog != null && roomDialog.isShowing()) {
                roomPoll(arenaId);
                roomPoller.postDelayed(roomPollRunnable(arenaId), 5000);
            }
        };
    }

    private static Runnable chatPollRunnable(final String arenaId) {
        return () -> {
            if (roomDialog != null && roomDialog.isShowing()) {
                chatPoll(arenaId);
                chatPoller.postDelayed(chatPollRunnable(arenaId), 3000);
            }
        };
    }

    private static void stopRoomPollers() {
        VoiceChatController vc = VoiceChatController.instance();
        try { vc.stop(); } catch (Exception ignored) {}
        if (roomPoller != null) { roomPoller.removeCallbacksAndMessages(null); roomPoller = null; }
        if (chatPoller != null) { chatPoller.removeCallbacksAndMessages(null); chatPoller = null; }
        if (voicePoller != null) { voicePoller.removeCallbacksAndMessages(null); voicePoller = null; }
        talkingVoice = false;
        try {
            if (voiceRecorder != null) {
                try { voiceRecorder.stop(); } catch (Exception ignored) {}
                try { voiceRecorder.release(); } catch (Exception ignored) {}
                voiceRecorder = null;
            }
        } catch (Exception ignored) {}
        stopVoicePlayback();
        if (voicePlayer != null) {
            try { voicePlayer.release(); } catch (Exception ignored) {}
            voicePlayer = null;
        }
        if (roomDialog != null) {
            try { roomDialog.dismiss(); } catch (Exception ignored) {}
            roomDialog = null;
        }
        roomArenaId = null;
        roomInfo = null;
        memberBox = null;
        chatBox = null;
    }

    private static void roomPoll(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                long now = System.currentTimeMillis() / 1000;
                String meKey = Db.encode(GameApp.player.username);
                // heartbeat
                JSONObject me = members.optJSONObject(meKey);
                if (me != null) {
                    long seen = now - me.optLong("last_seen", 0);
                    if (seen < 60 || seen > 90) {
                        me.put("last_seen", now);
                        try { Db.put(ARENAS + Db.encode(arenaId), arena); } catch (Exception ignored) {}
                    }
                }
                // temp host promotion
                String host = arena.optString("host", "");
                if (!arena.optBoolean("temp_host", false)) {
                    JSONObject hm = members.optJSONObject(Db.encode(host));
                    if (hm == null || now - hm.optLong("last_seen", 0) > 90) {
                        String newHost = null;
                        Iterator<String> it = members.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            JSONObject m = members.optJSONObject(k);
                            if (m != null && ("player".equals(m.optString("role")) || "host".equals(m.optString("role")))) {
                                newHost = k; // already encoded key
                                break;
                            }
                        }
                        if (newHost != null) {
                            arena.put("host", newHost);
                            arena.put("temp_host", true);
                            try { Db.put(ARENAS + Db.encode(arenaId), arena); } catch (Exception ignored) {}
                        }
                    }
                }
                final JSONObject fArena = arena;
                final boolean isHostHere = host.equals(GameApp.player.username) || host.equals(Db.encode(GameApp.player.username));
                UI.post(() -> {
                    if (roomInfo == null) return;
                    if (reclaimBtn != null) {
                        reclaimBtn.setVisibility(isHostHere ? View.VISIBLE : View.GONE);
                        reclaimBtn.setEnabled(isHostHere);
                    }
                    if (inviteBtn != null) {
                        inviteBtn.setVisibility(isHostHere ? View.VISIBLE : View.GONE);
                        inviteBtn.setEnabled(isHostHere);
                    }
                    if (startSurvBtn != null) {
                        startSurvBtn.setVisibility(isHostHere ? View.VISIBLE : View.GONE);
                        startSurvBtn.setEnabled(isHostHere);
                    }
                    if (muteAllBtn != null) {
                        muteAllBtn.setVisibility(isHostHere ? View.VISIBLE : View.GONE);
                        muteAllBtn.setEnabled(isHostHere);
                    }
                    final JSONObject fBattle = fArena.optJSONObject("battle");
                    if (fBattle != null && !fBattle.optBoolean("over", false) && !arenaBattleStarting
                            && !StrategicDuelSystem.isPvpActive()) {
                        String fId = fBattle.optString("from_id", "");
                        String tId = fBattle.optString("to_id", "");
                        final String myUsername = GameApp.player.username;
                        if (myUsername.equals(fId) || myUsername.equals(tId)) {
                            arenaBattleStarting = true;
                            final boolean iAmInitiator = myUsername.equals(fId);
                            final String oppId = iAmInitiator ? tId : fId;
                            final String oppName = fBattle.optString(iAmInitiator ? "to_name" : "from_name", GameApp.T("خصم", "Opponent"));
                            new Thread(() -> {
                                final JSONObject oppData = Db.get(USER + Db.encode(oppId));
                                UI.post(() -> {
                                    arenaBattleStarting = false;
                                    if (oppData != null) {
                                        StrategicDuelSystem.startArenaBattle(arenaId, oppId, oppName, iAmInitiator, oppData);
                                    }
                                });
                            }).start();
                        }
                    } else if (fBattle != null && fBattle.optBoolean("over", false)) {
                        arenaBattleStarting = false;
                    }
                    String status = fArena.optString("status", "open");
                    if (!"open".equals(status)) {
                        roomInfo.setText(GameApp.T("الساحة:", "Arena:") + fArena.optString("name", arenaId)
                                + "\n" + GameApp.T("البطل:", "Champion:") + fArena.optString("champion", "-")
                                + "\n" + GameApp.T("الجائزة:", "Reward:") + fArena.optLong("reward", 0) + GameApp.T(" ذهب", " gold")
                                + "\n" + GameApp.T("انتهت الساحة", "Arena finished"));
                        stopRoomPollers();
                        return;
                    }
                    String hostKey = fArena.optString("host", "-");
                    String hostName = hostKey;
                    JSONObject m2 = fArena.optJSONObject("members");
                    if (m2 != null) {
                        JSONObject hm = m2.optJSONObject(hostKey);
                        if (hm == null) hm = m2.optJSONObject(Db.encode(hostKey));
                        if (hm != null) hostName = hm.optString("name", hostKey);
                    }
                    roomInfo.setText(GameApp.T("الساحة:", "Arena:") + fArena.optString("name", arenaId)
                            + GameApp.T("\nالوضع:", "\nMode:") + modeLabel(fArena.optString("mode", "public"))
                            + GameApp.T(" | المضيف:", " | Host:") + hostName + (fArena.optBoolean("temp_host", false) ? GameApp.T(" (مؤقت)", " (temp)") : "")
                            + "\n" + GameApp.T("الأعضاء:", "Members:") + countPlayers(fArena.optJSONObject("members")) + "/" + fArena.optInt("slots", 2)
                            + (fArena.optLong("pot", 0) > 0 ? ("\n" + GameApp.T("الجائزة:", "Prize:") + NumberUtil.formatNumber(fArena.optLong("pot", 0)) + GameApp.T(" ذهب", " gold")) : ""));
                    rebuildMembers(fArena.optJSONObject("members"), arenaId);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static String modeLabel(String mode) {
        if ("survival".equals(mode)) return GameApp.T("نجاة", "Survival");
        if ("private".equals(mode)) return GameApp.T("خاصة", "Private");
        return GameApp.T("عامة", "Public");
    }

    private static void rebuildMembers(final JSONObject members, final String arenaId) {
        if (memberBox == null) return;
        memberBox.removeAllViews();
        List<String> keys = new ArrayList<>();
        Iterator<String> it = members.keys();
        while (it.hasNext()) keys.add(it.next());
        for (final String k : keys) {
            final JSONObject m = members.optJSONObject(k);
            if (m == null) continue;
            String role = m.optString("role", "spectator");
            String roleTxt;
            if ("host".equals(role)) roleTxt = GameApp.T("[مضيف]", "[Host]");
            else if ("player".equals(role)) roleTxt = GameApp.T("[لاعب]", "[Player]");
            else roleTxt = GameApp.T("[مشاهد]", "[Spectator]");
            String vTxt = "";
            if (m.optBoolean("voice_banned", false)) vTxt += " " + GameApp.T("[محظور صوتياً]", "[Voice Banned]");
            if (m.optBoolean("muted", false)) vTxt += " " + GameApp.T("[صامت]", "[Muted]");
            if (localMutes.getOrDefault(arenaId + "|" + k, false)) vTxt += " " + GameApp.T("[مكتم عندك]", "[Muted by you]");
            final String label = m.optString("name", k) + " " + roleTxt + vTxt;
            Button row = U.btn(memberBox.getContext(), label);
            row.setOnClickListener(v -> memberOptions(k, m, arenaId));
            memberBox.addView(row);
        }
    }

    private static void memberOptions(final String memberKey, final JSONObject member, final String arenaId) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final boolean isMe = memberKey.equals(Db.encode(GameApp.player.username));
        final boolean isHost = roomIdIsHost(arenaId);
        final List<String> opts = new ArrayList<>();
        if (isHost && !isMe) {
            boolean locallyMuted = localMutes.getOrDefault(arenaId + "|" + memberKey, false);
            opts.add(locallyMuted ? GameApp.T("فتح الصوت (لهذا اللاعب)", "Unmute (this player)") : GameApp.T("كتم الصوت (لهذا اللاعب)", "Mute (this player)"));
            if (member.optBoolean("voice_banned", false)) opts.add(GameApp.T("رفع حظر الصوت", "Remove Voice Ban"));
            else opts.add(GameApp.T("حظر صوتي (للكل)", "Voice Ban (for all)"));
        } else if (isMe) {
            opts.add(GameApp.T("كتم صوتي / فتح", "Mute / Unmute Mic"));
        }
        opts.add(GameApp.T("إرسال هدية", "Send Gift"));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(member.optString("name", memberKey));
        b.setItems(opts.toArray(new String[0]), (d, i) -> {
            if (isHost && !isMe) {
                if (i == 0) {
                    boolean nowMuted = !localMutes.getOrDefault(arenaId + "|" + memberKey, false);
                    localMutes.put(arenaId + "|" + memberKey, nowMuted);
                    try { VoiceChatController.instance().setPeerListenEnabledByKey(memberKey, !nowMuted); } catch (Throwable ignored) {}
                    SharedPreferences prefs = GameApp.prefs;
                    if (prefs != null) prefs.edit().putBoolean("vmute_" + arenaId + "_" + memberKey, nowMuted).apply();
                    GameApp.sound.playSnd("click.mp3");
                    roomPoll(arenaId);
                } else if (i == 1) {
                    voiceBanToggle(arenaId, memberKey);
                } else {
                    sendGiftMenu(memberKey, member.optString("name", memberKey));
                }
            } else if (isMe) {
                if (i == 0) {
                    toggleSelfMute(arenaId);
                } else {
                    sendGiftMenu(memberKey, member.optString("name", memberKey));
                }
            } else {
                sendGiftMenu(memberKey, member.optString("name", memberKey));
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static boolean roomIdIsHost(String arenaId) {
        if (GameApp.player == null) return false;
        try {
            JSONObject a = Db.get(ARENAS + Db.encode(arenaId));
            if (a == null) return false;
            String host = a.optString("host", "");
            return host.equals(GameApp.player.username) || host.equals(Db.encode(GameApp.player.username));
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------- actions
    private static void toggleSelfMute(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                JSONObject me = members.optJSONObject(Db.encode(GameApp.player.username));
                if (me == null) return;
                me.put("muted", !me.optBoolean("muted", false));
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                UI.post(() -> U.toast(GameApp.T("تم تحديث حالة الميكروفون.", "Microphone state updated.")));
            } catch (Exception ignored) {}
        }).start();
    }

    private static void voiceBanToggle(final String arenaId, final String memberKey) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                JSONObject m = members.optJSONObject(memberKey);
                if (m == null) return;
                boolean willBan = !m.optBoolean("voice_banned", false);
                m.put("voice_banned", willBan);
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                try { VoiceChatController.instance().setPeerListenEnabledByKey(memberKey, !willBan); } catch (Throwable ignored) {}
                UI.post(() -> U.toast(GameApp.T("تم تحديث حظر الصوت.", "Voice ban updated.")));
            } catch (Exception ignored) {}
        }).start();
    }

    private static void switchSpectator(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                JSONObject me = members.optJSONObject(Db.encode(GameApp.player.username));
                if (me == null) return;
                me.put("role", "spectator");
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                UI.post(() -> U.toast(GameApp.T("أصبحت مشاهداً.", "You are now a spectator.")));
            } catch (Exception ignored) {}
        }).start();
    }

    private static void reclaimOwnership(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                if (!GameApp.player.username.equals(arena.optString("host_original", ""))) {
                    UI.post(() -> U.toast(GameApp.T("لست المضيف الأصلي.", "You are not the original host.")));
                    return;
                }
                arena.put("host", GameApp.player.username);
                arena.put("temp_host", false);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                UI.post(() -> U.toast(GameApp.T("تمت استعادة ملكية الساحة!", "Arena ownership reclaimed!")));
            } catch (Exception ignored) {}
        }).start();
    }

    private static void leaveRoom(final String arenaId) {
        stopRoomPollers();
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) members = new JSONObject();
                members.remove(Db.encode(GameApp.player.username));
                if (members.length() == 0) {
                    Db.delete(ARENAS + Db.encode(arenaId));
                    Db.delete(CHAT + Db.encode(arenaId));
                } else {
                    arena.put("members", members);
                    String host = arena.optString("host", "");
                    if (GameApp.player.username.equals(host)) {
                        // promote another player
                        Iterator<String> it = members.keys();
                        String nh = null;
                        while (it.hasNext()) {
                            String k = it.next();
                            JSONObject m = members.optJSONObject(k);
                            if (m != null && ("player".equals(m.optString("role")) || "host".equals(m.optString("role")))) { nh = k; break; }
                        }
                        if (nh == null) {
                            Db.delete(ARENAS + Db.encode(arenaId));
                        } else {
                            arena.put("host", nh);
                            arena.put("temp_host", true);
                            Db.put(ARENAS + Db.encode(arenaId), arena);
                        }
                    } else {
                        Db.put(ARENAS + Db.encode(arenaId), arena);
                    }
                }
            } catch (Exception ignored) {}
        }).start();
        UI.post(() -> U.toast(GameApp.T("غادرت الساحة.", "You left the arena.")));
        openArenasMenu();
    }

    private static void closeArena(final String arenaId) {
        stopRoomPollers();
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                int players = countPlayers(members);
                int slots = arena.optInt("slots", 2);
                long refund = (long) SLOT_FEE * Math.max(0, slots - players);
                // refund survival bets to remaining players via inbox
                long bet = arena.optLong("entry_gold", 0);
                if (bet > 0 && members != null && "open".equals(arena.optString("status"))) {
                    Iterator<String> it = members.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject m = members.optJSONObject(k);
                        if (m != null && !"spectator".equals(m.optString("role"))) {
                            addGiftRecord(k, "", GameApp.T("استرداد رهان الساحة", "Arena bet refund"), "gold", bet, "");
                        }
                    }
                }
                Db.delete(ARENAS + Db.encode(arenaId));
                Db.delete(CHAT + Db.encode(arenaId));
                final long fRefund = refund;
                UI.post(() -> {
                    GameApp.player.gold += fRefund;
                    SaveSystem.saveAndRefresh();
                    U.alert(GameApp.uiCtx(), null, GameApp.T("تم إغلاق الساحة واسترداد ", "Arena closed, refunded ") + fRefund + GameApp.T(" ذهب للمقاعد غير المستخدمة.", " gold for unused slots."), GameApp.T("حسنا", "OK"), null);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static void sendChat(final String arenaId, final String msg) {
        new Thread(() -> {
            try {
                String seq = "" + System.currentTimeMillis() + "_" + NumberUtil.rand(10, 99);
                JSONObject entry = new JSONObject();
                entry.put("u", GameApp.player.username);
                entry.put("n", GameApp.player.name);
                entry.put("t", System.currentTimeMillis());
                entry.put("m", msg);
                Db.put(CHAT + Db.encode(arenaId) + "/" + Db.encode(seq), entry);
            } catch (Exception ignored) {}
        }).start();
    }

    private static void chatPoll(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject all = Db.get(CHAT + Db.encode(arenaId));
                final List<String> lines = new ArrayList<>();
                if (all != null) {
                    List<String> seqs = new ArrayList<>();
                    Iterator<String> it = all.keys();
                    while (it.hasNext()) seqs.add(it.next());
                    seqs.sort(String::compareTo);
                    for (String s : seqs) {
                        JSONObject e = all.optJSONObject(s);
                        if (e != null) lines.add(e.optString("n", "?") + ": " + e.optString("m", ""));
                    }
                }
                UI.post(() -> {
                    if (chatBox == null) return;
                    int prev = lastChatCount;
                    if (lines.size() == prev) return;
                    lastChatCount = lines.size();
                    // Play an arena notification sound when OTHER players send chat
                    // while we are inside the room (not on the very first load).
                    if (prev > 0 && prev < lines.size() && !GameApp.isMuted) {
                        boolean anyFromOther = false;
                        for (int i = prev; i < lines.size(); i++) {
                            String l = lines.get(i);
                            if (l != null && !l.startsWith(GameApp.player.name + ":")) {
                                anyFromOther = true;
                                break;
                            }
                        }
                        if (anyFromOther && notifEnabled("arena")) {
                            try {
                                GameApp.sound.playSnd("notify" + (NumberUtil.rand(1, 3)) + ".mp3");
                            } catch (Exception ignored) {
                                GameApp.sound.playSnd("notify1.mp3");
                            }
                        }
                    }
                    chatBox.removeAllViews();
                    for (String l : lines) {
                        TextView t = U.label(chatBox.getContext(), l, 13, U.LIGHT_BLUE);
                        t.setGravity(android.view.Gravity.START);
                        chatBox.addView(t);
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // ------------------------------------------------------- voice  (P2P WebRTC)
    private static void toggleVoiceTalk(final String arenaId) {
        final Context ctx = GameApp.uiCtx();
        VoiceChatController vc = VoiceChatController.instance();

        // If already talking, stop broadcasting (keep mesh alive so we still hear others).
        if (vc.isTalking()) {
            vc.toggleTalk();
            if (voiceTalkBtn != null) voiceTalkBtn.setText(GameApp.T("تحدث مباشرة (ميكروفون)", "Talk Live (Mic)"));
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            U.alert(ctx, null, GameApp.T("تحتاج منح إذن الميكروفون أولاً.", "You need to grant the microphone permission first."), GameApp.T("حسنا", "OK"), null);
            return;
        }
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                JSONObject members = arena == null ? null : arena.optJSONObject("members");
                JSONObject me = members == null ? null : members.optJSONObject(Db.encode(GameApp.player.username));
                if (me == null) {
                    UI.post(() -> U.toast(GameApp.T("لا يمكن الوصول لمعلوماتك في الساحة.", "Cannot access your arena info.")));
                    return;
                }
                if (me.optBoolean("voice_banned", false)) {
                    UI.post(() -> U.toast(GameApp.T("تم حظرك صوتياً من المضيف!", "The host voice-banned you!")));
                    return;
                }
                if (me.optBoolean("muted", false)) {
                    UI.post(() -> U.toast(GameApp.T("أنت مكتوم، أزل الكتم أولاً.", "You are muted, unmute first.")));
                    return;
                }
                UI.post(() -> {
                    vc.start(arenaId);
                    vc.toggleTalk();
                    if (voiceTalkBtn != null) voiceTalkBtn.setText(GameApp.T("إيقاف التحدث", "Stop Talking"));
                });
            } catch (Exception e) {
                UI.post(() -> U.toast(GameApp.T("فشل بدء الميكروفون.", "Failed to start the mic.")));
            }
        }).start();
    }

    private static void stopVoiceRecording() {
        talkingVoice = false;
        try {
            if (voiceRecorder != null) {
                try { voiceRecorder.stop(); } catch (Exception ignored) {}
                try { voiceRecorder.release(); } catch (Exception ignored) {}
                voiceRecorder = null;
            }
        } catch (Exception ignored) {}
        if (voiceTalkBtn != null) voiceTalkBtn.setText(GameApp.T("تحدث مباشرة (ميكروفون)", "Talk Live (Mic)"));
    }

    private static void voiceRecordLoop(final String arenaId) {
        if (!talkingVoice) { stopVoiceRecording(); return; }
        voiceStartRecorder(arenaId);
    }

    // Double-buffered recording: the next clip starts instantly after the previous
    // one stops (no dead air during the upload), keeping the stream continuous.
    private static void voiceStartRecorder(final String arenaId) {
        if (!talkingVoice) { stopVoiceRecording(); return; }
        final File f = new File(GameApp.ctx.getCacheDir(), "voice_" + System.currentTimeMillis() + ".m4a");
        final MediaRecorder rec = new MediaRecorder();
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            rec.setAudioEncodingBitRate(32000);
            rec.setAudioSamplingRate(16000);
            rec.setOutputFile(f.getAbsolutePath());
            rec.prepare();
            rec.start();
        } catch (Exception e) {
            try { rec.release(); } catch (Exception ignored) {}
            UI.post(() -> {
                talkingVoice = false;
                if (voiceTalkBtn != null) voiceTalkBtn.setText(GameApp.T("تحدث مباشرة (ميكروفون)", "Talk Live (Mic)"));
                U.toast(GameApp.T("فشل الميكروفون.", "Mic failed."));
            });
            return;
        }
        voiceRecorder = rec;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!talkingVoice) {
                try {
                    if (voiceRecorder == rec) {
                        try { rec.stop(); } catch (Exception ignored) {}
                        try { rec.release(); } catch (Exception ignored) {}
                        voiceRecorder = null;
                    }
                } catch (Exception ignored) {}
                try { f.delete(); } catch (Exception ignored) {}
                return;
            }
            final File finished = f;
            try {
                if (voiceRecorder == rec) {
                    try { rec.stop(); } catch (Exception ignored) {}
                    try { rec.release(); } catch (Exception ignored) {}
                    voiceRecorder = null;
                }
            } catch (Exception ignored) {}
            voiceStartRecorder(arenaId);
            new Thread(() -> uploadVoiceChunk(finished, arenaId)).start();
        }, VOICE_RECORD_MS);
    }

    private static void uploadVoiceChunk(final File f, final String arenaId) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            byte[] buf = new byte[16384];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            fis.close();
            byte[] data = bos.toByteArray();
            if (data.length < 200) return;
            String b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            JSONObject vo = new JSONObject();
            vo.put("u", GameApp.player.username);
            vo.put("t", System.currentTimeMillis());
            vo.put("c", b64);
            Db.put(VOICE + Db.encode(arenaId) + "/" + System.currentTimeMillis() + "_" + NumberUtil.rand(10, 99), vo);
        } catch (Exception ignored) {}
        try { f.delete(); } catch (Exception ignored) {}
    }

    private static Runnable voicePollRunnable(final String arenaId) {
        return () -> {
            if (roomDialog != null && roomDialog.isShowing()) {
                voicePoll(arenaId);
voicePoller.postDelayed(voicePollRunnable(arenaId), VOICE_POLL_MS);
            }
        };
    }

    private static void voicePoll(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject node = Db.get(VOICE + Db.encode(arenaId));
                if (node == null || node.length() == 0) return;
                long now = System.currentTimeMillis();
                final List<String> staleKeys = new ArrayList<>();
                final List<JSONObject> chunks = new ArrayList<>();
                Iterator<String> it = node.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject vo = node.optJSONObject(k);
                    if (vo == null) continue;
                    long t = vo.optLong("t", 0);
                    if (now - t > 60000) { staleKeys.add(k); continue; }
                    if (t <= lastVoiceSeen) continue;
                    if (now - t > 30000) continue;
                    chunks.add(vo);
                }
                if (!chunks.isEmpty()) {
                    chunks.sort((a, b) -> Long.compare(a.optLong("t", 0), b.optLong("t", 0)));
                    final List<String> audio = new ArrayList<>();
                    for (JSONObject vo : chunks) {
                        long t = vo.optLong("t", 0);
                        if (t > lastVoiceSeen) lastVoiceSeen = t;
                        String u = vo.optString("u", "");
                        if (u.isEmpty() || u.equals(GameApp.player.username)) continue;
                        if (localMutes.getOrDefault(arenaId + "|" + Db.encode(u), false)) continue;
                        String c = vo.optString("c", "");
                        if (!c.isEmpty()) audio.add(c);
                    }
                    for (String b64 : audio) {
                        byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        if (data != null && data.length >= 200) UI.post(() -> playVoiceChunk(data));
                    }
                }
                if (!staleKeys.isEmpty()) {
                    new Thread(() -> {
                        int n = 0;
                        for (String k : staleKeys) {
                            if (++n > 8) break;
                            try { Db.delete(VOICE + Db.encode(arenaId) + "/" + k); } catch (Exception ignored) {}
                        }
                    }).start();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    // Queue-based playback: clips play back-to-back and a new arriving clip NEVER
    // cuts the one that is already playing. If the network stalls, momentary
    // silence appears but no clipped words are lost.
    private static void playVoiceChunk(byte[] data) {
        synchronized (voicePlayQueue) {
            while (voicePlayQueue.size() >= 5) voicePlayQueue.remove(0);
            voicePlayQueue.add(data);
        }
        pumpVoice();
    }

    private static synchronized void pumpVoice() {
        byte[] data;
        synchronized (voicePlayQueue) {
            if (voicePlayQueue.isEmpty()) return;
            data = voicePlayQueue.remove(0);
        }
        playOneVoice(data);
    }

    private static void playOneVoice(byte[] data) {
        try {
            final File vf = new File(GameApp.ctx.getCacheDir(), "play_" + System.currentTimeMillis() + ".m4a");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(vf);
            fos.write(data);
            fos.flush();
            fos.close();
            final MediaPlayer mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(vf.getAbsolutePath());
            mp.setOnCompletionListener(p -> {
                try { p.release(); } catch (Exception ignored) {}
                if (voicePlayer == p) voicePlayer = null;
                pumpVoice();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                try { p.release(); } catch (Exception ignored) {}
                if (voicePlayer == p) voicePlayer = null;
                pumpVoice();
                return true;
            });
            mp.prepare();
            mp.start();
            voicePlayer = mp;
        } catch (Exception ignored) {
            pumpVoice();
        }
    }

    private static void stopVoicePlayback() {
        synchronized (voicePlayQueue) { voicePlayQueue.clear(); }
        if (voicePlayer != null) {
            try { voicePlayer.stop(); } catch (Exception ignored) {}
            try { voicePlayer.release(); } catch (Exception ignored) {}
            voicePlayer = null;
        }
    }

    // ------------------------------------------- staff voice admin (stealth)
    // Staff can listen to a room WITHOUT appearing as a member and apply the
    // same moderation as in-text-chat (mute / voice ban / mute-all / punishments).
    public static void openVoiceMonitor(final String arenaId) {
        if (GameApp.player == null) return;
        final Context ctx = GameApp.uiCtx();
        closeVoiceMonitor();
        voiceMonArenaId = arenaId;
        voiceMonLastSeen = System.currentTimeMillis();
        voiceMonSpeakers.clear();
        voiceMonActive = true;
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        voiceMonInfo = U.text(ctx, "", 14, U.GOLD, true);
        lay.addView(voiceMonInfo);
        TextView sv = U.text(ctx, GameApp.T("المتحدثون (اضغط للتحكم):", "Speakers (tap to control):"), 14, U.WHITE, true);
        sv.setPadding(0, 12, 0, 0);
        lay.addView(sv);
        voiceMonSpeakerBox = U.linear(ctx, true);
        ScrollView sc = U.scroll(ctx, voiceMonSpeakerBox);
        lay.addView(sc);
        Button mAll = U.btn(ctx, GameApp.T("كتم صوت الجميع (حظر صوتي)", "Mute Everyone (Voice Ban)"), v -> adminMuteAll(arenaId, true));
        lay.addView(mAll);
        Button uAll = U.btn(ctx, GameApp.T("فك كتم الجميع", "Unmute Everyone"), v -> adminMuteAll(arenaId, false));
        lay.addView(uAll);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("مراقبة الصوت - غير مرئي", "Voice Monitor - Invisible"));
        b.setView(U.scroll(ctx, lay));
        b.setCancelable(false);
        b.setNegativeButton(GameApp.T("إيقاف المراقبة", "Stop Monitoring"), (d, w) -> closeVoiceMonitor());
        if (!U.uiReady()) return;
        voiceMonDialog = b.create();
        voiceMonDialog.show();
        voiceMonPoller = new Handler(Looper.getMainLooper());
        voiceMonPoll(arenaId);
        voiceMonPoller.postDelayed(voiceMonPollRunnable(arenaId), VOICE_POLL_MS);
    }

    public static void closeVoiceMonitor() {
        voiceMonActive = false;
        voiceMonArenaId = null;
        voiceMonSpeakers.clear();
        if (voiceMonPoller != null) { voiceMonPoller.removeCallbacksAndMessages(null); voiceMonPoller = null; }
        if (voiceMonDialog != null) {
            try { voiceMonDialog.dismiss(); } catch (Exception ignored) {}
            voiceMonDialog = null;
        }
        if (voicePlayer != null) {
            try { voicePlayer.release(); } catch (Exception ignored) {}
            voicePlayer = null;
        }
    }

    private static Runnable voiceMonPollRunnable(final String arenaId) {
        return () -> {
            if (voiceMonActive) {
                voiceMonPoll(arenaId);
                if (voiceMonPoller != null) voiceMonPoller.postDelayed(voiceMonPollRunnable(arenaId), VOICE_POLL_MS);
            }
        };
    }

    private static void voiceMonPoll(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject node = Db.get(VOICE + Db.encode(arenaId));
                if (node == null || node.length() == 0) return;
                long now = System.currentTimeMillis();
                final List<JSONObject> chunks = new ArrayList<>();
                final Map<String, Long> speakers = new LinkedHashMap<>();
                Iterator<String> it = node.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject vo = node.optJSONObject(k);
                    if (vo == null) continue;
                    long t = vo.optLong("t", 0);
                    if (now - t > 30000) continue;
                    if (t <= voiceMonLastSeen) continue;
                    chunks.add(vo);
                }
                if (!chunks.isEmpty()) {
                    chunks.sort((a, b) -> Long.compare(a.optLong("t", 0), b.optLong("t", 0)));
                    final List<String> audio = new ArrayList<>();
                    for (JSONObject vo : chunks) {
                        long t = vo.optLong("t", 0);
                        if (t > voiceMonLastSeen) voiceMonLastSeen = t;
                        String u = vo.optString("u", "");
                        if (!u.isEmpty()) speakers.put(u, t);
                        String c = vo.optString("c", "");
                        if (!c.isEmpty()) audio.add(c);
                    }
                    for (String b64 : audio) {
                        byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        if (data != null && data.length >= 200) UI.post(() -> playVoiceChunk(data));
                    }
                }
                final Map<String, Long> fSpeakers = speakers;
                UI.post(() -> {
                    if (!voiceMonActive || voiceMonSpeakerBox == null) return;
                    for (Map.Entry<String, Long> e : fSpeakers.entrySet()) {
                        Long old = voiceMonSpeakers.get(e.getKey());
                        if (old == null || e.getValue() - old > 5000) voiceMonSpeakers.put(e.getKey(), e.getValue());
                    }
                    rebuildVoiceMonSpeakers();
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static void rebuildVoiceMonSpeakers() {
        if (voiceMonSpeakerBox == null) return;
        String arenaId = voiceMonArenaId;
        if (arenaId == null) return;
        long now = System.currentTimeMillis();
        long lastSeen = 0;
        for (Map.Entry<String, Long> e : voiceMonSpeakers.entrySet()) {
            long t = e.getValue();
            if (t > lastSeen) lastSeen = t;
        }
        final List<String[]> rows = new ArrayList<>();
        Iterator<Map.Entry<String, Long>> it = voiceMonSpeakers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() > 60000) { it.remove(); continue; }
            rows.add(new String[]{e.getKey(), e.getValue() + ""});
        }
        String info = GameApp.T("أنت لا تظهر في الساحة أثناء المراقبة.\n", "You do not appear in the arena while monitoring.\n")
                + GameApp.T("آخر صوت:", "Last audio:") + (now - lastSeen) + GameApp.T(" مللي ثانية", " ms");
        if (voiceMonInfo != null) voiceMonInfo.setText(info);
        voiceMonSpeakerBox.removeAllViews();
        for (final String[] r : rows) {
            final String u = r[0];
            Button rowBtn = U.btn(voiceMonSpeakerBox.getContext(), u);
            rowBtn.setOnClickListener(v -> voiceMonMemberActions(arenaId, u));
            voiceMonSpeakerBox.addView(rowBtn);
        }
    }

    private static void voiceMonMemberActions(final String arenaId, final String userName) {
        final Context ctx = GameApp.uiCtx();
        final JSONObject[] st = {null};
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                JSONObject members = arena == null ? null : arena.optJSONObject("members");
                JSONObject m = members == null ? null : members.optJSONObject(Db.encode(userName));
                if (m == null && members != null) m = members.optJSONObject(userName);
                st[0] = m;
            } catch (Exception ignored) {}
            UI.post(() -> {
                if (!U.uiReady()) return;
                final boolean isBanned = st[0] != null && st[0].optBoolean("voice_banned", false);
                final boolean isMuted = st[0] != null && st[0].optBoolean("muted", false);
                final List<String> opts = new ArrayList<>();
                opts.add(isBanned ? GameApp.T("رفع الحظر الصوتي", "Remove Voice Ban") : GameApp.T("حظر صوتي (كتم)", "Voice Ban (Mute)"));
                opts.add(isMuted ? GameApp.T("فتح صوت المستخدم", "Unmute User") : GameApp.T("كتم صوت المستخدم", "Mute User"));
                opts.add(GameApp.T("عقوبات مثل الدردشة النصية", "Same Punishments as Text Chat"));
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("التحكم في:", "Control:") + userName);
                b.setItems(opts.toArray(new String[0]), (d, i) -> {
                    if (i == 0) adminVoiceBan(arenaId, userName, !isBanned);
                    else if (i == 1) adminMuteUser(arenaId, userName, !isMuted);
                    else if (i == 2) com.lli.com.ui.AdminSystem.openPunishmentByDisplayName(userName);
                });
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                if (U.uiReady()) b.show();
            });
        }).start();
    }

    // ----- shared staff member-state write (never creates a member for staff) -----
    public static void adminVoiceBan(final String arenaId, final String userName, final boolean banned) {
        setMemberFlag(arenaId, userName, "voice_banned", banned,
                banned ? GameApp.T("تم الحظر الصوتي للمستخدم.", "User voice-banned.") : GameApp.T("تم رفع الحظر الصوتي.", "Voice ban removed."));
    }

    public static void adminMuteUser(final String arenaId, final String userName, final boolean muted) {
        setMemberFlag(arenaId, userName, "muted", muted,
                muted ? GameApp.T("تم كتم صوت المستخدم.", "User muted.") : GameApp.T("تم فتح صوت المستخدم.", "User unmuted."));
    }

    public static void adminMuteAll(final String arenaId, final boolean muted) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                int n = 0;
                Iterator<String> it = members.keys();
                while (it.hasNext()) {
                    JSONObject m = members.optJSONObject(it.next());
                    if (m != null) {
                        m.put("voice_banned", muted);
                        m.put("muted", muted);
                        n++;
                    }
                }
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                final int fc = n;
                UI.post(() -> U.toast(muted ? GameApp.T("تم كتم الجميع (" + fc + ").", "Everyone muted (" + fc + ").") : GameApp.T("تم فتح صوت الجميع.", "Everyone unmuted.")));
            } catch (Exception ignored) {}
        }).start();
    }

    private static void setMemberFlag(final String arenaId, final String userName, final String flag, final boolean val, final String toastMsg) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                JSONObject m = members.optJSONObject(Db.encode(userName));
                if (m == null) m = members.optJSONObject(userName);
                if (m == null) return;
                if ("muted".equals(flag)) {
                    m.put("muted", val);
                    m.put("voice_banned", val);
                } else {
                    m.put("voice_banned", val);
                }
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                UI.post(() -> U.toast(toastMsg == null ? "" : toastMsg));
            } catch (Exception ignored) {}
        }).start();
    }

    // ------------------------------------------------------- invites
    private static void inviteFromOnline(final String arenaId) {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("جاري البحث", "Searching"), GameApp.T("جاري إحضار اللاعبين المتصلين...", "Fetching online players..."), true);
        new Thread(() -> {
            JSONObject allPlayers = Db.get(USER.substring(0, USER.length() - 1));
            long curTime = System.currentTimeMillis() / 1000;
            final List<String[]> online = new ArrayList<>();
            if (allPlayers != null) {
                Iterator<String> keys = allPlayers.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    JSONObject data = allPlayers.optJSONObject(id);
                    if (data == null) continue;
                    if (id.equals(GameApp.player.username)) continue;
                    long lastOnline = data.optLong("last_online", 0);
                    if (lastOnline > 0 && (curTime - lastOnline < 180)) {
                        online.add(new String[]{id, data.optString("name", id)});
                    }
                }
            }
            UI.post(() -> {
                progress.dismiss();
                if (online.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبون متصلون حالياً.", "No players are currently online."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                List<String> names = new ArrayList<>();
                for (String[] p : online) names.add(p[1]);
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("دعوة لاعب إلى الساحة", "Invite a Player to the Arena"));
                b.setItems(names.toArray(new String[0]), (d, idx) -> sendInvite(arenaId, online.get(idx)[0], online.get(idx)[1]));
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    private static void sendInvite(final String arenaId, final String toId, final String toName) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                String invId = "" + System.currentTimeMillis();
                JSONObject inv = new JSONObject();
                inv.put("from_id", GameApp.player.username);
                inv.put("from_name", GameApp.player.name);
                inv.put("arena_id", arenaId);
                inv.put("arena_name", arena.optString("name", arenaId));
                inv.put("time", System.currentTimeMillis());
                inv.put("status", "pending");
                Db.put(INVITES + Db.encode(toId) + "/" + Db.encode(invId), inv);
                UI.post(() -> U.toast(GameApp.T("تم إرسال دعوة إلى ", "Invite sent to ") + toName));
            } catch (Exception ignored) {}
        }).start();
    }

    // Public bridge for LiveNotifier to fetch and open incoming invites.
    public static JSONObject fetchPendingInvites(String username) {
        try {
            return Db.get(INVITES + Db.encode(username));
        } catch (Exception e) {
            return null;
        }
    }

    public static void openIncomingInvites() {
        showInvites();
    }

    private static void showInvites() {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري جلب الدعوات...", "Fetching invites..."), true);
        new Thread(() -> {
            final JSONObject all = Db.get(INVITES + Db.encode(GameApp.player.username));
            UI.post(() -> {
                progress.dismiss();
                final List<String> ids = new ArrayList<>();
                final List<String> labels = new ArrayList<>();
                if (all != null) {
                    Iterator<String> it = all.keys();
                    while (it.hasNext()) {
                        String invId = it.next();
                        JSONObject inv = all.optJSONObject(invId);
                        if (inv == null) continue;
                        if (!"pending".equals(inv.optString("status"))) continue;
                        labels.add(inv.optString("from_name", "?") + " -> " + inv.optString("arena_name", inv.optString("arena_id", "?")));
                        ids.add(invId);
                    }
                }
                if (labels.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا توجد دعوات حالياً.", "No invites right now."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("الدعوات الواردة", "Incoming Invites"));
                b.setItems(labels.toArray(new String[0]), (d, idx) -> {
                    String invId = ids.get(idx);
                    new Thread(() -> {
                        JSONObject inv = Db.get(INVITES + Db.encode(GameApp.player.username) + "/" + Db.encode(invId));
                        if (inv == null) return;
                        String arenaId = inv.optString("arena_id", "");
                        Db.delete(INVITES + Db.encode(GameApp.player.username) + "/" + Db.encode(invId));
                        joinArena(arenaId, "player");
                    }).start();
                });
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
            });
        }).start();
    }

    // ----------------------------------------------------- survival
    private static void startSurvival(final String arenaId) {
        final Context ctx = GameApp.uiCtx();
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                String host = arena.optString("host", "");
                boolean isHost = host.equals(GameApp.player.username) || host.equals(Db.encode(GameApp.player.username));
                if (!isHost) {
                    UI.post(() -> U.toast(GameApp.T("فقط المضيف يمكنه بدء المعركة.", "Only the host can start the battle.")));
                    return;
                }
                if (StrategicDuelSystem.isPvpActive() || arenaBattleStarting) {
                    UI.post(() -> U.toast(GameApp.T("هناك معركة قيد التنفيذ الآن!", "A battle is already in progress!")));
                    return;
                }
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                long now = System.currentTimeMillis() / 1000;
                List<String> fighters = new ArrayList<>();
                Iterator<String> fit = members.keys();
                while (fit.hasNext()) {
                    String k = fit.next();
                    JSONObject m = members.optJSONObject(k);
                    if (m == null || "spectator".equals(m.optString("role"))) continue;
                    fighters.add(k);
                }
                if (fighters.size() != 2) {
                    UI.post(() -> U.alert(ctx, GameApp.T("المعركة الحقيقية", "Real Battle"),
                            GameApp.T("المعركة الاستراتيجية الحقيقية تُلعب واحداً ضد واحد.\n\nتحتاج لاعبَين بالضبط للبدء — اجعل اللاعبين الآخرين مشاهدين أولاً.",
                                    "The real strategic battle is played 1 vs 1.\n\nYou need exactly 2 players to start — make the other players spectators first."),
                            GameApp.T("حسنا", "OK"), null));
                    return;
                }
                final String myKey = Db.encode(GameApp.player.username);
                String oppKey = fighters.get(0).equals(myKey) ? fighters.get(1) : fighters.get(0);
                JSONObject oppM = members.optJSONObject(oppKey);
                if (oppM == null) return;
                if (now - oppM.optLong("last_seen", 0) > 90) {
                    UI.post(() -> U.toast(GameApp.T("الخصم غير متواجد في الساحة حالياً.", "The opponent is not present in the arena right now.")));
                    return;
                }
                final String oppId = dec(oppKey);
                final String oppName = oppM.optString("name", oppId);
                UI.post(() -> {
                    final ProgressDialog progress = U.progress(ctx, GameApp.T("بدء المعركة", "Starting Battle"), GameApp.T("جاري تجهيز ساحة المعركة...", "Preparing the battle arena..."), true);
                    new Thread(() -> {
                        final JSONObject oppData = Db.get(USER + oppKey);
                        UI.post(() -> {
                            progress.dismiss();
                            if (oppData == null) {
                                U.alert(ctx, null, GameApp.T("تعذر تحميل بيانات الخصم.", "Failed to load the opponent's data."), GameApp.T("حسنا", "OK"), null);
                                return;
                            }
                            startSurvivalImpl(arenaId, oppId, oppName, oppData);
                        });
                    }).start();
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static void startSurvivalImpl(final String arenaId, final String oppId, final String oppName, final JSONObject oppData) {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("بدء المعركة", "Starting Battle"), GameApp.T("جاري دخول ساحة المعركة...", "Entering the battle arena..."), true);
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) {
                    UI.post(() -> {
                        progress.dismiss();
                        U.alert(ctx, null, GameApp.T("تعذر بدء المعركة.", "Could not start the battle."), GameApp.T("حسنا", "OK"), null);
                    });
                    return;
                }
                JSONObject me = arena.optJSONObject("members").optJSONObject(Db.encode(GameApp.player.username));
                JSONObject oppIn = arena.optJSONObject("members").optJSONObject(Db.encode(oppId));
                if (me == null || oppIn == null) {
                    UI.post(() -> {
                        progress.dismiss();
                        U.alert(ctx, null, GameApp.T("أحد المقاتلين لم يعد في الساحة.", "One of the fighters is no longer in the arena."), GameApp.T("حسنا", "OK"), null);
                    });
                    return;
                }
                UI.post(() -> {
                    progress.dismiss();
                    StrategicDuelSystem.startArenaBattle(arenaId, oppId, oppName, true, oppData);
                });
            } catch (Exception ignored) {
                UI.post(progress::dismiss);
            }
        }).start();
    }

    private static String dec(String encoded) {
        try { return java.net.URLDecoder.decode(encoded, "UTF-8"); }
        catch (Exception e) { return encoded; }
    }

    private static List<String> simulateSurvival(List<String[]> fighters) {
        List<String> current = new ArrayList<>();
        for (String[] f : fighters) current.add(f[0]);
        while (current.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i + 1 < current.size(); i += 2) {
                String a = current.get(i);
                String b = current.get(i + 1);
                String w = duelWinner(a, b);
                next.add(w);
            }
            if (current.size() % 2 == 1) next.add(current.get(current.size() - 1));
            current = next;
        }
        return current;
    }

    private static String duelWinner(String a, String b) {
        double pa = fighterPower(a);
        double pb = fighterPower(b);
        double ra = pa * (0.85 + (NumberUtil.rand100() / 100.0) * 0.3);
        double rb = pb * (0.85 + (NumberUtil.rand100() / 100.0) * 0.3);
        return ra >= rb ? a : b;
    }

    private static double fighterPower(String encodedUser) {
        try {
            JSONObject pd = Db.get(USER + encodedUser);
            if (pd == null) return 100;
            double pow = pd.optDouble("total_power", 0);
            if (pow <= 0) pow = pd.optInt("level", 1) * 100.0;
            return pow;
        } catch (Exception e) {
            return 100;
        }
    }

    private static void bumpMedal(String encodedUser) {
        new Thread(() -> {
            try {
                String raw = encodedUser;
                String key = raw.contains("%") ? raw : Db.encode(raw);
                JSONObject rec = Db.get(MEDALS + key);
                long c = rec == null ? 0 : rec.optLong("count", 0);
                long w = rec == null ? 0 : rec.optLong("wins", 0);
                JSONObject pd = Db.get(USER + key);
                JSONObject nr = new JSONObject();
                nr.put("count", c + 1);
                nr.put("wins", w + 1);
                nr.put("name", pd == null ? key : pd.optString("name", key));
                nr.put("level", pd == null ? 1 : pd.optInt("level", 1));
                Db.put(MEDALS + key, nr);
            } catch (Exception ignored) {}
        }).start();
    }

    /** Called once by the winning fighter's client when an arena real battle ends. */
    public static void onArenaBattleEnd(final String arenaId, final String winnerId) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                String winnerName = winnerId;
                if (members != null) {
                    JSONObject wm = members.optJSONObject(Db.encode(winnerId));
                    if (wm != null && !wm.optString("name", "").isEmpty()) winnerName = wm.optString("name");
                }
                long reward = (long) Math.floor((arena.optInt("slots", 2) * SLOT_FEE) * 0.5) + arena.optLong("pot", 0);
                if (arena.optBoolean("result_applied", false)) {
                    sendChat(arenaId, GameApp.T("[النظام] انتهت المعركة! البطل:", "[System] Battle finished! Champion:") + winnerName);
                    return;
                }
                arena.put("champion", winnerId);
                arena.put("reward", reward);
                arena.put("result_applied", true);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                if (reward > 0) addGiftRecord(Db.encode(winnerId), "", GameApp.T("جائزة معركة الساحة", "Arena Battle Prize"), "gold", reward, "");
                bumpMedal(winnerId);
                sendChat(arenaId, GameApp.T("[النظام] انتهت المعركة! البطل:", "[System] Battle finished! Champion:") + winnerName + GameApp.T(" — الجائزة:", " — Prize:") + NumberUtil.formatNumber(reward) + GameApp.T(" ذهب + ميدالية", " gold + Medal"));
            } catch (Exception ignored) {}
        }).start();
    }

    private static void toggleMuteAll(final String arenaId) {
        new Thread(() -> {
            try {
                JSONObject arena = Db.get(ARENAS + Db.encode(arenaId));
                if (arena == null) return;
                JSONObject members = arena.optJSONObject("members");
                if (members == null) return;
                Iterator<String> it = members.keys();
                boolean hasActive = false;
                while (it.hasNext()) {
                    JSONObject m = members.optJSONObject(it.next());
                    if (m == null || "host".equals(m.optString("role"))) continue;
                    if (!m.optBoolean("voice_banned", false)) { hasActive = true; break; }
                }
                it = members.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject m = members.optJSONObject(k);
                    if (m == null || "host".equals(m.optString("role"))) continue;
                    m.put("voice_banned", hasActive);
                }
                arena.put("members", members);
                Db.put(ARENAS + Db.encode(arenaId), arena);
                try { VoiceChatController.instance().setAllPeersListenEnabled(!hasActive); } catch (Throwable ignored) {}
                final boolean fMuted = hasActive;
                UI.post(() -> U.toast(fMuted
                        ? GameApp.T("تم كتم صوت جميع اللاعبين.", "All players' voices have been muted.")
                        : GameApp.T("تم فتح صوت جميع اللاعبين.", "All players' voices have been unmuted.")));
                roomPoll(arenaId);
            } catch (Exception ignored) {}
        }).start();
    }

    // ------------------------------------------------------- gifts / inbox
    private static void addGiftRecord(String encodedTarget, String fromName, String label, String type, long amount, String itemJson) {
        try {
            String gid = "" + System.currentTimeMillis() + "_" + NumberUtil.rand(10, 99);
            JSONObject g = new JSONObject();
            g.put("from", GameApp.player == null ? "" : GameApp.player.username);
            g.put("from_name", fromName.isEmpty() && GameApp.player != null ? GameApp.player.name : fromName);
            g.put("type", type);
            g.put("amount", amount);
            if (!itemJson.isEmpty()) g.put("item", itemJson);
            g.put("label", label);
            g.put("time", System.currentTimeMillis());
            Db.put(GIFTS + encodedTarget + "/" + Db.encode(gid), g);
        } catch (Exception ignored) {}
    }

    public static void sendGiftMenu(final String targetId, final String targetName) {
        final Context ctx = GameApp.uiCtx();
        final String[] opts = {
                GameApp.T("إهداء ذهب", "Send Gold"),
                GameApp.T("إهداء كريستال", "Send Crystals"),
                GameApp.T("إهداء ألماس", "Send Diamonds"),
                GameApp.T("إهداء عنصر من حقيبتي", "Gift an Item from My Bag")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("هدية إلى", "Gift to") + " " + targetName);
        b.setItems(opts, (d, i) -> {
            if (i == 3) {
                giftOwnedItem(targetId, targetName);
                return;
            }
            final String what = i == 0 ? "gold" : (i == 1 ? "crystals" : "diamonds");
            final EditText ed = U.edit(ctx, GameApp.T("الكمية...", "Amount..."));
            LinearLayout lay = U.linear(ctx, true);
            lay.setPadding(30, 30, 30, 30);
            lay.addView(ed);
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(GameApp.T("إهداء " + what, "Gift " + what));
            b2.setView(lay);
            b2.setPositiveButton(GameApp.T("إرسال", "Send"), (d2, w2) -> {
                long amt = 0;
                try { amt = Long.parseLong(ed.getText().toString().trim()); } catch (Exception ignored) {}
                if (amt <= 0) return;
                transferResource(targetId, targetName, what, amt);
            });
            b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b2.show();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void giftOwnedItem(final String targetId, final String targetName) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        if (p.inventory == null || p.inventory.isEmpty()) {
            U.alert(ctx, null, GameApp.T("حقيبتك فارغة!", "Your bag is empty!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final List<Integer> indexes = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (int i = 0; i < p.inventory.size(); i++) {
            ItemData v = p.inventory.get(i);
            if (v == null) continue;
            indexes.add(i);
            String rarLabel = (v.rarity != null && !v.rarity.isEmpty()) ? " [" + GameApp.rarity(v.rarity) + "]" : "";
            String lvlLabel = v.level > 0 ? " " + GameApp.T("مستوى", "Level") + " " + v.level : "";
            String powLabel = " " + GameApp.T("القوة:", "Power:") + (long) Math.floor(InventoryUI.getItemPower(v));
            String eqMark = "";
            if (p.equipped != null) {
                for (String eq : p.equipped) {
                    if (eq != null && eq.equals(v.name)) { eqMark = " " + GameApp.T("[مجهز]", "[Equipped]"); break; }
                }
            }
            labels.add(v.name + (v.count > 1 ? " x" + v.count : "") + rarLabel + lvlLabel + powLabel + eqMark);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("اختر عنصراً من حقيبتك لإهدائه إلى", "Choose an item from your bag to gift to") + " " + targetName);
        b.setItems(labels.toArray(new String[0]), (d, idx) -> sendOwnedItemGift(targetId, targetName, indexes.get(idx)));
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void sendOwnedItemGift(final String targetId, final String targetName, final int invIndex) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        if (p.inventory == null || invIndex < 0 || invIndex >= p.inventory.size()) return;
        final ItemData item = p.inventory.get(invIndex);
        final String itemName = item.name;
        if (p.equipped != null) {
            for (int i = p.equipped.size() - 1; i >= 0; i--) {
                if (p.equipped.get(i) != null && p.equipped.get(i).equals(itemName)) {
                    p.equipped.remove(i);
                    if (item.boosts != null) {
                        p.stats.strength -= item.boosts.str;
                        p.stats.maxHp -= item.boosts.hp;
                        p.stats.agility -= item.boosts.agi;
                        p.stats.luck -= item.boosts.lck;
                        p.stats.endurance -= item.boosts.end;
                    }
                    if (item.atkMultiplier != 0 && item.appliedAtkBonus != 0) {
                        p.stats.strength -= item.appliedAtkBonus;
                        item.appliedAtkBonus = 0;
                    }
                }
            }
        }
        final JSONObject giftItem = item.toJSON();
        try { giftItem.put("count", 1); } catch (Exception ignored) {}
        final String itemJson = giftItem.toString();
        if (item.count > 1) item.count--;
        else p.inventory.remove(invIndex);
        SaveSystem.saveAndRefresh();
        new Thread(() -> {
            try {
                JSONObject g = new JSONObject();
                g.put("from", p.username);
                g.put("from_name", p.name);
                g.put("type", "item");
                g.put("amount", 0);
                g.put("item", itemJson);
                g.put("label", itemName);
                g.put("time", System.currentTimeMillis());
                dbPutGift(targetId, g);
            } catch (Exception ignored) {}
        }).start();
        UI.post(() -> U.toast(GameApp.T("تم إهداء", "Gifted") + " " + itemName + GameApp.T(" إلى ", " to ") + targetName));
    }

    private static void dbPutGift(String targetId, JSONObject gift) {
        try {
            Db.put(GIFTS + Db.encode(targetId) + "/" + Db.encode("" + System.currentTimeMillis() + "_" + NumberUtil.rand(10, 99)), gift);
        } catch (Exception ignored) {}
    }

    private static void transferResource(final String targetId, final String targetName, final String what, final long amt) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        long have = what.equals("gold") ? p.gold : (what.equals("crystals") ? p.crystals : p.diamonds);
        if (have < amt) {
            U.alert(ctx, null, GameApp.T("الكمية أكبر من رصيدك!", "Amount exceeds your balance!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        if (what.equals("gold")) p.gold -= amt;
        else if (what.equals("crystals")) p.crystals -= amt;
        else p.diamonds -= amt;
        SaveSystem.saveAndRefresh();
        new Thread(() -> {
            try {
                JSONObject g = new JSONObject();
                g.put("from", p.username);
                g.put("from_name", p.name);
                g.put("type", what);
                g.put("amount", amt);
                g.put("label", "");
                g.put("time", System.currentTimeMillis());
                dbPutGift(targetId, g);
            } catch (Exception ignored) {}
        }).start();
        UI.post(() -> U.toast(GameApp.T("تم إرسال ", "Sent ") + NumberUtil.formatNumber(amt) + GameApp.T(" إلى ", " to ") + targetName));
    }

    public static void openInbox() {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري جلب الهدايا...", "Fetching gifts..."), true);
        new Thread(() -> {
            final JSONObject all = Db.get(GIFTS + Db.encode(GameApp.player.username));
            UI.post(() -> {
                progress.dismiss();
                if (all == null || all.length() == 0) {
                    U.alert(ctx, null, GameApp.T("صندوق الهدايا فارغ.", "The gift inbox is empty."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final List<String> gids = new ArrayList<>();
                final List<String> labels = new ArrayList<>();
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String gid = it.next();
                    JSONObject g = all.optJSONObject(gid);
                    if (g == null) continue;
                    String type = g.optString("type", "gold");
                    String typeTxt = type.equals("gold") ? GameApp.T("ذهب", "Gold") : (type.equals("crystals") ? GameApp.T("كريستال", "Crystals") : (type.equals("diamonds") ? GameApp.T("ألماس", "Diamonds") : GameApp.T("عنصر", "Item")));
                    String lbl = g.optString("from_name", "?") + " -> " + typeTxt + " x" + g.optLong("amount", 0) + (g.optString("label", "").isEmpty() ? "" : " (" + g.optString("label", "") + ")");
                    labels.add(lbl);
                    gids.add(gid);
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("صندوق الهدايا", "Gift Inbox"));
                b.setItems(labels.toArray(new String[0]), (d, idx) -> claimGift(gids.get(idx)));
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
            });
        }).start();
    }

    private static void claimGift(final String gid) {
        final Context ctx = GameApp.uiCtx();
        new Thread(() -> {
            try {
                JSONObject g = Db.get(GIFTS + Db.encode(GameApp.player.username) + "/" + Db.encode(gid));
                if (g == null) return;
                Db.delete(GIFTS + Db.encode(GameApp.player.username) + "/" + Db.encode(gid));
                final String type = g.optString("type", "gold");
                final long amount = g.optLong("amount", 0);
                final String itemJson = g.optString("item", "");
                UI.post(() -> {
                    PlayerData p = GameApp.player;
                    if (type.equals("gold")) p.gold += amount;
                    else if (type.equals("crystals")) p.crystals += amount;
                    else if (type.equals("diamonds")) p.diamonds += amount;
                    else if (!itemJson.isEmpty()) {
                        try {
                            p.inventory.add(ItemData.fromJSON(new JSONObject(itemJson)));
                        } catch (Exception ignored) {}
                    }
                    GameApp.sound.playSnd("reward.mp3");
                    SaveSystem.saveAndRefresh();
                    U.alert(ctx, null, GameApp.T("تم استلام الهدية!", "Gift received!"), GameApp.T("رائع", "Awesome"), v -> openInbox());
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // ----------------------------------------------------- notification prefs UI
    public static void openNotifSettingsUI() {
        final Context ctx = GameApp.uiCtx();
        final String[] labels = notifLabels();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إعدادات الإشعارات", "Notification Settings"));
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < NOTIF_KEYS.length; i++) {
            String st = notifEnabled(NOTIF_KEYS[i]) ? GameApp.T("مفعل", "ON") : GameApp.T("معطل", "OFF");
            body.append("• ").append(labels[i]).append(": ").append(st).append("\n");
        }
        b.setView(U.msg(body.toString()));
        final String[] opts = new String[NOTIF_KEYS.length + 1];
        for (int i = 0; i < NOTIF_KEYS.length; i++) {
            opts[i] = labels[i] + " (" + (notifEnabled(NOTIF_KEYS[i]) ? GameApp.T("مفعل", "ON") : GameApp.T("معطل", "OFF")) + ")";
        }
        opts[NOTIF_KEYS.length] = GameApp.T("تشغيل الكل", "Enable All");
        b.setItems(opts, (d, i) -> {
            if (i == NOTIF_KEYS.length) {
                for (String k : NOTIF_KEYS) GameApp.player.notifPrefs.put(k, true);
                SaveSystem.saveAndRefresh();
            } else {
                String k = NOTIF_KEYS[i];
                Boolean cur = GameApp.player.notifPrefs.get(k);
                GameApp.player.notifPrefs.put(k, cur == null || !cur);
                SaveSystem.saveAndRefresh();
            }
            openNotifSettingsUI();
        });
        b.setNegativeButton(GameApp.T("رجوع", "Back"), (d, w) -> SettingsSystem.openSettingsUI());
        b.show();
    }
}