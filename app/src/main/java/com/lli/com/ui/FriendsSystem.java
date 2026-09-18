package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.FriendEntry;
import com.lli.com.core.ItemData;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FriendsSystem {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String USER_PATH = "players/";

    public static void openFriendsUI() {
        final Context ctx = GameApp.uiCtx();
        String[] opts = {GameApp.T("إضافة صديق (إرسال طلب)", "Add Friend (Send Request)"), GameApp.T("طلبات الصداقة الواردة", "Incoming Friend Requests"), GameApp.T("قائمة الأصدقاء", "Friends List")};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الأصدقاء والدعم", "Friends & Support"));
        b.setItems(opts, (d, i) -> {
            if (i == 0) addFriendUI();
            else if (i == 1) incomingRequestsUI();
            else friendsListUI();
        });
        b.show();
    }

    private static void addFriendUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("الأصدقاء", "Friends"), GameApp.T("جاري إحضار اللاعبين...", "Fetching players..."), true);
        new Thread(() -> {
            JSONObject dt = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
            final List<String> ids = new ArrayList<>();
            final List<String> names = new ArrayList<>();
            final List<Integer> status = new ArrayList<>();
            if (dt != null) {
                Iterator<String> it = dt.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (k.equals(player.username)) continue;
                    JSONObject v = dt.optJSONObject(k);
                    if (v == null) continue;
                    boolean isFriend = false;
                    for (FriendEntry f : player.friendsDetailed) {
                        if (f.username.equals(k)) { isFriend = true; break; }
                    }
                    if (isFriend) continue;
                    boolean alreadyReq = false;
                    JSONArray reqs = v.optJSONArray("friend_requests");
                    if (reqs != null) {
                        for (int i = 0; i < reqs.length(); i++) {
                            JSONObject e = reqs.optJSONObject(i);
                            if (e != null && e.optString("username", "").equals(player.username)) { alreadyReq = true; break; }
                        }
                    }
                    ids.add(k);
                    names.add(v.optString("name", k));
                    status.add(alreadyReq ? 1 : 0);
                }
            }
            final List<String> fIds = ids;
            final List<String> fNames = names;
            final List<Integer> fStatus = status;
            UI.post(() -> {
                progress.dismiss();
                if (fIds.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبون متاحون حالياً.", "No players available right now."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                List<String> shown = new ArrayList<>();
                for (int i = 0; i < fNames.size(); i++) {
                    shown.add(fNames.get(i) + (fStatus.get(i) == 1 ? GameApp.T("(طلب مرسل)", "(Request Sent)") : ""));
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اختر لاعباً لإرسال طلب", "Choose a player to send a request"));
                b.setItems(shown.toArray(new String[0]), (d2, idx) -> {
                    if (fStatus.get(idx) == 1) {
                        U.alert(ctx, null, GameApp.T("لقد أرسلت طلباً لهذا اللاعب بالفعل.", "You already sent a request to this player."), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    final String targetId = fIds.get(idx);
                    final String targetName = fNames.get(idx);
                    new Thread(() -> {
                        JSONObject tData = Db.get(USER_PATH + Db.encode(targetId));
                        if (tData != null) {
                            boolean alreadyFriend = false;
                            JSONArray tfd = tData.optJSONArray("friends_detailed");
                            if (tfd != null) {
                                for (int i = 0; i < tfd.length(); i++) {
                                    JSONObject f = tfd.optJSONObject(i);
                                    if (f != null && f.optString("username", "").equals(player.username)) { alreadyFriend = true; break; }
                                }
                            }
                            if (alreadyFriend) {
                                UI.post(() -> U.alert(ctx, null, GameApp.T("أنتم أصدقاء بالفعل!", "You are already friends!"), GameApp.T("حسنا", "OK"), null));
                                return;
                            }
                            JSONArray reqs = tData.optJSONArray("friend_requests");
                            if (reqs == null) reqs = new JSONArray();
                            reqs.put(new FriendEntry(player.username, player.name).toJSON());
                            JSONObject patch = new JSONObject();
                            try {
                                patch.put("friend_requests", reqs);
                            } catch (Exception ignored) {}
                            Db.patch(USER_PATH + Db.encode(targetId), patch);
                            UI.post(() -> U.alert(ctx, null, GameApp.T("تم إرسال طلب الصداقة إلى", "Friend request sent to") + targetName, GameApp.T("حسنا", "OK"), null));
                        } else {
                            UI.post(() -> U.alert(ctx, null, GameApp.T("تعذر العثور على اللاعب!", "Player not found!"), GameApp.T("حسنا", "OK"), null));
                        }
                    }).start();
                });
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    public static void acceptFriendRequest(final FriendEntry req) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        boolean alreadyFriend = false;
        for (FriendEntry f : player.friendsDetailed) {
            if (f.username.equals(req.username)) { alreadyFriend = true; break; }
        }
        if (!alreadyFriend) player.friendsDetailed.add(new FriendEntry(req.username, req.name));
        if (!player.friends.contains(req.name)) player.friends.add(req.name);
        removeLocalRequest(req.username);
        SaveSystem.saveAndRefresh();
        new Thread(() -> {
            removeRequestFromServer(req.username);
            JSONObject tData = Db.get(USER_PATH + Db.encode(req.username));
            if (tData != null) {
                boolean already2 = false;
                JSONArray fd = tData.optJSONArray("friends_detailed");
                if (fd != null) {
                    for (int i = 0; i < fd.length(); i++) {
                        JSONObject f = fd.optJSONObject(i);
                        if (f != null && f.optString("username", "").equals(player.username)) { already2 = true; break; }
                    }
                } else {
                    fd = new JSONArray();
                }
                if (!already2) fd.put(new FriendEntry(player.username, player.name).toJSON());
                JSONArray oldF = tData.optJSONArray("friends");
                if (oldF == null) oldF = new JSONArray();
                boolean inOld = false;
                for (int i = 0; i < oldF.length(); i++) {
                    if (oldF.optString(i).equals(player.name)) { inOld = true; break; }
                }
                if (!inOld) oldF.put(player.name);
                JSONObject notif = new JSONObject();
                try {
                    notif.put("type", "friend");
                    notif.put("from_id", player.username);
                    notif.put("from_name", player.name);
                    notif.put("time", System.currentTimeMillis() / 1000);
                } catch (Exception ignored) {}
                JSONArray inbox = tData.optJSONArray("inbox");
                if (inbox == null) inbox = new JSONArray();
                inbox.put(notif);
                JSONObject patch = new JSONObject();
                try {
                    patch.put("friends_detailed", fd);
                    patch.put("friends", oldF);
                    patch.put("inbox", inbox);
                } catch (Exception ignored) {}
                Db.patch(USER_PATH + Db.encode(req.username), patch);
            }
        }).start();
        U.alert(ctx, null, GameApp.T("تم قبول الصداقة!", "Friendship accepted!"), GameApp.T("حسنا", "OK"), null);
    }

    public static void rejectFriendRequest(final FriendEntry req) {
        final Context ctx = GameApp.uiCtx();
        removeLocalRequest(req.username);
        SaveSystem.saveAndRefresh();
        new Thread(() -> removeRequestFromServer(req.username)).start();
        U.alert(ctx, null, GameApp.T("تم الرفض", "Request rejected"), GameApp.T("حسنا", "OK"), null);
    }

    private static void removeLocalRequest(String reqUsername) {
        PlayerData player = GameApp.player;
        for (int i = player.friendRequests.size() - 1; i >= 0; i--) {
            if (player.friendRequests.get(i).username.equals(reqUsername)) {
                player.friendRequests.remove(i);
            }
        }
    }

    private static void removeRequestFromServer(final String reqUsername) {
        try {
            JSONObject me = Db.get(USER_PATH + Db.encode(GameApp.player.username));
            if (me == null) return;
            JSONArray reqs = me.optJSONArray("friend_requests");
            if (reqs != null) {
                JSONArray nr = new JSONArray();
                for (int i = 0; i < reqs.length(); i++) {
                    JSONObject e = reqs.optJSONObject(i);
                    if (e == null || !e.optString("username", "").equals(reqUsername)) nr.put(e);
                }
                JSONObject patch = new JSONObject();
                try { patch.put("friend_requests", nr); } catch (Exception ignored) {}
                Db.patch(USER_PATH + Db.encode(GameApp.player.username), patch);
            }
        } catch (Exception ignored) {}
    }

    private static void incomingRequestsUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        if (player.friendRequests.isEmpty()) {
            U.alert(ctx, null, GameApp.T("لا توجد طلبات جديدة!", "No new requests!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final String[] reqNames = new String[player.friendRequests.size()];
        for (int i = 0; i < player.friendRequests.size(); i++) reqNames[i] = player.friendRequests.get(i).name;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("طلبات الصداقة", "Friend Requests"));
        b.setItems(reqNames, (d, idx) -> {
            final FriendEntry req = player.friendRequests.get(idx);
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(GameApp.T("طلب من", "Request from") + req.name);
            b2.setView(U.msg(GameApp.T("هل تريد قبول الصداقة؟", "Do you want to accept the friendship?")));
            b2.setPositiveButton(GameApp.T("قبول", "Accept"), (d2, w2) -> acceptFriendRequest(req));
            b2.setNegativeButton(GameApp.T("رفض", "Reject"), (d2, w2) -> rejectFriendRequest(req));
            b2.show();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void friendsListUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        if (player.friendsDetailed.isEmpty()) {
            U.alert(ctx, null, GameApp.T("لا يوجد أصدقاء!", "No friends!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final String[] fNames = new String[player.friendsDetailed.size()];
        for (int i = 0; i < player.friendsDetailed.size(); i++) fNames[i] = player.friendsDetailed.get(i).name;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setItems(fNames, (d, fIdx) -> {
            final FriendEntry f = player.friendsDetailed.get(fIdx);
            String[] opts = {GameApp.T("إرسال ذهب", "Send Gold"), GameApp.T("إرسال معدات", "Send Equipment"), GameApp.T("حذف", "Delete")};
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(f.name);
            b2.setItems(opts, (d2, oi) -> {
                if (oi == 0) sendGoldUI(f);
                else if (oi == 1) sendItemUI(f);
                else deleteFriend(f);
            });
            b2.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
            b2.show();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void sendGoldUI(final FriendEntry f) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final EditText edG = U.edit(ctx, GameApp.T("الكمية بالذهب...", "Amount in gold..."));
        edG.setInputType(InputType.TYPE_CLASS_NUMBER);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إرسال ذهب لـ", "Send Gold to") + f.name);
        b.setView(edG);
        b.setPositiveButton(GameApp.T("إرسال", "Send"), (d, w) -> {
            long amt;
            try {
                amt = Long.parseLong(edG.getText().toString().trim());
            } catch (Exception e) {
                amt = 0;
            }
            if (amt > 0 && amt <= player.gold) {
                final long fAmt = amt;
                final ProgressDialog p2 = U.progress(ctx, GameApp.T("إرسال", "Sending"), GameApp.T("جاري المعالجة...", "Processing..."), true);
                new Thread(() -> {
                    boolean ok = false;
                    for (int attempt = 0; attempt < 3 && !ok; attempt++) {
                        JSONObject dt = Db.get(USER_PATH + Db.encode(f.username));
                        if (dt == null) break;
                        long newGold = dt.optLong("gold", 0) + fAmt;
                        JSONObject patch = new JSONObject();
                        try {
                            patch.put("gold", newGold);
                        } catch (Exception ignored) {}
                        JSONArray inbox = dt.optJSONArray("inbox");
                        if (inbox == null) inbox = new JSONArray();
                        JSONObject notif = new JSONObject();
                        try {
                            notif.put("type", "gold");
                            notif.put("from_id", player.username);
                            notif.put("from_name", player.name);
                            notif.put("amt", fAmt);
                            notif.put("time", System.currentTimeMillis() / 1000);
                        } catch (Exception ignored) {}
                        inbox.put(notif);
                        try { patch.put("inbox", inbox); } catch (Exception ignored) {}
                        Db.patch(USER_PATH + Db.encode(f.username), patch);
                        JSONObject check = Db.get(USER_PATH + Db.encode(f.username));
                        if (check != null && check.optLong("gold", 0) >= newGold) ok = true;
                    }
                    final boolean fOk = ok;
                    UI.post(() -> {
                        p2.dismiss();
                        if (fOk) {
                            player.gold -= fAmt;
                            SaveSystem.saveAndRefresh();
                            U.alert(ctx, null, GameApp.T("تم إرسال", "Sent") + fAmt + GameApp.T("ذهب إلى", "gold to") + f.name, GameApp.T("حسنا", "OK"), null);
                        } else {
                            U.alert(ctx, null, GameApp.T("تعذر إرسال الذهب! حاول مجدداً.", "Failed to send gold! Try again."), GameApp.T("حسنا", "OK"), null);
                        }
                    });
                }).start();
            } else {
                U.alert(ctx, null, GameApp.T("الذهب غير كافٍ أو الكمية خاطئة!", "Not enough gold or invalid amount!"), GameApp.T("حسنا", "OK"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void sendItemUI(final FriendEntry f) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final List<String> avItems = new ArrayList<>();
        final List<ItemData> avData = new ArrayList<>();
        for (ItemData item : player.inventory) {
            if (!player.equipped.contains(item.name)) {
                avItems.add(item.name);
                avData.add(item);
            }
        }
        if (avItems.isEmpty()) {
            U.alert(ctx, null, GameApp.T("لا تملك معدات غير مجهزة!", "You have no unequipped equipment!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setItems(avItems.toArray(new String[0]), (d, idx) -> {
            final ItemData itToSend = avData.get(idx);
            final ProgressDialog p3 = U.progress(ctx, GameApp.T("إرسال", "Sending"), GameApp.T("جاري المعالجة...", "Processing..."), true);
            new Thread(() -> {
                JSONObject tData = Db.get(USER_PATH + Db.encode(f.username));
                if (tData != null) {
                    JSONArray inv = tData.optJSONArray("inventory");
                    if (inv == null) inv = new JSONArray();
                    inv.put(itToSend.toJSON());
                    JSONArray inbox = tData.optJSONArray("inbox");
                    if (inbox == null) inbox = new JSONArray();
                    JSONObject notif = new JSONObject();
                    try {
                        notif.put("type", "item");
                        notif.put("from_id", player.username);
                        notif.put("from_name", player.name);
                        notif.put("item_name", itToSend.name);
                        notif.put("item_json", itToSend.toJSON());
                        notif.put("time", System.currentTimeMillis() / 1000);
                    } catch (Exception ignored) {}
                    inbox.put(notif);
                    JSONObject patch = new JSONObject();
                    try {
                        patch.put("inventory", inv);
                        patch.put("inbox", inbox);
                    } catch (Exception ignored) {}
                    Db.patch(USER_PATH + Db.encode(f.username), patch);
                    UI.post(() -> {
                        p3.dismiss();
                        player.inventory.remove(itToSend);
                        SaveSystem.saveAndRefresh();
                        U.alert(ctx, null, GameApp.T("تم إرسال", "Sent") + itToSend.name + GameApp.T("إلى", "to") + f.name, GameApp.T("حسنا", "OK"), null);
                    });
                } else {
                    UI.post(() -> p3.dismiss());
                }
            }).start();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void deleteFriend(final FriendEntry f) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final ProgressDialog pDel = U.progress(ctx, GameApp.T("حذف", "Delete"), GameApp.T("جاري الحذف من الطرفين...", "Deleting from both sides..."), true);
        player.friendsDetailed.remove(f);
        player.friends.remove(f.name);
        new Thread(() -> {
            JSONObject tData = Db.get(USER_PATH + Db.encode(f.username));
            if (tData != null) {
                JSONObject patch = new JSONObject();
                boolean changed = false;
                JSONArray fd = tData.optJSONArray("friends_detailed");
                if (fd != null) {
                    JSONArray nf = new JSONArray();
                    for (int i = 0; i < fd.length(); i++) {
                        JSONObject e = fd.optJSONObject(i);
                        if (e == null || !e.optString("username", "").equals(player.username)) nf.put(e);
                        else changed = true;
                    }
                    try { patch.put("friends_detailed", nf); } catch (Exception ignored) {}
                }
                JSONArray oldF = tData.optJSONArray("friends");
                if (oldF != null) {
                    JSONArray no = new JSONArray();
                    for (int i = 0; i < oldF.length(); i++) {
                        String nm = oldF.optString(i);
                        if (!nm.equals(player.name)) no.put(nm);
                        else changed = true;
                    }
                    try { patch.put("friends", no); } catch (Exception ignored) {}
                }
                if (changed) Db.patch(USER_PATH + Db.encode(f.username), patch);
            }
            UI.post(() -> {
                pDel.dismiss();
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("تم حذف الصديق بنجاح من الطرفين", "Friend deleted successfully from both sides"), GameApp.T("حسنا", "OK"), null);
            });
        }).start();
    }
}
