package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChatSystem {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String CHAT_ROOMS_PATH = "private_chats/";
    private static final String USER_PATH = "players/";
    public static volatile boolean chatOpen = false;
    public static final Dialog[] chatDialogHolder = new Dialog[1];

    private static String chatBlockMessage(PlayerData player) {
        if (player == null) return null;
        long now = System.currentTimeMillis() / 1000;
        if (player.isBanned && (player.bannedUntil == -1 || player.bannedUntil > now)) {
            return GameApp.T("أنت محظور من اللعبة.\nالسبب:", "You are banned from the game.\nReason:")
                    + (player.banReason == null || player.banReason.isEmpty() ? GameApp.T("غير محدد", "Not specified") : player.banReason)
                    + GameApp.T("\nالمدة المتبقية:", "\nTime remaining:")
                    + (player.bannedUntil == -1 ? GameApp.T("إلى الأبد", "Forever") : NumberUtil.formatRemainingTime(player.bannedUntil));
        }
        if (player.chatBanned && (player.chatBanUntil == -1 || player.chatBanUntil > now)) {
            return GameApp.T("أنت محظور من الدردشة.\nالسبب:", "You are banned from chat.\nReason:")
                    + (player.chatBanReason == null || player.chatBanReason.isEmpty() ? GameApp.T("غير محدد", "Not specified") : player.chatBanReason)
                    + GameApp.T("\nالمدة المتبقية:", "\nTime remaining:")
                    + (player.chatBanUntil == -1 ? GameApp.T("إلى الأبد (حتى يفك الإدارة الحظر)", "Forever (until the administration lifts it)") : NumberUtil.formatRemainingTime(player.chatBanUntil));
        }
        return null;
    }

    public static void forceChatBanKick(final long until, final String reason) {
        chatOpen = false;
        if (chatDialogHolder[0] != null) {
            try { chatDialogHolder[0].dismiss(); } catch (Exception ignored) {}
            chatDialogHolder[0] = null;
        }
        String rem = (until == -1) ? GameApp.T("إلى الأبد (حتى يفك الإدارة الحظر)", "Forever (until the administration lifts it)") : NumberUtil.formatRemainingTime(until);
        U.alert(GameApp.uiCtx(), GameApp.T("أنت محظور من الدردشة", "You Are Banned from Chat"),
                GameApp.T("لقد تم حظرك من الدردشة من قبل الإدارة.\n\nالسبب:", "You have been banned from chat by the administration.\n\nReason:")
                        + (reason == null || reason.isEmpty() ? GameApp.T("غير محدد", "Not specified") : reason)
                        + GameApp.T("\nالمدة المتبقية:", "\nRemaining time:") + rem,
                GameApp.T("حسنا", "OK"), null);
    }

    public static void openChatSelector() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        String blockMsg = chatBlockMessage(player);
        if (blockMsg != null) {
            U.alert(ctx, GameApp.T("لا يمكنك فتح الدردشة", "You Cannot Open the Chat"), blockMsg, GameApp.T("حسنا", "OK"), null);
            return;
        }
        String[] opts = {GameApp.T("عامة", "Public"), GameApp.T("خاصة مع صديق", "Private with Friend"), GameApp.T("رسالة للاعب معين (بحث بالاسم)", "Message a Player (Search by Name)"), GameApp.T("سجل الرسائل الخاصة", "PM History")};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الدردشة", "Chat"));
        b.setItems(opts, (d, i) -> {
            if (i == 0) {
                openChat(true, "", "");
            } else if (i == 3) {
                openPMHistoryUI();
            } else if (i == 1) {
                if (player.friendsDetailed.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا أصدقاء!", "No friends!"), GameApp.T("حسنا", "OK"), null);
                } else {
                    final String[] fNames = new String[player.friendsDetailed.size()];
                    for (int j = 0; j < player.friendsDetailed.size(); j++) {
                        fNames[j] = player.friendsDetailed.get(j).name;
                    }
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setItems(fNames, (d2, idx) -> {
                        final com.lli.com.core.FriendEntry f = player.friendsDetailed.get(idx);
                        openChat(false, f.username, f.name);
                    });
                    b2.show();
                }
            } else {
                final EditText searchEt = U.edit(ctx, GameApp.T("اكتب اسم اللاعب...", "Enter player name..."));
                AlertDialog.Builder b3 = new AlertDialog.Builder(ctx);
                b3.setTitle(GameApp.T("بحث عن لاعب", "Search for a Player"));
                b3.setView(searchEt);
                b3.setPositiveButton(GameApp.T("بحث", "Search"), (d2, w) -> {
                    final String query = searchEt.getText().toString().trim();
                    if (query.isEmpty()) return;
                    new Thread(() -> {
                        try {
                            JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
                            if (all == null) { UI.post(() -> U.toast(GameApp.T("لا يوجد لاعبين", "No players found"))); return; }
                            Iterator<String> it = all.keys();
                            while (it.hasNext()) {
                                String pid = it.next();
                                if (pid.isEmpty()) continue;
                                JSONObject pd = all.optJSONObject(pid);
                                if (pd != null && query.equalsIgnoreCase(pd.optString("name", ""))) {
                                    final String pidF = pid;
                                    final String nameF = pd.optString("name", pid);
                                    UI.post(() -> openChat(false, pidF, nameF));
                                    return;
                                }
                            }
                            UI.post(() -> U.toast(GameApp.T("لم يتم العثور على لاعب بهذا الاسم.", "No player found with that name.")));
                        } catch (Exception ignored) { UI.post(() -> U.toast(GameApp.T("خطأ في البحث.", "Search error."))); }
                    }).start();
                });
                b3.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b3.show();
            }
        });
        b.show();
    }

    private static String roomName(boolean isGlobal, String targetId) {
        if (isGlobal) return "chat_stable";
        final String me = GameApp.player.username;
        final String a = me.compareTo(targetId) < 0 ? me : targetId;
        final String b = me.compareTo(targetId) < 0 ? targetId : me;
        return CHAT_ROOMS_PATH + Db.encode(a) + "_" + Db.encode(b);
    }

    private static String dbDecode(String s) {
        if (s == null) return "";
        return s.replace("%2F", "/").replace("%5D", "]").replace("%5B", "[")
                .replace("%24", "$").replace("%23", "#").replace("%2E", ".");
    }

    public static void openPMHistoryUI() {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("سجل الرسائل الخاصة", "PM History"), GameApp.T("جاري تحميل السجلات...", "Loading history..."), true);
        new Thread(() -> {
            final JSONObject rooms = Db.get("private_chats");
            final JSONObject players = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                if (rooms == null || rooms.length() == 0) {
                    U.alert(ctx, null, GameApp.T("لا توجد محادثات خاصة بعد.", "No private conversations yet."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final String encMe = Db.encode(GameApp.player.username);
                final List<String[]> convs = new ArrayList<>(); // {otherUsername, otherName, roomKey, summary}
                Iterator<String> it = rooms.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    if (key == null) continue;
                    boolean mine = false;
                    String otherEnc = null;
                    if (key.startsWith(encMe + "_")) {
                        mine = true;
                        otherEnc = key.substring(encMe.length() + 1);
                    } else if (key.endsWith("_" + encMe)) {
                        mine = true;
                        otherEnc = key.substring(0, key.length() - encMe.length() - 1);
                    }
                    if (!mine || otherEnc == null || otherEnc.isEmpty()) continue;
                    String otherUser = dbDecode(otherEnc);
                    String otherName = otherUser;
                    if (players != null) {
                        JSONObject po = players.optJSONObject(otherEnc);
                        if (po != null) {
                            String nn = po.optString("name", "");
                            if (!nn.isEmpty()) otherName = nn;
                        }
                    }
                    JSONObject room = rooms.optJSONObject(key);
                    long lastTime = 0;
                    int cnt = 0;
                    if (room != null) {
                        Iterator<String> kit = room.keys();
                        while (kit.hasNext()) {
                            JSONObject mv = room.optJSONObject(kit.next());
                            if (mv == null) continue;
                            cnt++;
                            lastTime = Math.max(lastTime, mv.optLong("time", 0));
                        }
                    }
                    String summary = cnt + " " + GameApp.T("رسالة", "msgs");
                    if (lastTime > 0) {
                        summary += " • " + NumberUtil.formatRemainingTime(lastTime);
                    }
                    convs.add(new String[]{otherUser, otherName, key, summary});
                }
                if (convs.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا توجد محادثات خاصة بعد.", "No private conversations yet."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                convs.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
                final List<String> names = new ArrayList<>();
                for (String[] c : convs) names.add(c[1] + " (" + c[3] + ")");
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("سجل الرسائل الخاصة (" + convs.size() + ")", "PM History (" + convs.size() + ")"));
                b.setItems(names.toArray(new String[0]), (d, idx) -> {
                    String[] c = convs.get(idx);
                    openChat(false, c[0], c[1]);
                });
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                if (U.uiReady()) b.show();
            });
        }).start();
    }

    public static void openChat(final boolean isGlobal, final String targetId, final String targetDisplayName) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        String blockMsg = chatBlockMessage(player);
        if (blockMsg != null) {
            U.alert(ctx, GameApp.T("لا يمكنك فتح الدردشة", "You Cannot Open the Chat"), blockMsg, GameApp.T("حسنا", "OK"), null);
            return;
        }
        final String room = roomName(isGlobal, targetId);
        final String title = isGlobal ? GameApp.T("الدردشة العامة", "Public Chat") : (GameApp.T("دردشة مع", "Chat with") + (targetDisplayName != null && !targetDisplayName.isEmpty() ? targetDisplayName : targetId));
        final String[] lastCount = {"-1"};

        LinearLayout lay = U.linear(ctx, true);
        lay.setBackgroundColor(0xFF000000);
        TextView titleTxt = U.text(ctx, title, 18, Color.YELLOW, false);
        titleTxt.setGravity(Gravity.CENTER);
        titleTxt.setPadding(0, 20, 0, 20);
        final ListView list = new ListView(ctx);
        final java.util.List<String> chatLines = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, chatLines);
        list.setAdapter(adapter);
        final EditText edt = U.edit(ctx, GameApp.T("اكتب رسالة...", "Type a message..."));
        edt.setTextColor(Color.WHITE);

        list.setOnItemClickListener((l, v, p, id) -> {
            String msg = adapter.getItem(p);
            String senderName = GameApp.T("غير معروف", "Unknown");
            if (msg != null) {
                int colonIdx = msg.indexOf(':');
                String prefix = colonIdx >= 0 ? msg.substring(0, colonIdx) : msg;
                int lastClose = prefix.lastIndexOf(']');
                int lastOpen = prefix.lastIndexOf('[', lastClose);
                if (lastOpen >= 0 && lastClose > lastOpen) {
                    senderName = prefix.substring(lastOpen + 1, lastClose).trim();
                } else {
                    senderName = prefix.replaceAll("\\[", "").replaceAll("\\]", "").trim();
                }
                if (senderName.isEmpty()) senderName = GameApp.T("غير معروف", "Unknown");
            }
            final String fSender = senderName;
            List<String> opts = new ArrayList<>();
            opts.add(GameApp.T("الاستماع للرسالة", "Listen to Message"));
            opts.add(GameApp.T("الرد على الرسالة", "Reply to Message"));
            opts.add(GameApp.T("عرض الملف الشخصي", "View Profile"));
            opts.add(GameApp.T("مراسلة خاصة", "Private Message"));
            opts.add(GameApp.T("تحدي لمبارزة", "Challenge to Duel"));
            opts.add(GameApp.T("إرسال هدية", "Send a Gift"));
            if (player.adminData) {
                opts.add(GameApp.T("حظر مؤقت (24س)", "Temporary Ban (24h)"));
                opts.add(GameApp.T("حظر نهائي", "Permanent Ban"));
            }
            final String[] optArr = opts.toArray(new String[0]);
            final String fMsgText = msg;
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("خيارات:", "Options:") + fSender);
            b.setItems(optArr, (d, idx) -> {
                if (idx == 0) {
                    GameApp.speakTts(msg);
                } else if (idx == 1) {
                    openReplyDialog(ctx, room, fSender, fMsgText, edt);
                } else {
                    final ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري البحث...", "Searching..."), true);
                    new Thread(() -> {
                        JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1), "orderBy=\"name\"&equalTo=\"" + fSender + "\"");
                        if (all == null) {
                            JSONObject scan = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
                            if (scan != null) {
                                Iterator<String> kit = scan.keys();
                                while (kit.hasNext()) {
                                    String k = kit.next();
                                    JSONObject pv = scan.optJSONObject(k);
                                    if (pv != null && pv.optString("name", "").equals(fSender)) {
                                        JSONObject found = new JSONObject();
                                        try {
                                            found.put("x", pv);
                                        } catch (Exception ignored) {}
                                        all = found;
                                        break;
                                    }
                                }
                            }
                        }
                        final JSONObject fAll = all;
                        UI.post(() -> {
                            progress.dismiss();
                            if (fAll != null) {
                                Iterator<String> it = fAll.keys();
                                while (it.hasNext()) {
                                    String pid = it.next();
                                    JSONObject data = fAll.optJSONObject(pid);
                                    if (data == null) continue;
                                    if (idx == 2) {
                                        showProfile(data);
                                    } else if (idx == 3) {
                                        openChat(false, pid, data.optString("name", pid));
                                    } else if (idx == 4) {
                                        if (pid.equals(player.username)) {
                                            U.alert(ctx, null, GameApp.T("لا يمكنك مبارزة نفسك!", "You cannot duel yourself!"), GameApp.T("حسنا", "OK"), null);
                                            return;
                                        }
                                        StrategicDuelSystem.startStrategicDuel(true, data, true, GameApp.player.username);
                                    } else if (idx == 5) {
                                        com.lli.com.ui.ArenaSystem.sendGiftMenu(pid, data.optString("name", pid));
                                    } else if (idx == 6) {
                                        JSONObject bp = new JSONObject();
                                        try {
                                            bp.put("is_banned", true);
                                            bp.put("banned_until", System.currentTimeMillis() / 1000 + 86400);
                                            bp.put("ban_reason", GameApp.T("حظر مؤقت بواسطة المدير", "Temporary ban by admin"));
                                        } catch (Exception ignored) {}
                                        new Thread(() -> Db.patch(USER_PATH + Db.encode(pid), bp)).start();
                                        U.alert(ctx, null, GameApp.T("تم الحظر", "Banned"), GameApp.T("حسنا", "OK"), null);
                                    } else if (idx == 7) {
                                        JSONObject bp = new JSONObject();
                                        try {
                                            bp.put("is_banned", true);
                                            bp.put("banned_until", -1);
                                            bp.put("ban_reason", GameApp.T("حظر نهائي بواسطة المدير", "Permanent ban by admin"));
                                        } catch (Exception ignored) {}
                                        new Thread(() -> Db.patch(USER_PATH + Db.encode(pid), bp)).start();
                                        U.alert(ctx, null, GameApp.T("تم الحظر النهائي", "Permanently banned"), GameApp.T("حسنا", "OK"), null);
                                    }
                                    return;
                                }
                            }
                            U.alert(ctx, null, GameApp.T("تعذر العثور على اللاعب في قاعدة البيانات.", "Could not find the player in the database."), GameApp.T("حسنا", "OK"), null);
                        });
                    }).start();
                }
            });
            b.show();
        });

        final Runnable[] ticker = new Runnable[1];
        final Dialog[] dHolder = new Dialog[1];
        startTicker(room, lastCount, new String[]{"x"}, adapter, chatLines, list, ticker, dHolder, 1);

        lay.addView(titleTxt);
        FrameLayout frame = new FrameLayout(ctx);
        frame.addView(list, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 800));
        TextView emptyTxt = U.text(ctx, GameApp.T("السجل فارغ حالياً، كن أول من يكتب رسالة", "The log is currently empty, be the first to write a message"), 15, Color.GRAY, false);
        emptyTxt.setGravity(Gravity.CENTER);
        emptyTxt.setPadding(20, 40, 20, 40);
        frame.addView(emptyTxt, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 800));
        list.setEmptyView(emptyTxt);
        lay.addView(frame);
        lay.addView(edt);

        LinearLayout btnLay = U.linear(ctx, false);
        btnLay.setGravity(Gravity.CENTER);
        Button bSend = U.btn(ctx, GameApp.T("إرسال", "Send"));
        bSend.setOnClickListener(v -> {
            String txt = edt.getText().toString();
            if (!txt.isEmpty()) {
                String sendBlock = chatBlockMessage(player);
                if (sendBlock != null) {
                    U.alert(ctx, GameApp.T("تنبيه الحظر", "Ban Warning"), sendBlock, GameApp.T("حسنا", "OK"), null);
                    return;
                }
                String pTag = player.prestigeTitle != null ? ("[" + player.prestigeTitle + "]") : "";
                final String finalM = pTag + "[" + player.name + "]:" + txt;
                postMessage(room, finalM, edt);
            }
        });
        Button bGold = U.btn(ctx, GameApp.T("رسالة ملكية (1 ألماس)", "Royal Message (1 Diamond)"));
        bGold.setOnClickListener(v -> {
            String txt = edt.getText().toString();
            if (!txt.isEmpty() && player.diamonds >= 1) {
                String sendBlock = chatBlockMessage(player);
                if (sendBlock != null) {
                    U.alert(ctx, GameApp.T("تنبيه الحظر", "Ban Warning"), sendBlock, GameApp.T("حسنا", "OK"), null);
                    return;
                }
                player.diamonds -= 1;
                final String finalM = GameApp.T("[رسالة ملكية]", "[Royal Message]") + player.name + ":" + txt;
                postMessage(room, finalM, edt);
                SaveSystem.saveAndRefresh();
            } else {
                U.alert(ctx, null, GameApp.T("لا تملك ألماس أو الحقل فارغ!", "You have no diamonds or the field is empty!"), GameApp.T("حسنا", "OK"), null);
            }
        });
        final Dialog d = new Dialog(ctx);
        dHolder[0] = d;
        chatDialogHolder[0] = d;
        Button bClose = U.btn(ctx, GameApp.T("إغلاق", "Close"));
        bClose.setOnClickListener(v -> {
            ticker[0] = null;
            chatOpen = false;
            if (chatDialogHolder[0] == d) chatDialogHolder[0] = null;
            d.dismiss();
        });
        btnLay.addView(bSend, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnLay.addView(bGold, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnLay.addView(bClose, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        lay.addView(btnLay);
        d.setCancelable(true);
        d.setCanceledOnTouchOutside(false);
        d.setOnCancelListener(dl -> {
            ticker[0] = null;
            chatOpen = false;
            if (chatDialogHolder[0] == d) chatDialogHolder[0] = null;
        });
        d.setOnDismissListener(dl -> {
            ticker[0] = null;
            chatOpen = false;
            if (chatDialogHolder[0] == d) chatDialogHolder[0] = null;
        });
        d.setContentView(lay);
        d.show();
        chatOpen = true;
    }

    private static void startTicker(final String room, final String[] lastCount, final String[] lastSig,
                                    final ArrayAdapter<String> adapter, final java.util.List<String> chatLines,
                                    final ListView list, final Runnable[] tickerSlot, final Dialog[] dHolder, final int mode) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (tickerSlot[0] == null) return;
                new Thread(() -> {
                    JSONObject d = Db.get(room);
                    final JSONObject fD = d;
                    UI.post(() -> {
                        if (tickerSlot[0] == null) return;
                        if (GameApp.player != null) {
                            String blockMsg = chatBlockMessage(GameApp.player);
                            if (blockMsg != null) {
                                tickerSlot[0] = null;
                                chatOpen = false;
                                if (dHolder != null && dHolder[0] != null) {
                                    if (chatDialogHolder[0] == dHolder[0]) chatDialogHolder[0] = null;
                                    try { dHolder[0].dismiss(); } catch (Exception ignored) {}
                                }
                                U.alert(GameApp.uiCtx(), GameApp.T("تنبيه الحظر", "Ban Warning"), blockMsg, GameApp.T("حسنا", "OK"), null);
                                return;
                            }
                        }
                        if (fD != null) {
                            List<JSONObject> msgs = new ArrayList<>();
                            Iterator<String> it = fD.keys();
                            while (it.hasNext()) {
                                JSONObject v = fD.optJSONObject(it.next());
                                if (v != null) msgs.add(v);
                            }
                            msgs.sort((x, y) -> Long.compare(x.optLong("time", 0), y.optLong("time", 0)));
                            int cnt = msgs.size();
                            String lc = lastCount[0];
                            try {
                                if (!lc.equals("-1") && cnt > Integer.parseInt(lc)) {
                                    GameApp.sound.playSnd("msg.mp3");
                                }
                            } catch (Exception ignored) {}
                            lastCount[0] = String.valueOf(cnt);
                            try {
                                if (room.equals("chat_stable")) {
                                    GameApp.prefs.edit().putInt("last_global_chat_count", cnt).apply();
                                } else if (room.startsWith(CHAT_ROOMS_PATH)) {
                                    GameApp.prefs.edit().putInt("pm_count_" + room, cnt).apply();
                                }
                            } catch (Exception ignored) {}
                            if (lastSig[0] == null || !lastSig[0].equals(countSig(msgs))) {
                                lastSig[0] = countSig(msgs);
                                int firstVisible = list.getFirstVisiblePosition();
                                int keepIdx = -1;
                                int keepDiff = 0;
                                if (list.getChildCount() > 0 && firstVisible >= 0) {
                                    keepIdx = firstVisible;
                                    keepDiff = list.getChildAt(0).getTop();
                                }
                                chatLines.clear();
                                for (JSONObject v : msgs) chatLines.add(v.optString("msg", ""));
                                adapter.notifyDataSetChanged();
                                if (chatLines.size() > 0) {
                                    int lastVisible = list.getLastVisiblePosition();
                                    boolean wasAtBottom = lastVisible < 0 || lastVisible >= chatLines.size() - 3;
                                    if (wasAtBottom) {
                                        list.setSelection(chatLines.size() - 1);
                                    } else if (keepIdx >= 0 && keepIdx < chatLines.size()) {
                                        list.setSelectionFromTop(keepIdx, keepDiff);
                                    }
                                }
                            }
                        }
                        if (tickerSlot[0] != null) UI.postDelayed(this, 2000);
                    });
                }).start();
            }
        };
        tickerSlot[0] = r;
        if (mode == 1) UI.postDelayed(r, 100);
    }

    private static String countSig(List<JSONObject> msgs) {
        StringBuilder sb = new StringBuilder();
        sb.append(msgs.size()).append(':');
        for (JSONObject m : msgs) {
            sb.append(m.optString("msg", "")).append('|');
            sb.append(m.optLong("time", 0)).append(';');
        }
        return sb.toString();
    }

    private static void postMessage(final String room, final String msg, final EditText edt) {
        JSONObject m = new JSONObject();
        try {
            m.put("msg", msg);
            m.put("time", System.currentTimeMillis() / 1000);
        } catch (Exception ignored) {}
        final JSONObject fm = m;
        boolean priv = room != null && !room.startsWith("chat_stable");
        String shortM = (msg == null || msg.length() <= 45) ? msg : msg.substring(0, 45);
        com.lli.com.core.Db.serverLog((priv ? GameApp.T("رسالة خاصة", "PM") : GameApp.T("رسالة عامة", "Global chat")) + ": " + shortM);
        new Thread(() -> {
            String key = "m_" + System.currentTimeMillis() + "_" + NumberUtil.rand(1000, 9999);
            Db.put(room + "/" + key, fm);
            UI.post(() -> {
                if (edt != null) edt.setText("");
            });
        }).start();
    }

    private static void openReplyDialog(final Context ctx, final String room, final String senderName, final String originalMsg, final EditText outEdt) {
        final PlayerData player = GameApp.player;
        String blockMsg = chatBlockMessage(player);
        if (blockMsg != null) {
            U.alert(ctx, GameApp.T("تنبيه الحظر", "Ban Warning"), blockMsg, GameApp.T("حسنا", "OK"), null);
            return;
        }
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(20, 20, 20, 20);
        lay.addView(U.text(ctx, GameApp.T("الرد على", "Replying to") + " " + senderName, 16, Color.YELLOW, true));
        String shortOrig = (originalMsg == null || originalMsg.length() <= 60) ? originalMsg : originalMsg.substring(0, 60) + "...";
        lay.addView(U.text(ctx, shortOrig, 13, Color.GRAY, false));
        final EditText ed = U.edit(ctx, GameApp.T("اكتب ردك...", "Type your reply..."));
        ed.setTextColor(Color.BLACK);
        lay.addView(ed);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الرد على الرسالة", "Reply to Message"));
        b.setView(U.scroll(ctx, lay));
        b.setPositiveButton(GameApp.T("إرسال", "Send"), (d, w) -> {
            String txt = ed.getText().toString().trim();
            if (txt.isEmpty()) return;
            String sendBlock = chatBlockMessage(player);
            if (sendBlock != null) {
                U.alert(ctx, GameApp.T("تنبيه الحظر", "Ban Warning"), sendBlock, GameApp.T("حسنا", "OK"), null);
                return;
            }
            String pTag = player.prestigeTitle != null ? ("[" + player.prestigeTitle + "]") : "";
            final String replyM = pTag + "[" + player.name + "]:" + GameApp.T("رد على", "Reply to") + " " + senderName + ": " + txt;
            postMessage(room, replyM, outEdt);
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    public static void openPlayerProfile(final String id) {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("ملف اللاعب", "Player Profile"), GameApp.T("جاري التحميل...", "Loading..."), true);
        new Thread(() -> {
            JSONObject data = Db.get(USER_PATH + Db.encode(id));
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                if (data == null) {
                    U.alert(ctx, null, GameApp.T("تعذر العثور على اللاعب", "Could not find the player"), GameApp.T("حسنا", "OK"), null);
                } else {
                    showProfile(data);
                }
            });
        }).start();
    }

    private static void showProfile(final JSONObject data) {        final Context ctx = GameApp.uiCtx();
        String msg = GameApp.T("الاسم:", "Name:") + data.optString("name", "?") + "\n"
                + GameApp.T("المستوى:", "Level:") + data.optInt("level", 1) + "\n"
                + GameApp.T("القوة:", "Power:") + data.optLong("total_power", 0) + "\n"
                + GameApp.T("الذهب:", "Gold:") + data.optLong("gold", 0) + "\n"
                + GameApp.T("القبيلة:", "Clan:") + data.optString("clan", GameApp.T("لا يوجد", "None")) + "\n"
                + GameApp.T("الجنس:", "Gender:") + genderText(data.optString("gender", "")) + "\n"
                + GameApp.T("اللغة:", "Language:") + languageText(data.optString("language", ""));
        long lastOnline = data.optLong("last_online", 0);
        String onlineState;
        if (lastOnline > 0 && (System.currentTimeMillis() / 1000) - lastOnline < 120) {
            onlineState = GameApp.T("متصل الآن", "Online Now");
        } else {
            onlineState = GameApp.T("آخر ظهور:", "Last seen:") + NumberUtil.formatRemainingTime(lastOnline + 120);
        }
        msg += "\n" + onlineState;
        String gv = data.optString("game_version", "");
        if (gv == null || gv.isEmpty()) gv = GameApp.T("غير معروف (إصدار قديم)", "Unknown (old version)");
        msg += "\n" + GameApp.T("إصدار اللعبة: ", "Game version: ") + gv;
        U.alert(ctx, data.optString("name", GameApp.T("ملف اللاعب", "Player Profile")), msg, GameApp.T("حسنا", "OK"), null);
    }

    private static String genderText(String g) {
        if ("male".equals(g)) return GameApp.T("ذكر", "Male");
        if ("female".equals(g)) return GameApp.T("أنثى", "Female");
        return GameApp.T("غير محدد", "Not specified");
    }

    private static String languageText(String l) {
        if ("en".equals(l)) return "English";
        if ("ar".equals(l)) return "العربية";
        return GameApp.T("غير محدد", "Not specified");
    }
}
