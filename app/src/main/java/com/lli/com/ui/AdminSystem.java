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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.Db;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AdminSystem {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String USER_PATH = "players/";
    private static final String SHOPS_PATH = "shops.json";
    private static final String CHAT_ROOM = "chat_stable";
    private static ProgressDialog progressDelete;

    private static boolean hasPermission(String perm) {
        if (GameApp.player == null) return false;
        if (GameApp.player.isDev) return true;
        if (perm.equals("manage_chat")) return GameApp.player.adminData;
        if (perm.equals("manage_shop")) return GameApp.player.canManageShop;
        if (perm.equals("manage_voice")) return GameApp.player.adminData;
        if (perm.equals("manage_players")) return GameApp.player.canEditData;
        return false;
    }

    public static void openAdminPanelUI() {
        final Context ctx = GameApp.uiCtx();
        LinearLayout layout = U.linear(ctx, true);
        layout.setPadding(30, 30, 30, 30);
        layout.setBackgroundColor(0xFF111111);
        TextView title = U.text(ctx, GameApp.T("لوحة تحكم الإدارة", "Admin Control Panel"), 20, Color.YELLOW, false);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);
        layout.addView(title);
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout btnContainer = U.linear(ctx, true);
        scroll.addView(btnContainer);
        layout.addView(scroll);

        if (hasPermission("manage_chat")) addAdminBtn(btnContainer, GameApp.T("إدارة الدردشة", "Manage Chat"), 0xFF2196F3, () -> openChatAdminUI());
        if (hasPermission("manage_voice")) addAdminBtn(btnContainer, GameApp.T("إدارة الصوت والمراقبة السرية", "Voice Admin & Stealth Monitor"), 0xFF00BCD4, () -> openVoiceAdminUI());
        if (hasPermission("manage_chat")) addAdminBtn(btnContainer, GameApp.T("إرسال إعلان للجميع", "Send an Announcement to All"), 0xFF673AB7, AdminSystem::sendAnnouncement);
        if (hasPermission("manage_players")) addAdminBtn(btnContainer, GameApp.T("إدارة بيانات اللاعبين", "Manage Player Data"), 0xFF4CAF50, () -> openPlayerAdminUI());
        if (hasPermission("manage_shop")) addAdminBtn(btnContainer, GameApp.T("إدارة المتجر", "Manage Shop"), 0xFFFF9800, () -> openShopAdminUI());
        if (hasPermission("manage_punishments")) addAdminBtn(btnContainer, GameApp.T("الرقابة والعقوبات", "Supervision and Punishments"), 0xFFF44336, () -> openSanctionsUI());
        if (GameApp.player != null && GameApp.player.isDev && hasPermission("manage_players")) addAdminBtn(btnContainer, GameApp.T("منح الصلاحيات (مطور/مدير)", "Grant Permissions (Developer/Manager)"), 0xFF9C27B0, () -> openGrantRolesUI());
        if (hasPermission("manage_players")) addAdminBtn(btnContainer, GameApp.T("إرسال هدايا/عناصر", "Send Gifts/Items"), 0xFFE91E63, () -> openSendGiftUI());
        if (GameApp.player != null && GameApp.player.isDev) addAdminBtn(btnContainer, GameApp.T("رسالة للاعب / الردود الواردة", "Message to Player / Inbox Replies"), 0xFF00897B, () -> openStaffMessagingUI());
        if (GameApp.player != null && GameApp.player.isDev) addAdminBtn(btnContainer, GameApp.T("سجلات السيرفر 📝🖥️", "Server Logs 📝🖥️"), 0xFF263238, () -> openServerLogsUI());

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("نظام الإدارة", "Admin System"));
        b.setView(layout);
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    public static void openServerLogsUI() {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("سجلات السيرفر", "Server Logs"), GameApp.T("جاري تحميل السجلات...", "Loading logs..."), true);
        new Thread(() -> {
            final org.json.JSONArray arr = com.lli.com.core.Db.getServerLogs();
            final org.json.JSONObject players = com.lli.com.core.Db.get("players");
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                if (arr == null) {
                    U.alert(ctx, null, GameApp.T("لم يتم الوصول إلى السجلات", "Could not fetch logs"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                try {
                    if (arr.length() == 0) {
                        U.alert(ctx, null, GameApp.T("لا توجد سجلات بعد", "No logs yet"), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    LinearLayout list = U.linear(ctx, true);
                    list.setPadding(20, 20, 20, 20);
                    TextView head = U.text(ctx, GameApp.T("سجلات السيرفر — أحدث أولاً", "Server Logs — newest first") + " (" + arr.length() + ")", 17, Color.CYAN, true);
                    list.addView(head);
                    for (int i = arr.length() - 1; i >= 0; i--) {
                        org.json.JSONObject e = arr.optJSONObject(i);
                        if (e == null) continue;
                        long t = e.optLong("t", 0);
                        String dateStr = "";
                        if (t > 0) {
                            java.util.Date d = new java.util.Date(t * 1000L);
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.ENGLISH);
                            dateStr = sdf.format(d);
                        }
                        String meth = e.optString("method", "");
                        String u = e.optString("user", "");
                        String det = e.optString("detail", "");
                        String uname = u;
                        if (u != null && !u.isEmpty() && players != null) {
                            org.json.JSONObject pj = players.optJSONObject(com.lli.com.core.Db.encode(u));
                            if (pj == null) pj = players.optJSONObject(u);
                            if (pj != null) {
                                String nn = pj.optString("name", "");
                                if (nn != null && !nn.isEmpty()) uname = nn + " (" + u + ")";
                            }
                        }
                        String line = dateStr + "  " + logMethodWord(meth) + "  " + uname;
                        if (det != null && !det.isEmpty()) line += "  |  " + readableLogDetail(det);
                        TextView tv = U.text(ctx, line, 13, Color.WHITE, false);
                        tv.setPadding(0, 8, 0, 8);
                        list.addView(tv);
                    }
                    ScrollView sv = new ScrollView(ctx);
                    sv.addView(list);
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setTitle(GameApp.T("سجلات السيرفر 📝🖥️", "Server Logs 📝🖥️"));
                    b2.setView(sv);
                    b2.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                    AlertDialog dlg = b2.create();
                    android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                    dlg.getWindow().setLayout((int) (dm.widthPixels * 0.95f), (int) (dm.heightPixels * 0.9f));
                    dlg.show();
                } catch (Exception ex) {
                    U.alert(ctx, null, GameApp.T("خطأ في قراءة السجلات", "Error reading logs"), GameApp.T("حسنا", "OK"), null);
                }
            });
        }).start();
    }

    private static void addAdminBtn(LinearLayout container, String text, int color, Runnable func) {
        Button btn = U.btn(ctx(), text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 20);
        btn.setLayoutParams(params);
        btn.setOnClickListener(v -> func.run());
        container.addView(btn);
    }

    private static Context ctx() {
        return GameApp.uiCtx();
    }

    private static void openChatAdminUI() {
        final Context ctx = ctx();
        LinearLayout layout = U.linear(ctx, true);
        layout.setPadding(30, 30, 30, 30);
        Button btnClear = U.btn(ctx, GameApp.T("تنظيف الدردشة", "Clear Chat"));
        btnClear.setBackgroundColor(0xFFFF0000);
        btnClear.setTextColor(Color.WHITE);
        btnClear.setOnClickListener(v -> {
            AlertDialog.Builder c = new AlertDialog.Builder(ctx);
            c.setTitle(GameApp.T("تأكيد", "Confirm"));
            c.setView(U.msg(GameApp.T("هل أنت متأكد من حذف جميع الرسائل؟", "Are you sure you want to delete all messages?")));
            c.setPositiveButton(GameApp.T("نعم، احذف", "Yes, Delete"), (d, w) -> new Thread(() -> {
                Db.delete(CHAT_ROOM);
                try {
                    JSONObject m1 = new JSONObject();
                    m1.put("msg", "[النظام]: تم تنظيف الدردشة");
                    m1.put("time", System.currentTimeMillis() / 1000);
                    JSONObject m2 = new JSONObject();
                    m2.put("msg", "[System]: Chat has been cleaned");
                    m2.put("time", System.currentTimeMillis() / 1000);
                    String k1 = "m_" + System.currentTimeMillis() + "_c_" + ((System.nanoTime() % 9000) + 1000);
                    String k2 = "m_" + (System.currentTimeMillis() + 1) + "_c_" + (((System.nanoTime() / 7) % 9000) + 1000);
                    Db.put(CHAT_ROOM + "/" + k1, m1);
                    Db.put(CHAT_ROOM + "/" + k2, m2);
                } catch (Exception ignored) {}
                UI.post(() -> U.alert(ctx, null, GameApp.T("تم مسح الدردشة", "Chat cleared"), GameApp.T("حسنا", "OK"), null));
            }).start());
            c.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            c.show();
        });
        layout.addView(btnClear);
        layout.addView(U.text(ctx, GameApp.T("\nاختر رسالة لحذفها أو حظر صاحبها:", "\nChoose a message to delete or ban its sender:"), 14, Color.WHITE, false));

        final ScrollView sv = new ScrollView(ctx);
        final LinearLayout listLayout = U.linear(ctx, true);
        sv.addView(listLayout);
        layout.addView(sv);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(GameApp.T("إدارة الدردشة", "Manage Chat"))
                .setView(layout)
                .setNegativeButton(GameApp.T("إغلاق", "Close"), null)
                .create();
        dialog.show();
        android.util.DisplayMetrics admDm = ctx.getResources().getDisplayMetrics();
        dialog.getWindow().setLayout((int) (admDm.widthPixels * 0.95f), (int) (admDm.heightPixels * 0.75f));

        new Thread(() -> {
            JSONObject d = Db.get(CHAT_ROOM);
            final List<String> msgs = new ArrayList<>();
            final List<String> keys = new ArrayList<>();
            if (d != null) {
                Iterator<String> it = d.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject v = d.optJSONObject(k);
                    if (v != null) {
                        msgs.add(v.optString("msg", ""));
                        keys.add(k);
                    }
                }
            }
            UI.post(() -> {
                if (msgs.isEmpty()) {
                    listLayout.addView(U.text(ctx, GameApp.T("\nلا توجد رسائل في الدردشة.", "\nThere are no messages in the chat."), 14, Color.WHITE, false));
                }
                for (int i = 0; i < msgs.size(); i++) {
                    final String key = keys.get(i);
                    final String msgText = msgs.get(i);
                    final String senderName = extractSender(msgText);
                    final TextView tv = U.text(ctx, msgText, 13, 0xFFDDEEFF, false);
                    tv.setPadding(10, 12, 10, 12);
                    tv.setBackgroundColor(0xFF1B4F72);
                    tv.setOnClickListener(v -> {
                        String[] opts = {GameApp.T("حذف هذه الرسالة", "Delete This Message"), GameApp.T("حظر اللاعب من الدردشة (مؤقت)", "Ban Player from Chat (Temporary)"), GameApp.T("حظر اللاعب نهائياً", "Ban Player Permanently")};
                        AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                        b2.setTitle(GameApp.T("إدارة الرسالة", "Manage Message"));
                        b2.setItems(opts, (d3, i2) -> {
                            if (i2 == 0) {
                                new Thread(() -> Db.delete(CHAT_ROOM + "/" + Db.encode(key))).start();
                                U.alert(ctx, null, GameApp.T("تم حذف الرسالة", "Message deleted"), GameApp.T("حسنا", "OK"), null);
                            } else if (i2 == 1 || i2 == 2) {
                                final ProgressDialog progress = U.progress(ctx, GameApp.T("جاري البحث", "Searching"), GameApp.T("جاري جلب بيانات اللاعب...", "Fetching player data..."), true);
                                new Thread(() -> {
                                    JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
                                    UI.post(() -> {
                                        progress.dismiss();
                                        if (all != null) {
                                            Iterator<String> it2 = all.keys();
                                            String targetId = null;
                                            JSONObject targetData = null;
                                            while (it2.hasNext()) {
                                                String id = it2.next();
                                                JSONObject data = all.optJSONObject(id);
                                                if (data != null && data.optString("name", "").equals(senderName)) {
                                                    targetId = id;
                                                    targetData = data;
                                                    break;
                                                }
                                            }
                                            if (targetId != null && targetData != null) {
                                                openPunishmentUI(targetId, targetData);
                                            } else {
                                                U.alert(ctx, null, GameApp.T("لم يتم العثور على بيانات اللاعب:" + senderName, "Player data was not found:" + senderName), GameApp.T("حسنا", "OK"), null);
                                            }
                                        } else {
                                            U.alert(ctx, null, GameApp.T("قاعدة البيانات فارغة أو تعذر الوصول إليها", "The database is empty or inaccessible"), GameApp.T("حسنا", "OK"), null);
                                        }
                                    });
                                }).start();
                            }
                        });
                        b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                        b2.show();
                    });
                    listLayout.addView(tv);
                }
            });
        }).start();
    }

    private static String extractSender(String msgText) {
        if (msgText == null) return GameApp.T("غير معروف", "Unknown");
        String s = msgText;
        if (s.contains("[")) {
            int start = s.indexOf("[");
            int end = s.indexOf("]", start + 1);
            if (end > start) s = s.substring(start + 1, end);
        }
        s = s.trim();
        if (s.isEmpty()) return GameApp.T("غير معروف", "Unknown");
        return s;
    }

    private static final java.util.Map<String, String> LOG_FIELD_LABELS = new java.util.HashMap<>();

    static {
        LOG_FIELD_LABELS.put("name", "الاسم");
        LOG_FIELD_LABELS.put("username", "اسم المستخدم");
        LOG_FIELD_LABELS.put("gold", "الذهب");
        LOG_FIELD_LABELS.put("crystals", "الكريستال");
        LOG_FIELD_LABELS.put("diamonds", "الألماس");
        LOG_FIELD_LABELS.put("exp", "الخبرة");
        LOG_FIELD_LABELS.put("level", "المستوى");
        LOG_FIELD_LABELS.put("points", "نقاط التطوير");
        LOG_FIELD_LABELS.put("hp", "الطاقة");
        LOG_FIELD_LABELS.put("maxHp", "أقصى طاقة");
        LOG_FIELD_LABELS.put("last_online", "آخر ظهور");
        LOG_FIELD_LABELS.put("bank_gold", "بنك الذهب");
        LOG_FIELD_LABELS.put("stats", "الإحصائيات");
        LOG_FIELD_LABELS.put("inventory", "الحقيبة");
        LOG_FIELD_LABELS.put("equipped", "المعدات المجهزة");
        LOG_FIELD_LABELS.put("desc", "الوصف");
        LOG_FIELD_LABELS.put("effect", "التأثير");
        LOG_FIELD_LABELS.put("rarity", "الندرة");
        LOG_FIELD_LABELS.put("price", "السعر");
        LOG_FIELD_LABELS.put("msg", "الرسالة");
        LOG_FIELD_LABELS.put("reason", "السبب");
        LOG_FIELD_LABELS.put("event", "الحدث");
        LOG_FIELD_LABELS.put("category", "الفئة");
    }

    private static String logMethodWord(String m) {
        if (m == null) return "";
        switch (m) {
            case "PUT": return GameApp.T("تعديل", "Edit");
            case "POST": return GameApp.T("إنشاء", "Add");
            case "PATCH": return GameApp.T("تعديل", "Edit");
            case "DELETE": return GameApp.T("حذف", "Delete");
            case "ADMIN": return GameApp.T("إدارة", "Admin");
            case "CLIENT_EVENT": return GameApp.T("حدث", "Event");
            case "GET": return GameApp.T("قراءة", "Read");
            default: return m;
        }
    }

    private static String readableLogDetail(String det) {
        if (det == null) return "";
        String s = det.trim();
        if (s.isEmpty()) return s;
        s = s.replaceFirst("^(put|patch|set|create|update|delete|post)\\s+", "");
        if (s.equals("delete")) return GameApp.T("حذف", "Delete");
        if (s.equals("delete empty")) return GameApp.T("حذف (فارغ)", "Delete (empty)");
        if (s.equals("delete null")) return GameApp.T("حذف (لاشيء)", "Delete (nothing)");
        StringBuilder sb = new StringBuilder();
        String[] parts = s.split("[,،]");
        for (String raw : parts) {
            String p = raw.trim();
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append("، ");
            int eq = p.indexOf('=');
            int colon = p.indexOf(':');
            int sep = -1;
            if (eq > 0 && (colon < 0 || eq < colon)) sep = eq;
            else if (colon > 0) sep = colon;
            if (sep > 0) {
                String k = p.substring(0, sep).trim();
                String v = p.substring(sep + 1).trim();
                String lk = LOG_FIELD_LABELS.get(k);
                if (lk == null) lk = k;
                sb.append(lk).append(": ").append(v);
            } else {
                sb.append(p);
            }
        }
        return sb.toString();
    }

    private static String bagItemSummary(JSONObject item) {
        if (item == null) return "?";
        String nm = item.optString("name", "?");
        String rar = item.optString("rarity", "");
        if (rar == null || rar.isEmpty()) rar = GameApp.T("عادي", "Common");
        StringBuilder sb = new StringBuilder();
        sb.append(nm);
        sb.append("\n").append(GameApp.T("الندرة: ", "Rarity: ")).append(GameApp.rarity(rar));
        String cat = item.optString("category", "");
        if (cat != null && !cat.isEmpty()) sb.append(" | ").append(GameApp.T("الفئة: ", "Category: ")).append(cat);
        String desc = item.optString("desc", "");
        if (desc != null && !desc.isEmpty()) sb.append("\n").append(desc);
        String effect = item.optString("effect", "");
        if (effect != null && !effect.isEmpty()) sb.append("\n").append(GameApp.T("التأثير: ", "Effect: ")).append(effect);
        JSONObject boosts = item.optJSONObject("boosts");
        StringBuilder st = new StringBuilder();
        if (boosts != null) {
            if (boosts.optDouble("str", 0) > 0) st.append(GameApp.T("هجوم +", "Attack +")).append((long) boosts.optDouble("str", 0)).append(" ");
            if (boosts.optDouble("end", 0) > 0) st.append(GameApp.T("دفاع +", "Defense +")).append((long) boosts.optDouble("end", 0)).append(" ");
            if (boosts.optDouble("hp", 0) > 0) st.append(GameApp.T("صحة +", "HP +")).append((long) boosts.optDouble("hp", 0)).append(" ");
            if (boosts.optDouble("agi", 0) > 0) st.append(GameApp.T("رشاقة +", "Agility +")).append((long) boosts.optDouble("agi", 0)).append(" ");
            if (boosts.optDouble("lck", 0) > 0) st.append(GameApp.T("حظ +", "Luck +")).append((long) boosts.optDouble("lck", 0)).append(" ");
        }
        JSONObject stJ = item.optJSONObject("stats");
        if (stJ != null) {
            if (stJ.optDouble("strength", 0) > 0) st.append(GameApp.T("قوة +", "Strength +")).append((long) stJ.optDouble("strength", 0)).append(" ");
            if (stJ.optDouble("endurance", 0) > 0) st.append(GameApp.T("تحمل +", "Endurance +")).append((long) stJ.optDouble("endurance", 0)).append(" ");
            if (stJ.optDouble("maxHp", 0) > 0) st.append(GameApp.T("صحة قصوى +", "Max HP +")).append((long) stJ.optDouble("maxHp", 0)).append(" ");
        }
        if (item.optDouble("atk_multiplier", 0) > 0) st.append(GameApp.T("ضرر x", "Damage x")).append(item.optDouble("atk_multiplier", 1)).append(" ");
        if (item.optDouble("speed_bonus", 0) > 0) st.append(GameApp.T("سرعة +", "Speed +")).append((long) (item.optDouble("speed_bonus", 0) * 100)).append("% ");
        if (st.length() > 0) sb.append("\n").append(st.toString().trim());
        return sb.toString();
    }

    private static String ipLabel(JSONObject ips, String id, String base) {
        if (ips == null) return base;
        String ip = "";
        try {
            if (ips.has(Db.encode(id))) {
                Object v = ips.opt(Db.encode(id));
                ip = v instanceof JSONObject ? ((JSONObject) v).optString("ip", "") : String.valueOf(v);
            } else if (ips.has(id)) {
                Object v = ips.opt(id);
                ip = v instanceof JSONObject ? ((JSONObject) v).optString("ip", "") : String.valueOf(v);
            }
        } catch (Exception ignored) {}
        if (ip == null) return base;
        ip = ip.trim();
        if (ip.isEmpty() || "null".equals(ip) || "1".equals(ip) || ip.startsWith("[") && ip.endsWith("]")) return base;
        return base + " [IP:" + ip + "]";
    }

    private static void sendAnnouncement() {
        final Context ctx = ctx();
        final EditText et = U.edit(ctx, GameApp.T("اكتب نص الإعلان...", "Write the announcement text..."));
        LinearLayout lay = U.linear(ctx, false);
        lay.setPadding(40, 40, 40, 40);
        lay.addView(et);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إرسال إعلان للجميع", "Send an Announcement to All"));
        b.setView(lay);
        b.setPositiveButton(GameApp.T("إرسال", "Send"), (d, w) -> {
            final String msg = et.getText().toString().trim();
            if (msg.isEmpty()) {
                U.toast(GameApp.T("الرجاء كتابة نص الإعلان", "Please write the announcement text"));
                return;
            }
            new Thread(() -> {
                try {
                    long now = System.currentTimeMillis();
                    long nowSec = now / 1000;
                    JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
                    if (all == null) {
                        UI.post(() -> U.toast(GameApp.T("فشل: لا يوجد لاعبين في قاعدة البيانات", "Failed: there are no players in the database")));
                        return;
                    }
                    final String myId = GameApp.player == null ? "" : GameApp.player.username;
                    int sent = 0;
                    Iterator<String> it = all.keys();
                    while (it.hasNext()) {
                        String pid = it.next();
                        if (!pid.isEmpty() && pid.equals(myId)) continue;
                        JSONObject pd = all.optJSONObject(pid);
                        if (pd == null) continue;
                        long lastOnline = pd.optLong("last_online", 0);
                        if (lastOnline <= 0 || nowSec - lastOnline > 120) continue;
                        JSONObject a = new JSONObject();
                        a.put("msg", msg);
                        a.put("ts", nowSec);
                        Db.put("announcements/" + Db.encode(pid) + "/" + now, a);
                        sent++;
                    }
                    final int fSent = sent;
                    UI.post(() -> U.toast(GameApp.T("تم إرسال الإعلان إلى", "Announcement sent to") + " " + fSent + " " + GameApp.T("لاعب متصل لحظة الإرسال فقط.", "online-at-send-time players only.")));
                } catch (Exception e) {
                    UI.post(() -> U.toast(GameApp.T("فشل إرسال الإعلان.", "Failed to send the announcement.")));
                }
            }).start();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void openSanctionsUI() {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("مدير العقوبات", "Punishments Manager"), GameApp.T("جاري تحميل قائمة اللاعبين...", "Loading the players list..."), true);
        new Thread(() -> {
            JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
            final JSONObject ips = Db.get("player_ips");
            final JSONObject fAll = all;
            UI.post(() -> {
                progress.dismiss();
                if (fAll == null) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبين في قاعدة البيانات", "There are no players in the database"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final List<String> names = new ArrayList<>();
                final List<String> ids = new ArrayList<>();
                Iterator<String> it = fAll.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject pd = fAll.optJSONObject(id);
                    if (pd == null) continue;
                    names.add(ipLabel(ips, id, (pd.optString("name", GameApp.T("بدون اسم", "No name"))) + "(ID:" + id + ")"));
                    ids.add(id);
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اختر لاعباً للعقوبة", "Choose a Player for Punishment"));
                b.setItems(names.toArray(new String[0]), (d2, idx) -> {
                    JSONObject sel = fAll.optJSONObject(ids.get(idx));
                    if (sel != null) openPunishmentUI(ids.get(idx), sel);
                });
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    public static void openPunishmentUI(final String playerId, final JSONObject p) {
        final Context ctx = ctx();
        final boolean isDev = GameApp.player != null && GameApp.player.isDev;
        // Never allow punishing your own account.
        if (GameApp.player != null && playerId != null && playerId.equals(GameApp.player.username)) {
            U.alert(ctx, GameApp.T("تنبيه", "Warning"), GameApp.T("لا يمكنك تطبيق عقوبة على حسابك الخاص!", "You cannot apply a punishment to your own account!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final List<String> optsList = new ArrayList<>();
        if (isDev) {
            optsList.add(GameApp.T("سجن مؤقت", "Temporary Jail"));
            optsList.add(GameApp.T("سجن دائم", "Permanent Jail"));
            optsList.add(GameApp.T("فك السجن", "Release from Jail"));
            optsList.add(GameApp.T("حظر مؤقت", "Temporary Ban"));
            optsList.add(GameApp.T("حظر دائم", "Permanent Ban"));
            optsList.add(GameApp.T("فك الحظر", "Unban"));
        }
        optsList.add(GameApp.T("حظر دردشة مؤقت", "Temporary Chat Ban"));
        optsList.add(GameApp.T("حظر دردشة دائم", "Permanent Chat Ban"));
        optsList.add(GameApp.T("فك حظر الدردشة", "Unban from Chat"));
        if (isDev) {
            optsList.add(GameApp.T("حظر جهاز (IP)", "Ban Device (IP)"));
            optsList.add(GameApp.T("فك حظر الجهاز", "Unban Device"));
            optsList.add(GameApp.T("حظر نسخة قديمة (يُرفع تلقائياً بعد التحديث)", "Old-Version Ban (auto-lifts on update)"));
        }
        final String[] opts = optsList.toArray(new String[0]);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("خيارات العقوبة لـ", "Punishment options for") + (p.optString("name", playerId)));
        b.setItems(opts, (d2, i2) -> {
            if (isDev) {
                if (i2 == 0 || i2 == 1) {
                    showPunishDetailsUI(playerId, p, true, i2 == 1, false);
                } else if (i2 == 2) {
                    try {
                        p.put("is_jailed", false);
                        p.put("jail_until", 0);
                    } catch (Exception ignored) {}
                    applyPunishment(playerId, p, GameApp.T("تم فك السجن عن" + p.optString("name", playerId), "Released" + p.optString("name", playerId) + "from jail"));
                } else if (i2 == 3 || i2 == 4) {
                    showPunishDetailsUI(playerId, p, false, i2 == 4, false);
                } else if (i2 == 5) {
                    if (GameApp.isNeverUnban(playerId)) {
                        U.alert(ctx, GameApp.T("غير مسموح", "Not Allowed"), GameApp.T("لا يمكن فك الحظر عن هذا الحساب نهائياً.", "This account can never be unbanned."), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    try {
                        p.put("is_banned", false);
                        p.put("banned_until", 0);
                        p.put("ban_type", JSONObject.NULL);
                    } catch (Exception ignored) {}
                    applyPunishment(playerId, p, GameApp.T("تم فك الحظر عن" + p.optString("name", playerId), "Unbanned" + p.optString("name", playerId)));
                } else if (i2 == 6 || i2 == 7) {
                    showPunishDetailsUI(playerId, p, false, i2 == 7, true);
                } else if (i2 == 8) {
                    try {
                        p.put("chat_banned", false);
                        p.put("chat_ban_until", 0);
                        p.put("chat_ban_reason", JSONObject.NULL);
                    } catch (Exception ignored) {}
                    applyPunishment(playerId, p, GameApp.T("تم فك حظر الدردشة عن" + p.optString("name", playerId), "Unbanned" + p.optString("name", playerId) + "from chat"));
                } else if (i2 == 9) {
                    banDeviceByPlayer(playerId, p);
                } else if (i2 == 10) {
                    unbanDeviceByPlayer(playerId, p);
                } else if (i2 == 11) {
                    showOldVersionBanUI(playerId, p);
                }
            } else {
                if (i2 == 0 || i2 == 1) {
                    showPunishDetailsUI(playerId, p, false, i2 == 1, true);
                } else if (i2 == 2) {
                    try {
                        p.put("chat_banned", false);
                        p.put("chat_ban_until", 0);
                        p.put("chat_ban_reason", JSONObject.NULL);
                    } catch (Exception ignored) {}
                    applyPunishment(playerId, p, GameApp.T("تم فك حظر الدردشة عن" + p.optString("name", playerId), "Unbanned" + p.optString("name", playerId) + "from chat"));
                }
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void banDeviceByPlayer(final String playerId, final JSONObject p) {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("حظر جهاز", "Ban Device"), GameApp.T("جاري البحث عن IP اللاعب...", "Looking up the player's IP..."), true);
        new Thread(() -> {
            JSONObject ipRec = Db.get("player_ips/" + Db.encode(playerId));
            String sip = "";
            try { sip = Db.myIp(); } catch (Exception ignored) {}
            final String selfIp = sip;
            UI.post(() -> {
                progress.dismiss();
                if (ipRec == null || ipRec.optString("ip", "").isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لم يتم تسجيل IP لهذا اللاعب بعد (يجب أن يلعب مرة واحدة).", "No IP has been recorded for this player yet (they must play at least once)."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final String ip = ipRec.optString("ip", "");
                if (isLikelyInternalIp(ip)) {
                    AlertDialog.Builder w = new AlertDialog.Builder(ctx);
                    w.setTitle(GameApp.T("تحذير: IP داخلي مشترك", "Warning: Shared Internal IP"));
                    w.setView(U.msg(GameApp.T("الـ IP المسجل لهذا اللاعب هو (", "The IP recorded for this player is (") + ip + GameApp.T(") وهو IP داخلي/مشترك (يُظهر نفس الرقم لجميع اللاعبين).\nحظر هذا الـ IP سيحظر الجميع!\nملاحظة: حدث هذا بسبب أن نسخة السيرفر المنشورة قديمة — يجب تحديث ملف server.js على الخادم ليُعيد الـ IP الحقيقي لكل جهاز عبر x-forwarded-for.\nهل تريد الحظر على كل حال؟", ") which is an internal/shared address (the same single number you see for all players).\nBanning it would ban everyone!\nNote: this happens because the deployed server is outdated — update server.js on the server so it returns the real per-device IP via x-forwarded-for.\nDo you still want to ban it?")));
                    w.setPositiveButton(GameApp.T("حسنا", "OK"), (dd, ww) -> showBanReasonDialog(ctx, playerId, p, ip));
                    w.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    w.show();
                    return;
                }
                if (!ip.isEmpty() && ip.equals(selfIp)) {
                    AlertDialog.Builder w = new AlertDialog.Builder(ctx);
                    w.setTitle(GameApp.T("تحذير: نفس الـ IP", "Warning: Same IP"));
                    w.setView(U.msg(GameApp.T("هذا اللاعب يتصل من نفس IP جهازك. حسابك محمي بامتياز المطور/المدير، لكن كل الأجهزة على هذه الشبكة سيتم حظرها للعبة.\nهل تريد متابعة الحظر؟", "This player connects from the same IP as your device. Your account is protected as staff, but every device on this network will be banned from the game.\nDo you want to continue with the ban?")));
                    w.setPositiveButton(GameApp.T("متابعة المزيد", "Continue"), (dd, ww) -> showBanReasonDialog(ctx, playerId, p, ip));
                    w.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    w.show();
                    return;
                }
                showBanReasonDialog(ctx, playerId, p, ip);
            });
        }).start();
    }

    private static boolean isLikelyInternalIp(String ip) {
        if (ip == null || ip.isEmpty()) return true;
        if (ip.equals("::1") || ip.equals("127.0.0.1") || ip.startsWith("127.")) return true;
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("169.254.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) return true;
            } catch (Exception ignored) {}
        }
        if (ip.startsWith("100.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 64 && second <= 127) return true;
            } catch (Exception ignored) {}
        }
        if (ip.startsWith("fc") || ip.startsWith("fd") || ip.startsWith("fe80")) return true;
        if (ip.equals("unknown") || ip.equals("null")) return true;
        return false;
    }

    private static void showBanReasonDialog(final Context ctx, final String playerId, final JSONObject p, final String ip) {
        final EditText edReason = U.edit(ctx, GameApp.T("اكتب سبب حظر الجهاز...", "Write the device ban reason..."));
        LinearLayout lay = U.linear(ctx, true);
        lay.addView(edReason);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("حظر جهاز (IP) لـ", "Ban Device (IP) of") + p.optString("name", playerId) + "\n" + ip);
        b.setView(lay);
        b.setPositiveButton(GameApp.T("حظر الجهاز", "Ban Device"), (d, w) -> {
            final String reason = edReason.getText().toString().trim();
            new Thread(() -> {
                JSONObject rec = new JSONObject();
                try {
                    rec.put("reason", reason.isEmpty() ? GameApp.T("غير محدد", "Not specified") : reason);
                    rec.put("time", System.currentTimeMillis() / 1000);
                    rec.put("player", p.optString("name", playerId));
                } catch (Exception ignored) {}
                Db.put("device_bans/" + Db.encode(ip), rec);
                try {
                    JSONObject c = new JSONObject();
                    c.put("target", playerId);
                    c.put("type", "device_ban");
                    c.put("reason", reason);
                    Db.enqueueCommand(c);
                } catch (Exception ignored) {}
                UI.post(() -> U.alert(ctx, null, GameApp.T("تم حظر الجهاز (IP:" + ip + ")", "Device banned (IP:" + ip + ")"), GameApp.T("حسنا", "OK"), null));
            }).start();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void unbanDeviceByPlayer(final String playerId, final JSONObject p) {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("فك حظر الجهاز", "Unban Device"), GameApp.T("جاري البحث عن IP اللاعب...", "Looking up the player's IP..."), true);
        new Thread(() -> {
            JSONObject ipRec = Db.get("player_ips/" + Db.encode(playerId));
            UI.post(() -> {
                progress.dismiss();
                if (ipRec == null || ipRec.optString("ip", "").isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لم يتم تسجيل IP لهذا اللاعب.", "No IP has been recorded for this player."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final String ip = ipRec.optString("ip", "");
                new Thread(() -> {
                    Db.delete("device_bans/" + Db.encode(ip));
                    try {
                        JSONObject c = new JSONObject();
                        c.put("target", playerId);
                        c.put("type", "device_unban");
                        Db.enqueueCommand(c);
                    } catch (Exception ignored) {}
                    UI.post(() -> U.alert(ctx, null, GameApp.T("تم فك حظر الجهاز (IP:" + ip + ")", "Device unbanned (IP:" + ip + ")"), GameApp.T("حسنا", "OK"), null));
                }).start();
            });
        }).start();
    }

    private static void showPunishDetailsUI(final String playerId, final JSONObject p, final boolean isJail, final boolean isPermanent, final boolean isChat) {
        final Context ctx = ctx();
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(30, 30, 30, 30);
        final EditText edReason = U.edit(ctx, GameApp.T("اكتب سبب العقوبة...", "Write the punishment reason..."));
        lay.addView(edReason);
        final long[] MULT = {31536000L, 2592000L, 604800L, 86400L, 3600L, 60L, 1L};
        final String[] labels = {GameApp.T("سنوات", "Years"), GameApp.T("أشهر", "Months"), GameApp.T("أسابيع", "Weeks"), GameApp.T("أيام", "Days"), GameApp.T("ساعات", "Hours"), GameApp.T("دقائق", "Minutes"), GameApp.T("ثواني", "Seconds")};
        final EditText[] fEds = new EditText[labels.length];
        if (!isPermanent) {
            LinearLayout grid = U.linear(ctx, true);
            for (int i = 0; i < labels.length; i++) {
                final EditText ed = U.edit(ctx, labels[i]);
                ed.setInputType(InputType.TYPE_CLASS_NUMBER);
                fEds[i] = ed;
                grid.addView(ed);
            }
            lay.addView(grid);
            Button calcBtn = U.btn(ctx, GameApp.T("حساب المدة", "Calculate Duration"));
            calcBtn.setOnClickListener(v -> {
                long secs = 0;
                for (int i = 0; i < fEds.length; i++) {
                    long val = 0;
                    try { val = Long.parseLong(fEds[i].getText().toString().trim()); } catch (Exception e) {}
                    secs += val * MULT[i];
                }
                if (secs <= 0) {
                    U.alert(ctx, null, GameApp.T("المدة = دائم (0 ثانية)", "Duration = Permanent (0 seconds)"), GameApp.T("حسنا", "OK"), null);
                } else {
                    U.alert(ctx, null, GameApp.T("المدة المحسوبة:" + secs + "ثانية", "Calculated duration:" + secs + "seconds"), GameApp.T("حسنا", "OK"), null);
                }
            });
            lay.addView(calcBtn);
        }
        String targetWord = isChat ? GameApp.T("حظر دردشة", "Chat Ban") : (isJail ? GameApp.T("سجن", "Jail") : GameApp.T("حظر", "Ban"));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T(targetWord + (isPermanent ? "دائم" : "مؤقت") + "-" + p.optString("name", playerId), targetWord + (isPermanent ? "Permanent" : "Temporary") + "-" + p.optString("name", playerId)));
        b.setView(lay);
        b.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (d, w) -> {
            String reason = edReason.getText().toString().trim();
            if (reason.isEmpty()) {
                U.alert(ctx, null, GameApp.T("يجب كتابة السبب!", "You must write the reason!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            long seconds;
            if (isPermanent) {
                seconds = -1;
            } else {
                seconds = 0;
                for (int i = 0; i < fEds.length; i++) {
                    long val = 0;
                    try { val = Long.parseLong(fEds[i].getText().toString().trim()); } catch (Exception e) {}
                    seconds += val * MULT[i];
                }
                // All fields empty/0 => permanent punishment (-1).
                if (seconds == 0) seconds = -1;
            }
            try {
                if (isChat) {
                    p.put("chat_banned", true);
                    p.put("chat_ban_reason", reason);
                    p.put("chat_ban_until", seconds == -1 ? -1 : System.currentTimeMillis() / 1000 + seconds);
                } else if (isJail) {
                    p.put("is_jailed", true);
                    p.put("jail_reason", reason);
                    p.put("jail_until", seconds == -1 ? -1 : System.currentTimeMillis() / 1000 + seconds);
                } else {
                    p.put("is_banned", true);
                    p.put("ban_reason", reason);
                    p.put("banned_until", seconds == -1 ? -1 : System.currentTimeMillis() / 1000 + seconds);
                }
            } catch (Exception ignored) {}
            applyPunishment(playerId, p, GameApp.T("تم تطبيق العقوبة بنجاح", "Punishment applied successfully"));
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void showOldVersionBanUI(final String playerId, final JSONObject p) {
        final Context ctx = ctx();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("حظر نسخة قديمة", "Old-Version Ban"));
        final EditText ed = new EditText(ctx);
        ed.setText(GameApp.T("استخدام نسخة قديمة من اللعبة. يرجى تحديث اللعبة للمتابعة.", "Playing on an outdated version. Please update the game to continue."));
        b.setView(ed);
        b.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (d, w) -> {
            String reason = ed.getText().toString().trim();
            if (reason.isEmpty()) reason = GameApp.T("نسخة قديمة", "Old version");
            try {
                p.put("is_banned", true);
                p.put("banned_until", -1);
                p.put("ban_type", "old_version");
                p.put("ban_reason", reason);
            } catch (Exception ignored) {}
            applyPunishment(playerId, p, GameApp.T("تم حظر اللاعب لاستخدامه نسخة قديمة. سيُرفع الحظر تلقائياً بعد تحديثه.", "Player banned for an old version. The ban lifts automatically once they update."));
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void applyPunishment(final String playerId, final JSONObject p, final String successMsg) {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("تنفيذ", "Executing"), GameApp.T("جاري الحفظ...", "Saving..."), true);
        new Thread(() -> {
            // Update the server backup directly (as before). The result tells us
            // whether the write actually reached the server, so the admin no longer
            // sees a silent "nothing happened" when the connection fails.
            JSONObject res = Db.put(USER_PATH + Db.encode(playerId), p);
            // Store the sanction in a dedicated collection the player's own cloud
            // save can never overwrite, so ban/unban/jail buttons always take effect.
            syncSanctionRecord("bans", playerId, p, "is_banned", "banned_until", "ban_reason", "ban_type");
            syncSanctionRecord("jails", playerId, p, "is_jailed", "jail_until", "jail_reason", null);
            syncSanctionRecord("chat_bans", playerId, p, "chat_banned", "chat_ban_until", "chat_ban_reason", null);
            // Also enqueue a targeted update so the player's on-device file applies it.
            enqueueSetData(playerId, p);
            // Broadcast the FULL edited player data so every admin change (stats,
            // inventory, equipment, chests...) reaches the online device, not just
            // the scalar whitelist.
            enqueueSyncData(playerId, p);
            final boolean ok = res != null;
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                U.alert(ctx, null, ok ? successMsg
                        : GameApp.T("تعذر الحفظ على الخادم، تحقق من اتصالك بالإنترنت ثم حاول مجدداً.", "Could not save to the server. Check your internet connection and try again."),
                        GameApp.T("حسنا", "OK"), null);
            });
        }).start();
    }

    // Writes/clears a dedicated sanction record (bans/jails/chat_bans) for the
    // target so the sanction survives the player's own periodic cloud saves.
    private static void syncSanctionRecord(final String coll, final String playerId, final JSONObject p,
                                           final String flagKey, final String untilKey, final String reasonKey, final String typeKey) {
        try {
            // Version auto-bans are owned by the server version gate (they must lift
            // automatically after an update), so never mirror them here.
            if ("bans".equals(coll)
                    && (p.optBoolean("_version_ban", false)
                        || GameApp.isOldVersionBan(p.optString("ban_type", ""), p.optString("ban_reason", "")))) {
                return;
            }
            String path = coll + "/" + Db.encode(playerId);
            boolean active = p.optBoolean(flagKey, false);
            long until = p.optLong(untilKey, 0);
            long now = System.currentTimeMillis() / 1000;
            if (active && (until == -1 || until > now)) {
                JSONObject rec = new JSONObject();
                rec.put("active", true);
                rec.put("reason", p.optString(reasonKey, ""));
                rec.put("until", until == 0 ? -1 : until);
                if (typeKey != null) rec.put("type", p.optString(typeKey, ""));
                rec.put("ts", now);
                Db.put(path, rec);
            } else {
                // Write an explicit "cleared" marker instead of deleting, so the
                // target can tell a real unban from a failed/empty fetch.
                JSONObject rec = new JSONObject();
                rec.put("active", false);
                rec.put("until", 0);
                rec.put("ts", now);
                Db.put(path, rec);
            }
        } catch (Exception ignored) {}
    }

    // Build and push a set_data command carrying only the admin-controlled fields,
    // so the target device merges them into its local DB without losing live stats.
    private static void enqueueSetData(final String playerId, final JSONObject p) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("target", playerId);
            cmd.put("type", "set_data");
            if (p.has("name")) cmd.put("name", p.optString("name", ""));
            if (p.has("level")) cmd.put("level", p.optInt("level", 1));
            if (p.has("exp")) cmd.put("exp", p.optDouble("exp", 0));
            if (p.has("points")) cmd.put("points", p.optInt("points", 0));
            if (p.has("gold")) cmd.put("gold", p.optLong("gold", 0));
            if (p.has("bank_gold")) cmd.put("bank_gold", p.optLong("bank_gold", 0));
            if (p.has("crystals")) cmd.put("crystals", p.optLong("crystals", 0));
            if (p.has("diamonds")) cmd.put("diamonds", p.optLong("diamonds", 0));
            if (p.has("is_banned") && p.optBoolean("is_banned", false)) {
                JSONObject c = new JSONObject();
                c.put("target", playerId);
                c.put("type", "ban");
                c.put("banned_until", p.optLong("banned_until", -1));
                c.put("ban_reason", p.optString("ban_reason", "غير محدد"));
                if (p.has("ban_type") && !p.isNull("ban_type")) c.put("ban_type", p.optString("ban_type", ""));
                Db.enqueueCommand(c);
            } else if (p.has("is_banned") && !p.optBoolean("is_banned", false)) {
                JSONObject c = new JSONObject();
                c.put("target", playerId);
                c.put("type", "unban");
                Db.enqueueCommand(c);
            }
            if (p.has("is_jailed") && p.optBoolean("is_jailed", false)) {
                JSONObject c = new JSONObject();
                c.put("target", playerId);
                c.put("type", "jail");
                c.put("jail_until", p.optLong("jail_until", -1));
                c.put("jail_reason", p.optString("jail_reason", "غير محدد"));
                Db.enqueueCommand(c);
            } else if (p.has("is_jailed") && !p.optBoolean("is_jailed", false)) {
                JSONObject c = new JSONObject();
                c.put("target", playerId);
                c.put("type", "unjail");
                Db.enqueueCommand(c);
            }
            if (p.has("chat_banned") && p.optBoolean("chat_banned", false)) {
                JSONObject c = new JSONObject();
                c.put("target", playerId);
                c.put("type", "chat_ban");
                c.put("chat_ban_until", p.optLong("chat_ban_until", -1));
                c.put("chat_ban_reason", p.optString("chat_ban_reason", "غير محدد"));
                Db.enqueueCommand(c);
            } else if (p.has("chat_banned") && !p.optBoolean("chat_banned", false)) {
                JSONObject c = new JSONObject();
                c.put("target", playerId);
                c.put("type", "chat_unban");
                Db.enqueueCommand(c);
            }
            if (p.has("admin_data") || p.has("is_dev") || p.has("can_manage_shop")) {
                JSONObject c = new JSONObject();
                c.put("target", playerId);
                c.put("type", "set_data");
                if (p.has("admin_data")) c.put("admin_data", p.optBoolean("admin_data", false));
                if (p.has("is_dev")) c.put("is_dev", p.optBoolean("is_dev", false));
                if (p.has("can_manage_shop")) c.put("can_manage_shop", p.optBoolean("can_manage_shop", false));
                Db.enqueueCommand(c);
            }
            // Only enqueue the generic set_data if there's an actual scalar change.
            if (cmd.length() > 2) Db.enqueueCommand(cmd);
        } catch (Exception ignored) {}
    }

    private static void enqueueSyncData(final String playerId, final JSONObject p) {
        try {
            JSONObject c = new JSONObject();
            c.put("target", playerId);
            c.put("type", "sync_data");
            c.put("data", p);
            Db.enqueueCommand(c);
        } catch (Exception ignored) {}
    }

    private static void openGrantRolesUI() {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("منح الصلاحيات", "Grant Permissions"), GameApp.T("جاري تحميل قائمة اللاعبين...", "Loading the players list..."), true);
        new Thread(() -> {
            JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
            final JSONObject fAll = all;
            final JSONObject ips = Db.get("player_ips");
            UI.post(() -> {
                progress.dismiss();
                if (fAll == null) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبين في قاعدة البيانات", "There are no players in the database"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final List<String> names = new ArrayList<>();
                final List<String> ids = new ArrayList<>();
                Iterator<String> it = fAll.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject pd = fAll.optJSONObject(id);
                    if (pd == null) continue;
                    String role;
                    boolean aD = pd.optBoolean("admin_data", false);
                    boolean dD = pd.optBoolean("is_dev", false);
                    boolean sD = pd.optBoolean("can_manage_shop", false);
                    boolean eD = pd.optBoolean("can_edit_data", false);
                    if (dD) role = GameApp.T("[مطور]", "[Developer]");
                    else if (aD) role = GameApp.T("[مدير دردشة]", "[Chat Manager]");
                    else if (sD) role = GameApp.T("[مدير متجر]", "[Shop Manager]");
                    else if (eD) role = GameApp.T("[مدير بيانات]", "[Data Manager]");
                    else role = "";
                    names.add(ipLabel(ips, id, (pd.optString("name", GameApp.T("بدون اسم", "No name"))) + "(ID:" + id + ")" + role));
                    ids.add(id);
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اختر اللاعب", "Choose the Player"));
                b.setItems(names.toArray(new String[0]), (d2, idx) -> {
                    final String selId = ids.get(idx);
                    final JSONObject sel = fAll.optJSONObject(selId);
                    if (sel == null) return;
                    String[] opts = {GameApp.T("منح صلاحيات كاملة (مطور)", "Grant Full Permissions (Developer)"), GameApp.T("منح مدير دردشة فقط", "Grant Chat Manager Only"), GameApp.T("منح مدير متجر فقط", "Grant Shop Manager Only"), GameApp.T("منح مدير بيانات فقط", "Grant Data Manager Only"), GameApp.T("إزالة كل الصلاحيات", "Remove All Permissions")};
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setTitle(GameApp.T("صلاحيات:", "Permissions:") + sel.optString("name", selId));
                    b2.setItems(opts, (d3, i2) -> {
                        try {
                            if (i2 == 0) { sel.put("admin_data", true); sel.put("is_dev", true); sel.put("can_manage_shop", true); sel.put("can_edit_data", true); }
                            else if (i2 == 1) { sel.put("admin_data", true); sel.remove("is_dev"); sel.remove("can_manage_shop"); sel.remove("can_edit_data"); }
                            else if (i2 == 2) { sel.put("can_manage_shop", true); sel.remove("admin_data"); sel.remove("is_dev"); sel.remove("can_edit_data"); }
                            else if (i2 == 3) { sel.put("can_edit_data", true); sel.remove("admin_data"); sel.remove("is_dev"); sel.remove("can_manage_shop"); }
                            else { sel.remove("admin_data"); sel.remove("is_dev"); sel.remove("can_manage_shop"); sel.remove("can_edit_data"); }
                        } catch (Exception ignored) {}
                        applyPunishment(selId, sel, GameApp.T("تم تحديث صلاحيات اللاعب بنجاح", "Player permissions updated successfully"));
                    });
                    b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    b2.show();
                });
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    private static void openPlayerAdminUI() {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("مدير البيانات", "Data Manager"), GameApp.T("جاري تحميل قائمة اللاعبين...", "Loading the players list..."), true);
        new Thread(() -> {
            JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
            final JSONObject fAll = all;
            final JSONObject ips = Db.get("player_ips");
            UI.post(() -> {
                progress.dismiss();
                if (fAll == null) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبين في قاعدة البيانات", "There are no players in the database"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final List<String> names = new ArrayList<>();
                final List<String> ids = new ArrayList<>();
                Iterator<String> it = fAll.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject pd = fAll.optJSONObject(id);
                    if (pd == null) continue;
                    names.add(ipLabel(ips, id, (pd.optString("name", GameApp.T("بدون اسم", "No name"))) + "(ID:" + id + ")"));
                    ids.add(id);
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اختر لاعباً للتعديل", "Choose a Player to Edit"));
                b.setItems(names.toArray(new String[0]), (d2, idx) -> {
                    JSONObject sel = fAll.optJSONObject(ids.get(idx));
                    if (sel != null) showPlayerEditPanel(ids.get(idx), sel);
                });
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    private static void showPlayerEditPanel(final String id, final JSONObject p) {
        final Context ctx = ctx();
        LinearLayout layout = U.linear(ctx, true);
        layout.setPadding(30, 30, 30, 30);
        ScrollView scroll = new ScrollView(ctx);
        final LinearLayout container = U.linear(ctx, true);
        scroll.addView(container);
        layout.addView(scroll);

        addStatEdit(container, p, GameApp.T("المستوى", "Level"), "level", false);
        addStatEdit(container, p, GameApp.T("الذهب", "Gold"), "gold", false);
        addStatEdit(container, p, GameApp.T("ذهب البنك", "Bank Gold"), "bank_gold", false);
        addStatEdit(container, p, GameApp.T("الألماس", "Diamonds"), "diamonds", false);
        addStatEdit(container, p, GameApp.T("الكريستال", "Crystals"), "crystals", false);
        addStatEdit(container, p, GameApp.T("الصحة (HP)", "Health (HP)"), "max_hp", true);
        addStatEdit(container, p, GameApp.T("الهجوم (القوة)", "Attack (Strength)"), "strength", true);
        addStatEdit(container, p, GameApp.T("الدفاع (التحمل)", "Defense (Endurance)"), "endurance", true);
        addStatEdit(container, p, GameApp.T("الرشاقة", "Agility"), "agility", true);
        addStatEdit(container, p, GameApp.T("الحظ", "Luck"), "luck", true);

        Button btnRename = U.btn(ctx, GameApp.T("تغيير اسم اللاعب", "Change Player Name"));
        btnRename.setBackgroundColor(0xFF3F51B5);
        btnRename.setTextColor(Color.WHITE);
        btnRename.setOnClickListener(v -> {
            final EditText ed = U.edit(ctx, GameApp.T("الاسم الجديد...", "New name..."));
            LinearLayout lay = U.linear(ctx, true);
            lay.addView(ed);
            AlertDialog.Builder nb = new AlertDialog.Builder(ctx);
            nb.setTitle(GameApp.T("تغيير اسم:", "Change name of:") + p.optString("name", id));
            nb.setView(lay);
            nb.setPositiveButton(GameApp.T("تغيير", "Change"), (d, w) -> {
                String nn = ed.getText().toString().trim();
                if (nn.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("اكتب الاسم الجديد", "Write the new name"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                try {
                    p.put("name", nn);
                } catch (Exception ignored) {}
                // Rename-specific command so the player's device applies it.
                try {
                    JSONObject c = new JSONObject();
                    c.put("target", id);
                    c.put("type", "rename");
                    c.put("new_name", nn);
                    Db.enqueueCommand(c);
                } catch (Exception ignored) {}
                applyPunishment(id, p, GameApp.T("تم تغيير اسم اللاعب إلى:" + nn, "Player name changed to:" + nn));
            });
            nb.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            nb.show();
        });
        container.addView(btnRename);

        Button btnChests = U.btn(ctx, GameApp.T("إضافة صناديق", "Add Chests"));
        btnChests.setBackgroundColor(0xFF9C27B0);
        btnChests.setTextColor(Color.WHITE);
        btnChests.setOnClickListener(v -> {
            String[] chestTypes = {"عادي", "غير عادي", "نادر", "ملحمي", "أسطوري", "خرافي", "خرافي مطلق"};
            int[] reqs = {10, 15, 20, 35, 50, 70, 100};
            AlertDialog.Builder c = new AlertDialog.Builder(ctx);
            c.setTitle(GameApp.T("اختر نوع الصندوق", "Choose Chest Type"));
            c.setItems(chestTypes, (d, i) -> {
                try {
                    JSONArray chests = p.optJSONArray("locked_chests");
                    if (chests == null) chests = new JSONArray();
                    JSONObject nc = new JSONObject();
                    nc.put("type", chestTypes[i]);
                    nc.put("req", reqs[i]);
                    nc.put("done", 0);
                    chests.put(nc);
                    p.put("locked_chests", chests);
                    applyPunishment(id, p, GameApp.T("تم إضافة صندوق", "Chest added:") + chestTypes[i]);
                } catch (Exception ignored) {}
            });
            c.show();
        });
        container.addView(btnChests);

        Button btnInv = U.btn(ctx, GameApp.T("إدارة حقيبة اللاعب", "Manage Player Bag"));
        btnInv.setBackgroundColor(0xFFFF9800);
        btnInv.setTextColor(Color.WHITE);
        btnInv.setOnClickListener(v -> {
            JSONArray inv = p.optJSONArray("inventory");
            JSONArray eq = p.optJSONArray("equipped");
            if (inv == null || inv.length() == 0) {
                U.alert(ctx, null, GameApp.T("الحقيبة فارغة", "The bag is empty"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            final List<String> invNames = new ArrayList<>();
            final List<Integer> eqIdx = new ArrayList<>();
            for (int i = 0; i < inv.length(); i++) {
                JSONObject item = inv.optJSONObject(i);
                String nm = item != null ? item.optString("name", "?") : "?";
                boolean isEq = false;
                if (eq != null) {
                    for (int j = 0; j < eq.length(); j++) {
                        if (eq.optString(j).equals(nm)) { isEq = true; break; }
                    }
                }
                eqIdx.add(isEq ? -1 : -2);
                invNames.add(bagItemSummary(item) + (isEq ? "\n" + GameApp.T("[مرتدي]", "[Equipped]") : ""));
            }
            AlertDialog.Builder ib = new AlertDialog.Builder(ctx);
            ib.setTitle(GameApp.T("حقيبة اللاعب", "Player Bag"));
            ib.setItems(invNames.toArray(new String[0]), (d, i) -> {
                final JSONObject item = inv.optJSONObject(i);
                final String itemName = item != null ? item.optString("name", "?") : "?";
                final int idxInInv = i;
                String[] opts;
                boolean isEq = false;
                int eqKey = -1;
                if (eq != null) {
                    for (int j = 0; j < eq.length(); j++) {
                        if (eq.optString(j).equals(itemName)) { isEq = true; eqKey = j; break; }
                    }
                }
                List<String> optsList = new ArrayList<>();
                if (isEq) optsList.add(GameApp.T("خلع العنصر", "Unequip Item"));
                optsList.add(GameApp.T("بيع العنصر (للاعب)", "Sell Item (to Player)"));
                optsList.add(GameApp.T("حذف العنصر نهائياً", "Delete Item Permanently"));
                opts = optsList.toArray(new String[0]);
                final boolean fIsEq = isEq;
                final int fEqKey = eqKey;
                AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                b2.setTitle(itemName);
                b2.setItems(opts, (d2, i2) -> {
                    String choice = opts[i2];
                    try {
                        if (choice.equals(GameApp.T("خلع العنصر", "Unequip Item"))) {
                            if (fEqKey >= 0) {
                                JSONArray neq = new JSONArray();
                                for (int j = 0; j < eq.length(); j++) if (j != fEqKey) neq.put(eq.optString(j));
                                p.put("equipped", neq);
                                if (item != null) {
                                    JSONObject boosts = item.optJSONObject("boosts");
                                    if (boosts != null) {
                                        JSONObject stats = p.optJSONObject("stats");
                                        if (stats == null) stats = new JSONObject();
                                        stats.put("strength", stats.optDouble("strength", 0) - boosts.optDouble("str", 0));
                                        stats.put("max_hp", stats.optDouble("max_hp", 0) - boosts.optDouble("hp", 0));
                                        stats.put("agility", stats.optDouble("agility", 0) - boosts.optDouble("agi", 0));
                                        stats.put("luck", stats.optDouble("luck", 0) - boosts.optDouble("lck", 0));
                                        stats.put("endurance", stats.optDouble("endurance", 0) - boosts.optDouble("end", 0));
                                        p.put("stats", stats);
                                    }
                                }
                            }
                            applyPunishment(id, p, GameApp.T("تم خلع العنصر", "Item unequipped"));
                        } else if (choice.equals(GameApp.T("بيع العنصر (للاعب)", "Sell Item (to Player)"))) {
                            long price = (long) Math.floor((item != null ? item.optLong("price", 0) : 0) / 2.0);
                            p.put("gold", p.optLong("gold", 0) + price);
                            JSONArray ni = new JSONArray();
                            for (int j = 0; j < inv.length(); j++) if (j != idxInInv) ni.put(inv.optJSONObject(j));
                            p.put("inventory", ni);
                            if (fIsEq && fEqKey >= 0) {
                                JSONArray neq = new JSONArray();
                                for (int j = 0; j < eq.length(); j++) if (j != fEqKey) neq.put(eq.optString(j));
                                p.put("equipped", neq);
                            }
                            applyPunishment(id, p, GameApp.T("تم بيع العنصر وإضافة" + price + "ذهب للاعب", "Item sold and" + price + "gold added to the player"));
                        } else {
                            JSONArray ni = new JSONArray();
                            for (int j = 0; j < inv.length(); j++) if (j != idxInInv) ni.put(inv.optJSONObject(j));
                            p.put("inventory", ni);
                            if (fIsEq && fEqKey >= 0) {
                                JSONArray neq = new JSONArray();
                                for (int j = 0; j < eq.length(); j++) if (j != fEqKey) neq.put(eq.optString(j));
                                p.put("equipped", neq);
                            }
                            applyPunishment(id, p, GameApp.T("تم حذف العنصر", "Item deleted"));
                        }
                    } catch (Exception ignored) {}
                });
                b2.show();
            });
            ib.show();
        });
        container.addView(btnInv);

        Button btnSave = U.btn(ctx, GameApp.T("حفظ التغييرات", "Save Changes"));
        btnSave.setBackgroundColor(0xFF2196F3);
        btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v -> applyPunishment(id, p, GameApp.T("تم حفظ التغييرات", "Changes saved")));
        container.addView(btnSave);

        Button btnDel = U.btn(ctx, GameApp.T("حذف الحساب نهائياً", "Delete Account Permanently"));
        btnDel.setBackgroundColor(0xFFD32F2F);
        btnDel.setTextColor(Color.WHITE);
        btnDel.setOnClickListener(v -> {
            AlertDialog.Builder conf = new AlertDialog.Builder(ctx);
            conf.setTitle(GameApp.T("تأكيد الحذف", "Confirm Deletion"));
            conf.setView(U.msg(GameApp.T("هل أنت متأكد من حذف حساب هذا اللاعب نهائياً؟ لا يمكن التراجع عن هذا الإجراء.", "Are you sure you want to permanently delete this player's account? This action cannot be undone.")));
            conf.setPositiveButton(GameApp.T("حذف نهائي", "Delete Permanently"), (d, w) -> {
                progressDelete = U.progress(ctx, GameApp.T("حذف الحساب", "Deleting Account"), GameApp.T("جاري الحذف...", "Deleting..."), true);
                new Thread(() -> {
                    JSONObject del = Db.delete(USER_PATH + Db.encode(id));
                    try {
                        JSONObject c = new JSONObject();
                        c.put("target", id);
                        c.put("type", "delete_account");
                        Db.enqueueCommand(c);
                    } catch (Exception ignored) {}
                    UI.post(() -> {
                        if (progressDelete != null) { try { progressDelete.dismiss(); } catch (Exception ignored) {} }
                        if (del != null) U.alert(ctx, null, GameApp.T("فشل حذف الحساب. حاول مرة أخرى", "Failed to delete account. Try again"), GameApp.T("حسنا", "OK"), null);
                        else U.alert(ctx, null, GameApp.T("تم حذف الحساب نهائياً", "Account deleted permanently"), GameApp.T("حسنا", "OK"), null);
                    });
                }).start();
            });
            conf.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            conf.show();
        });
        container.addView(btnDel);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("تعديل بيانات:", "Edit Data:") + p.optString("name", id));
        b.setView(layout);
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void addStatEdit(final LinearLayout container, final JSONObject p, final String label, final String key, final boolean nested) {
        final Context ctx = ctx();
        LinearLayout lay = U.linear(ctx, false);
        lay.setGravity(Gravity.CENTER_VERTICAL);
        lay.setPadding(0, 10, 0, 10);
        TextView txt = U.text(ctx, label + ":", 14, Color.WHITE, false);
        txt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final Button btn = U.btn(ctx, nested ? String.valueOf(getNested(p, key)) : String.valueOf(p.optLong(key, 0)));
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        btn.setOnClickListener(v -> {
            final EditText ed = U.edit(ctx, GameApp.T("قيمة جديدة...", "New value..."));
            ed.setInputType(InputType.TYPE_CLASS_NUMBER);
            ed.setText(nested ? String.valueOf(getNested(p, key)) : String.valueOf(p.optLong(key, 0)));
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("تعديل", "Edit") + label);
            b.setView(ed);
            b.setPositiveButton(GameApp.T("تحديث", "Update"), (d, w) -> {
                long val = 0;
                try { val = Long.parseLong(ed.getText().toString().trim()); } catch (Exception e) {}
                try {
                    if (nested) {
                        JSONObject stats = p.optJSONObject("stats");
                        if (stats == null) stats = new JSONObject();
                        stats.put(key, val);
                        p.put("stats", stats);
                    } else {
                        p.put(key, val);
                    }
                } catch (Exception ignored) {}
                btn.setText(String.valueOf(val));
            });
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        });
        lay.addView(txt);
        lay.addView(btn);
        container.addView(lay);
    }

    private static double getNested(JSONObject p, String key) {
        JSONObject stats = p.optJSONObject("stats");
        if (stats == null) return 0;
        return stats.optDouble(key, 0);
    }

    private static final String[] CHEST_TYPES_G = {"عادي", "غير عادي", "نادر", "ملحمي", "أسطوري", "خرافي", "خرافي مطلق"};
    private static final int[] CHEST_REQS_G = {10, 15, 20, 35, 50, 70, 100};

    private static int chestReqForType(String t) {
        for (int i = 0; i < CHEST_TYPES_G.length; i++) {
            if (CHEST_TYPES_G[i].equals(t)) return CHEST_REQS_G[i];
        }
        return 10;
    }

    private static void openSendGiftUI() {
        final Context ctx = ctx();
        String[] recipients = {
                GameApp.T("لاعب محدد (اسم أو ID)", "Specific player (name or ID)"),
                GameApp.T("جميع اللاعبين المتصلين فوراً", "All connected players instantly")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إرسال هدية", "Send a Gift"));
        b.setItems(recipients, (d, i) -> {
            if (i == 0) {
                final EditText ed = U.edit(ctx, GameApp.T("اكتب اسم اللاعب أو ID...", "Type the player name or ID..."));
                AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                b2.setTitle(GameApp.T("المرسل إليه", "Recipient"));
                b2.setView(ed);
                b2.setPositiveButton(GameApp.T("متابعة", "Continue"), (d2, w) -> {
                    String target = ed.getText().toString().trim();
                    if (target.isEmpty()) return;
                    chooseGift(ctx, target, false);
                });
                b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b2.show();
            } else {
                chooseGift(ctx, null, true);
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void chooseGift(final Context ctx, final String target, final boolean allConnected) {
        String[] types = {
                GameApp.T("ذهب", "Gold"),
                GameApp.T("ألماس", "Diamonds"),
                GameApp.T("كريستال", "Crystals"),
                GameApp.T("صندوق", "Chest"),
                GameApp.T("عنصر من المتجر", "Item from shop")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("اختر نوع الهدية", "Choose Gift Type"));
        b.setItems(types, (d, i) -> {
            if (i == 4) {
                chooseShopItem(ctx, target, allConnected);
                return;
            }
            if (i == 3) {
                AlertDialog.Builder cb = new AlertDialog.Builder(ctx);
                cb.setTitle(GameApp.T("اختر نوع الصندوق", "Choose Chest Type"));
                cb.setItems(CHEST_TYPES_G, (d2, ci) -> sendGift(target, allConnected, "chest", 0, CHEST_TYPES_G[ci], null));
                cb.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                cb.show();
                return;
            }
            final EditText ed = U.edit(ctx, GameApp.T("اكتب المبلغ...", "Type the amount..."));
            ed.setInputType(InputType.TYPE_CLASS_NUMBER);
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(types[i]);
            b2.setView(ed);
            b2.setPositiveButton(GameApp.T("إرسال", "Send"), (d2, w) -> {
                long amt = 0;
                try { amt = Long.parseLong(ed.getText().toString().trim()); } catch (Exception e) {}
                if (amt > 0) {
                    String giftType = i == 0 ? "gold" : (i == 1 ? "diamond" : "crystal");
                    sendGift(target, allConnected, giftType, amt, null, null);
                }
            });
            b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b2.show();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void chooseShopItem(final Context ctx, final String target, final boolean allConnected) {
        final ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل المتجر", "Loading Shop"), GameApp.T("جاري تحميل العناصر...", "Loading items..."), true);
        new Thread(() -> {
            JSONObject shopData = Db.get(SHOPS_PATH);
            UI.post(() -> {
                progress.dismiss();
                if (shopData == null) {
                    U.alert(ctx, null, GameApp.T("لا توجد بيانات للمتجر", "No shop data found"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final List<String> itemNames = new ArrayList<>();
                final List<JSONObject> itemObjs = new ArrayList<>();
                Iterator<String> kit = shopData.keys();
                while (kit.hasNext()) {
                    String arrName = kit.next();
                    JSONArray arr = shopData.optJSONArray(arrName);
                    if (arr == null) continue;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null && !o.optString("name", "").isEmpty()) {
                            itemNames.add(o.optString("name") + "(" + arrName + ")");
                            itemObjs.add(o);
                        }
                    }
                }
                if (itemNames.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا توجد عناصر في المتجر", "No items in shop"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اختر عنصراً", "Choose an Item"));
                b.setItems(itemNames.toArray(new String[0]), (d, i) -> {
                    JSONObject src = itemObjs.get(i);
                    JSONObject copy = new JSONObject();
                    Iterator<String> fk = src.keys();
                    while (fk.hasNext()) {
                        String k = fk.next();
                        try { copy.put(k, src.get(k)); } catch (Exception ignored) {}
                    }
                    try {
                        copy.put("name", src.optString("name", "?") + "");
                    } catch (Exception ignored) {}
                    sendGift(target, allConnected, "item", 0, null, copy);
                });
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    private static void sendGift(final String target, final boolean allConnected, final String type, final long amt, final String chestType, final JSONObject shopItem) {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("إرسال الهدية", "Sending Gift"), GameApp.T("جاري الإرسال...", "Sending..."), true);
        new Thread(() -> {
            String result;
            if (allConnected) {
                JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
                if (all == null) {
                    result = GameApp.T("فشل: لا يوجد لاعبين في قاعدة البيانات", "Failed: no players in the database");
                } else {
                    int sent = 0;
                    long now = System.currentTimeMillis() / 1000;
                    Iterator<String> it = all.keys();
                    while (it.hasNext()) {
                        String id = it.next();
                        JSONObject pd = all.optJSONObject(id);
                        if (pd == null) continue;
                        long lastOnline = pd.optLong("last_online", 0);
                        if (lastOnline <= 0 || now - lastOnline > 120) continue;
                        if (applyGiftToJSON(pd, type, amt, chestType, shopItem)) {
                            Db.put(USER_PATH + Db.encode(id), pd);
                            pushGiftInbox(id, type, amt, chestType, shopItem);
                            sent++;
                        }
                    }
                    result = GameApp.T("تم إرسال الهدية إلى" + sent + "لاعب متصل", "Gift sent to" + sent + "connected players");
                }
            } else {
                String id = target;
                JSONObject pd = Db.get(USER_PATH + Db.encode(id));
                if (pd == null) {
                    JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
                    if (all != null) {
                        Iterator<String> it = all.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            JSONObject o = all.optJSONObject(k);
                            if (o != null && o.optString("name", "").equalsIgnoreCase(target)) { pd = o; id = k; break; }
                        }
                    }
                }
                if (pd == null) {
                    result = GameApp.T("اللاعب غير موجود", "Player not found");
                } else if (applyGiftToJSON(pd, type, amt, chestType, shopItem)) {
                    Db.put(USER_PATH + Db.encode(id), pd);
                    pushGiftInbox(id, type, amt, chestType, shopItem);
                    result = GameApp.T("تم إرسال الهدية إلى" + pd.optString("name", id), "Gift sent to" + pd.optString("name", id));
                } else {
                    result = GameApp.T("فشل إرسال الهدية", "Failed to send the gift");
                }
            }
            final String fResult = result;
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                U.alert(ctx, null, fResult, GameApp.T("حسنا", "OK"), null);
            });
        }).start();
    }

    private static boolean applyGiftToJSON(JSONObject pd, String type, long amt, String chestType, JSONObject shopItem) {
        try {
            if (type.equals("gold")) {
                pd.put("gold", pd.optLong("gold", 0) + amt);
            } else if (type.equals("diamond")) {
                pd.put("diamonds", pd.optLong("diamonds", 0) + amt);
            } else if (type.equals("crystal")) {
                pd.put("crystals", pd.optLong("crystals", 0) + amt);
            } else if (type.equals("chest")) {
                JSONArray chests = pd.optJSONArray("locked_chests");
                if (chests == null) chests = new JSONArray();
                JSONObject nc = new JSONObject();
                nc.put("type", chestType);
                nc.put("req", chestReqForType(chestType));
                nc.put("done", 0);
                chests.put(nc);
                pd.put("locked_chests", chests);
            } else if (type.equals("item")) {
                JSONArray inv = pd.optJSONArray("inventory");
                if (inv == null) inv = new JSONArray();
                inv.put(shopItem);
                pd.put("inventory", inv);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void pushGiftInbox(String id, String type, long amt, String chestType, JSONObject shopItem) {
        try {
            JSONObject rec = Db.get(USER_PATH + Db.encode(id));
            if (rec == null) return;
            JSONArray inbox = rec.optJSONArray("inbox");
            if (inbox == null) inbox = new JSONArray();
            JSONObject n = new JSONObject();
            n.put("type", type);
            n.put("from_name", GameApp.player == null ? GameApp.T("الإدارة", "Administration") : GameApp.player.name);
            n.put("from_id", GameApp.player == null ? "admin" : GameApp.player.username);
            if (type.equals("gold") || type.equals("diamond") || type.equals("crystal")) n.put("amt", amt);
            if (type.equals("chest")) {
                n.put("chest_type", chestType);
                n.put("req", chestReqForType(chestType));
                n.put("done", 0);
                n.put("min_lvl", 1);
            }
            if (type.equals("item")) {
                n.put("item_name", shopItem != null ? shopItem.optString("name", "?") : "?");
                n.put("item_json", shopItem);
            }
            inbox.put(n);
            JSONObject patch = new JSONObject();
            patch.put("inbox", inbox);
            Db.patch(USER_PATH + Db.encode(id), patch);
        } catch (Exception ignored) {}
    }

    private static void openShopAdminUI() {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("مدير المتجر", "Shop Manager"), GameApp.T("جاري تحميل البيانات...", "Loading the data..."), true);
        new Thread(() -> {
            JSONObject shopData = Db.get(SHOPS_PATH);
            UI.post(() -> {
                progress.dismiss();
                showShopAdminMenu(ctx, shopData == null ? new JSONObject() : shopData);
            });
        }).start();
    }

    private static void showShopAdminMenu(final Context ctx, final JSONObject fShop) {
        final List<String> opts = new ArrayList<>();
        final List<String> arrNames = new ArrayList<>();
        final List<Integer> arrIdx = new ArrayList<>();
        opts.add(GameApp.T("إضافة عنصر جديد", "Add New Item"));
        String[] keys = {"equip", "crystals", "pets"};
        String[] prefixes = {"", "", ""};
        for (int k = 0; k < keys.length; k++) {
            JSONArray arr = fShop.optJSONArray(keys[k]);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.optJSONObject(i);
                    if (item != null) {
                        opts.add(prefixes[k] + "" + item.optString("name", GameApp.T("بدون اسم", "No name")) + "(" + keys[k] + ")");
                        arrNames.add(keys[k]);
                        arrIdx.add(i);
                    }
                }
            }
        }
        if (opts.size() == 1) opts.add(GameApp.T("المتجر فارغ حالياً", "The shop is currently empty"));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إدارة المتجر (" + (opts.size() - 1) + "عنصر)", "Shop Management (" + (opts.size() - 1) + "items)"));
        b.setItems(opts.toArray(new String[0]), (d, i) -> {
            if (i == 0) {
                addShopItemUI(ctx, fShop);
            } else if (i == 1 && opts.size() == 2 && opts.get(1).equals(GameApp.T("المتجر فارغ حالياً", "The shop is currently empty"))) {
                U.alert(ctx, null, GameApp.T("استخدم \"إضافة عنصر جديد\" لبدء ملء المتجر.", "Use \"Add New Item\" to start filling the shop."), GameApp.T("حسنا", "OK"), null);
            } else {
                final String key = arrNames.get(i - 1);
                final int idx = arrIdx.get(i - 1);
                final JSONArray arr = fShop.optJSONArray(key);
                final JSONObject item = (arr != null && idx < arr.length()) ? arr.optJSONObject(idx) : null;
                if (item == null) return;
                List<String> aoptsL = new ArrayList<>();
                aoptsL.add(GameApp.T("تعديل السعر", "Edit Price"));
                aoptsL.add(GameApp.T("تعديل الوصف", "Edit Description"));
                if (item.optBoolean("is_potion", false)) aoptsL.add(GameApp.T("تعديل الجرعة (النوع/المدة)", "Edit Potion (type/duration)"));
                aoptsL.add(GameApp.T("حذف العنصر", "Delete Item"));
                final String[] aopts = aoptsL.toArray(new String[0]);
                AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                b2.setTitle(item.optString("name", GameApp.T("عنصر", "Item")));
                b2.setItems(aopts, (d2, i2) -> {
                    if (i2 == 0) {
                        final EditText ed = U.edit(ctx, GameApp.T("السعر الجديد...", "New price..."));
                        ed.setInputType(InputType.TYPE_CLASS_NUMBER);
                        ed.setText(String.valueOf(item.optLong("price", 0)));
                        AlertDialog.Builder b3 = new AlertDialog.Builder(ctx);
                        b3.setTitle(GameApp.T("تعديل السعر", "Edit Price"));
                        b3.setView(ed);
                        b3.setPositiveButton(GameApp.T("حفظ", "Save"), (d3, w3) -> {
                            long np = 0;
                            try { np = Long.parseLong(ed.getText().toString().trim()); } catch (Exception e) {}
                            try { item.put("price", np); } catch (Exception ignored) {}
                            saveShop(fShop, GameApp.T("تم تحديث السعر", "Price updated"));
                        });
                        b3.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                        b3.show();
                    } else if (i2 == 1) {
                        final EditText ed = U.edit(ctx, GameApp.T("الوصف الجديد...", "New description..."));
                        ed.setText(item.optString("desc", ""));
                        AlertDialog.Builder b3 = new AlertDialog.Builder(ctx);
                        b3.setTitle(GameApp.T("تعديل الوصف", "Edit Description"));
                        b3.setView(ed);
                        b3.setPositiveButton(GameApp.T("حفظ", "Save"), (d3, w3) -> {
                            try { item.put("desc", ed.getText().toString().trim()); } catch (Exception ignored) {}
                            saveShop(fShop, GameApp.T("تم تحديث الوصف", "Description updated"));
                        });
                        b3.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                        b3.show();
                    } else if (i2 == 2 && item.optBoolean("is_potion", false)) {
                        editPotionItem(ctx, item, arr, idx, key, fShop);
                    } else {
                        JSONArray ne = new JSONArray();
                        for (int j = 0; j < arr.length(); j++) if (j != idx) ne.put(arr.optJSONObject(j));
                        try { fShop.put(key, ne); } catch (Exception ignored) {}
                        saveShop(fShop, GameApp.T("تم حذف العنصر بنجاح", "Item deleted successfully"));
                    }
                });
                b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b2.show();
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void saveShop(final JSONObject fShop, final String msg) {
        final Context ctx = ctx();
        new Thread(() -> Db.put(SHOPS_PATH, fShop)).start();
        U.alert(ctx, null, msg, GameApp.T("حسنا", "OK"), null);
    }

    private static void addShopItemUI(final Context ctx, final JSONObject fShop) {
        String[] cats = {GameApp.T("سلاح", "Weapon"), GameApp.T("دروع", "Armors"), GameApp.T("أدوات مساعدة", "Support Items"), GameApp.T("مخطوطة", "Scroll"), GameApp.T("جرعة", "Potion"), GameApp.T("مرافقين", "Companions")};
        final String[] cCats = {"سلاح", "دروع", "أدوات مساعدة", "مخطوطة", "جرعة", "مرافق"};
        final String[] cKeys = {"equip", "equip", "equip", "equip", "crystals", "pets"};
        final boolean[] cScroll = {false, false, false, true, false, false};
        final boolean[] cPotion = {false, false, false, false, true, false};
        AlertDialog.Builder c = new AlertDialog.Builder(ctx);
        c.setTitle(GameApp.T("اختر نوع العنصر", "Choose Item Type"));
        c.setItems(cats, (d, ci) -> {
            final String key = cKeys[ci];
            final String cat = cCats[ci];
            final boolean isScroll = cScroll[ci];
            final boolean isPotion = cPotion[ci];
            LinearLayout lay = U.linear(ctx, true);
            lay.setPadding(30, 30, 30, 30);
            final EditText edName = U.edit(ctx, GameApp.T("اسم العنصر...", "Item name..."));
            final EditText edPrice = U.edit(ctx, GameApp.T("السعر...", "Price..."));
            edPrice.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText edDesc = U.edit(ctx, GameApp.T("الوصف...", "Description..."));
            lay.addView(edName);
            lay.addView(edPrice);
            lay.addView(edDesc);
            final EditText edStr = U.edit(ctx, GameApp.T("زيادة القوة (الهجوم)...", "Increase strength (attack)..."));
            edStr.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText edEnd = U.edit(ctx, GameApp.T("زيادة الدفاع...", "Increase defense..."));
            edEnd.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText edHp = U.edit(ctx, GameApp.T("زيادة الصحة...", "Increase health..."));
            edHp.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText edAgi = U.edit(ctx, GameApp.T("زيادة الرشاقة...", "Increase agility..."));
            edAgi.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText edLck = U.edit(ctx, GameApp.T("زيادة الحظ...", "Increase luck..."));
            edLck.setInputType(InputType.TYPE_CLASS_NUMBER);
            lay.addView(edStr);
            lay.addView(edEnd);
            lay.addView(edHp);
            lay.addView(edAgi);
            lay.addView(edLck);
            final EditText edDurMin = U.edit(ctx, GameApp.T("مدة الجرعة بالدقائق (0 = معارك)...", "Potion duration in minutes (0 = battles)..."));
            edDurMin.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText edDurBat = U.edit(ctx, GameApp.T("مدة الجرعة بعدد المعارك (افتراضي 10)...", "Potion duration in battles (default 10)..."));
            edDurBat.setInputType(InputType.TYPE_CLASS_NUMBER);
            TextView hintDur = U.text(ctx, GameApp.T("للجرع فقط: حدد إما الدقائق أو المعارك. إذا وضعت دقائق > 0 ستُستخدم الدقائق.", "Potions only: set either minutes or battles. If minutes is greater than 0, minutes will be used."), 12, Color.WHITE, false);
            lay.addView(hintDur);
            lay.addView(edDurMin);
            lay.addView(edDurBat);
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("إضافة عنصر (" + cat + ")", "Add Item (" + cat + ")"));
            b.setView(lay);
            b.setPositiveButton(GameApp.T("إضافة", "Add"), (d2, w) -> {
                String name = edName.getText().toString().trim();
                long price = 0;
                try { price = Long.parseLong(edPrice.getText().toString().trim()); } catch (Exception ignored) {}
                if (name.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("اكتب اسم العنصر!", "Write the item name!"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                double str = parseStat(edStr), end = parseStat(edEnd), hp = parseStat(edHp), agi = parseStat(edAgi), lck = parseStat(edLck);
                JSONObject it = new JSONObject();
                try {
                    it.put("name", name);
                    it.put("price", price);
                    it.put("desc", edDesc.getText().toString().trim());
                    it.put("category", cat);
                    it.put("level", 1);
                    it.put("rarity", "خرافي");
                    JSONObject boosts = new JSONObject();
                    boosts.put("str", str);
                    boosts.put("end", end);
                    boosts.put("hp", hp);
                    boosts.put("agi", agi);
                    boosts.put("lck", lck);
                    it.put("boosts", boosts);
                    if (isScroll) {
                        it.put("is_scroll", true);
                        JSONObject st = new JSONObject();
                        st.put("strength", str);
                        st.put("endurance", end);
                        st.put("maxHp", hp);
                        st.put("luck", lck);
                        it.put("stats", st);
                    }
                    if (isPotion) {
                        it.put("is_potion", true);
                        String type = "str";
                        double val = str;
                        if (end >= val) { type = "end"; val = end; }
                        if (hp >= val) { type = "hp"; val = hp; }
                        if (agi >= val) { type = "agi"; val = agi; }
                        if (lck >= val) { type = "luck"; val = lck; }
                        it.put("type", type);
                        it.put("val", val);
                        long durMin = (long) parseStat(edDurMin);
                        int durBat = (int) parseStat(edDurBat);
                        if (durMin > 0) it.put("duration_min", durMin);
                        else if (durBat > 0) it.put("duration_battles", durBat);
                    }
                    if (!isScroll && !isPotion) it.put("is_royal", true);
                } catch (Exception ignored) {}
                JSONArray arr = fShop.optJSONArray(key);
                if (arr == null) arr = new JSONArray();
                arr.put(it);
                try { fShop.put(key, arr); } catch (Exception ignored) {}
                saveShop(fShop, GameApp.T("تم إضافة العنصر إلى المتجر", "Item added to the shop"));
            });
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        });
        c.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        c.show();
    }

    private static double parseStat(final EditText ed) {
        try {
            return Double.parseDouble(ed.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static void editPotionItem(final Context ctx, final JSONObject item, final JSONArray arr, final int idx, final String key, final JSONObject fShop) {
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(30, 30, 30, 30);
        final EditText edName = U.edit(ctx, GameApp.T("اسم الجرعة...", "Potion name..."));
        edName.setText(item.optString("name", ""));
        final EditText edPrice = U.edit(ctx, GameApp.T("السعر (كريستال)...", "Price (crystals)..."));
        edPrice.setInputType(InputType.TYPE_CLASS_NUMBER);
        edPrice.setText(String.valueOf(item.optLong("price", 0)));
        final EditText edVal = U.edit(ctx, GameApp.T("قيمة التأثير...", "Effect value..."));
        edVal.setInputType(InputType.TYPE_CLASS_NUMBER);
        edVal.setText(String.valueOf(item.optDouble("val", 0)));
        String curType = item.optString("type", "str");
        lay.addView(edName);
        lay.addView(edPrice);
        lay.addView(edVal);
        TextView tLbl = U.text(ctx, GameApp.T("النوع الحالي: ", "Current type: ") + typesFor(typeIndex(curType)), 13, Color.WHITE, false);
        lay.addView(tLbl);
        Button bType = U.btn(ctx, GameApp.T("تغيير النوع (هجوم/دفاع/رشاقة/حظ/صحة)", "Change type (Attack/Defense/Agility/Luck/Health)"));
        final String[] newType = {curType};
        bType.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                AlertDialog.Builder tb = new AlertDialog.Builder(ctx);
                String[] types = {GameApp.T("هجوم", "Attack"), GameApp.T("دفاع", "Defense"), GameApp.T("رشاقة", "Agility"), GameApp.T("حظ", "Luck"), GameApp.T("صحة", "Health")};
                tb.setTitle(GameApp.T("اختر نوع التأثير", "Choose Effect Type"));
                tb.setItems(types, (d, ti) -> newType[0] = typeKey(ti));
                tb.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                tb.show();
            }
        });
        AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
        b2.setTitle(GameApp.T("تعديل الجرعة", "Edit Potion"));
        b2.setView(lay);
        b2.setPositiveButton(GameApp.T("حفظ", "Save"), (d, w) -> {
            double val = parseStat(edVal);
            String name = edName.getText().toString().trim();
            long price = 0;
            try { price = Long.parseLong(edPrice.getText().toString().trim()); } catch (Exception ignored) {}
            if (!name.isEmpty()) try { item.put("name", name); } catch (Exception ignored) {}
            try { item.put("price", price); } catch (Exception ignored) {}
            try { item.put("type", newType[0]); } catch (Exception ignored) {}
            try { item.put("val", val); } catch (Exception ignored) {}
            saveShop(fShop, GameApp.T("تم تعديل الجرعة", "Potion updated"));
        });
        b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b2.show();
    }

    private static int typeIndex(String t) {
        if (t.equals("end")) return 1;
        if (t.equals("agi")) return 2;
        if (t.equals("luck")) return 3;
        if (t.equals("hp")) return 4;
        return 0;
    }

    private static String typeKey(int i) {
        if (i == 1) return "end";
        if (i == 2) return "agi";
        if (i == 3) return "luck";
        if (i == 4) return "hp";
        return "str";
    }

    private static String typesFor(int i) {
        String[] types = {GameApp.T("هجوم", "Attack"), GameApp.T("دفاع", "Defense"), GameApp.T("رشاقة", "Agility"), GameApp.T("حظ", "Luck"), GameApp.T("صحة", "Health")};
        return types[i];
    }

    /** Find a player by display name and open the same punishment menu used in text chat. */
    public static void openPunishmentByDisplayName(final String displayName) {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("جاري البحث", "Searching"), GameApp.T("جاري جلب بيانات اللاعب...", "Fetching player data..."), true);
        new Thread(() -> {
            JSONObject all = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
            UI.post(() -> {
                progress.dismiss();
                if (!U.uiReady()) return;
                if (all == null) {
                    U.alert(ctx, null, GameApp.T("قاعدة البيانات فارغة أو تعذر الوصول إليها", "The database is empty or inaccessible"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject data = all.optJSONObject(id);
                    if (data != null && data.optString("name", "").equals(displayName)) {
                        openPunishmentUI(id, data);
                        return;
                    }
                }
                U.alert(ctx, null, GameApp.T("لم يتم العثور على بيانات اللاعب:" + displayName, "Player data was not found:" + displayName), GameApp.T("حسنا", "OK"), null);
            });
        }).start();
    }

    private static void openVoiceAdminUI() {
        final Context ctx = ctx();
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        final EditText edCode = U.edit(ctx, GameApp.T("اكتب كود الساحة مباشرة (اختياري)...", "Type the arena code directly (optional)..."));
        lay.addView(edCode);
        Button bCode = U.btn(ctx, GameApp.T("مراقبة ساحة بكود", "Monitor Arena by Code"), v -> {
            String c = edCode.getText().toString().trim();
            if (c.isEmpty()) {
                U.toast(GameApp.T("اكتب كود الساحة أولاً.", "Write the arena code first."));
                return;
            }
            ArenaSystem.openVoiceMonitor(c);
        });
        lay.addView(bCode);
        final ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري جلب الساحات...", "Fetching arenas..."), true);
        new Thread(() -> {
            final JSONObject all = Db.get("arenas");
            UI.post(() -> {
                progress.dismiss();
                if (!U.uiReady()) return;
                final List<String> ids = new ArrayList<>();
                final List<String> labels = new ArrayList<>();
                if (all != null) {
                    Iterator<String> it = all.keys();
                    while (it.hasNext()) {
                        String id = it.next();
                        JSONObject a = all.optJSONObject(id);
                        if (a == null) continue;
                        if (!"open".equals(a.optString("status"))) continue;
                        int players = 0;
                        JSONObject members = a.optJSONObject("members");
                        if (members != null) {
                            Iterator<String> mit = members.keys();
                            while (mit.hasNext()) {
                                JSONObject m = members.optJSONObject(mit.next());
                                if (m == null) continue;
                                String r = m.optString("role", "spectator");
                                if ("player".equals(r) || "host".equals(r)) players++;
                            }
                        }
                        String lbl = a.optString("name", id) + " | " + GameApp.T("المضيف:", "Host:") + a.optString("host", "-") + " | " + players + "/" + a.optInt("slots", 2);
                        ids.add(id);
                        labels.add(lbl);
                    }
                }
                Button bList = U.btn(ctx, GameApp.T("اختيار ساحة من القائمة (" + labels.size() + ")", "Choose an Arena from the List (" + labels.size() + ")"));
                bList.setOnClickListener(v -> {
                    if (labels.isEmpty()) {
                        U.alert(ctx, null, GameApp.T("لا توجد ساحات مفتوحة حالياً.", "No open arenas right now."), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    AlertDialog.Builder bb = new AlertDialog.Builder(ctx);
                    bb.setTitle(GameApp.T("الساحات المفتوحة", "Open Arenas"));
                    bb.setItems(labels.toArray(new String[0]), (d, idx) -> ArenaSystem.openVoiceMonitor(ids.get(idx)));
                    bb.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    if (U.uiReady()) bb.show();
                });
                lay.addView(bList);
                TextView note = U.text(ctx, GameApp.T("\nالتنبيه: المراقبة غير مرئية - لن يظهر حسابك ضمن أعضاء الساحة إطلاقاً، ولا يتعرف اللاعبون على وجودك. يمكنك الاستماع وكتم الأفراد أو الجميع وتطبيق أي عقوبة من عقوبات الدردشة.", "\nNote: Monitoring is invisible - your account never appears among the arena members and players cannot detect your presence. You can listen, mute individuals or everyone, and apply any text-chat punishment."), 13, Color.WHITE, false);
                lay.addView(note);
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("المراقبة الصوتية السرية", "Stealth Voice Monitoring"));
                b.setView(U.scroll(ctx, lay));
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                if (U.uiReady()) b.show();
            });
        }).start();
    }

    /* ── Developer ↔ Player direct messaging ─────────────────────────── */

    private static void openStaffMessagingUI() {
        final Context ctx = ctx();
        String[] opts = {
            GameApp.T("إرسال رسالة للاعب", "Send a Message to a Player"),
            GameApp.T("عرض الردود الواردة", "View Incoming Replies")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("مراسلة اللاعبين", "Player Messaging"));
        b.setItems(opts, (d, i) -> {
            if (i == 0) sendStaffMessageToPlayer();
            else showStaffInboxReplies();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void sendStaffMessageToPlayer() {
        final Context ctx = ctx();
        final EditText ed = U.edit(ctx, GameApp.T("اكتب اسم اللاعب أو ID...", "Type the player name or ID..."));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إسم أو ID اللاعب", "Player Name or ID"));
        b.setView(ed);
        b.setPositiveButton(GameApp.T("متابعة", "Continue"), (d, w) -> {
            String target = ed.getText().toString().trim();
            if (target.isEmpty()) return;
            new Thread(() -> {
                final String[] resolved = resolvePlayerByNameOrId(target);
                UI.post(() -> {
                    if (resolved == null) {
                        U.alert(ctx, null, GameApp.T("اللاعب غير موجود!", "Player not found!"), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    final String toKey = resolved[0];
                    final String toName = resolved[1];
                    final EditText msgEd = U.edit(ctx, GameApp.T("اكتب رسالتك للاعب " + toName + "...", "Type your message to " + toName + "..."));
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setTitle(GameApp.T("رسالة إلى " + toName, "Message to " + toName));
                    b2.setView(msgEd);
                    b2.setPositiveButton(GameApp.T("إرسال", "Send"), (d2, w2) -> {
                        String msg = msgEd.getText().toString().trim();
                        if (msg.isEmpty()) return;
                        final String staffName = GameApp.player == null ? "Staff" : GameApp.player.name;
                        final String staffId = GameApp.player == null ? "staff" : GameApp.player.username;
                        new Thread(() -> {
                            try {
                                long time = System.currentTimeMillis();
                                JSONObject rec = new JSONObject();
                                rec.put("from", staffId);
                                rec.put("from_name", staffName);
                                rec.put("msg", msg);
                                rec.put("time", time);
                                Db.put("staff_msgs/" + Db.encode(toKey) + "/" + time, rec);
                                UI.post(() -> U.toast(GameApp.T("تم إرسال الرسالة إلى " + toName, "Message sent to " + toName)));
                            } catch (Exception e) {
                                UI.post(() -> U.toast(GameApp.T("فشل إرسال الرسالة!", "Failed to send message!")));
                            }
                        }).start();
                    });
                    b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    b2.show();
                });
            }).start();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void showStaffInboxReplies() {
        final Context ctx = ctx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("الردود الواردة", "Incoming Replies"), GameApp.T("جاري التحميل...", "Loading..."), true);
        final String staffId = GameApp.player == null ? "staff" : GameApp.player.username;
        new Thread(() -> {
            JSONObject replies = Db.get("staff_replies/" + Db.encode(staffId));
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                if (replies == null || replies.length() == 0) {
                    U.alert(ctx, null, GameApp.T("لا توجد ردود واردة.", "No incoming replies."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                List<String> labels = new ArrayList<>();
                List<String> ids = new ArrayList<>();
                Iterator<String> it = replies.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject r = replies.optJSONObject(k);
                    if (r == null) continue;
                    String from = r.optString("from_name", "??");
                    String msg = r.optString("msg", "");
                    long time = r.optLong("time", 0);
                    String timeStr = time > 0 ? new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(time)) : "";
                    labels.add("[" + from + "] " + msg + " (" + timeStr + ")");
                    ids.add(k);
                }
                if (ids.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا توجد ردود واردة.", "No incoming replies."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("الردود الواردة", "Incoming Replies"));
                b.setItems(labels.toArray(new String[0]), (d, idx) -> {
                    String replyId = ids.get(idx);
                    JSONObject r = replies.optJSONObject(replyId);
                    if (r == null) return;
                    String from = r.optString("from_name", "??");
                    String toName = r.optString("to_name", "??");
                    String msg = r.optString("msg", "");
                    String detail = GameApp.T("اللاعب:", "Player:") + " " + from + "\n" + GameApp.T("الرسالة الأصلية:", "Original message:") + " " + r.optString("original_msg", "") + "\n" + GameApp.T("الرد:", "Reply:") + " " + msg;
                    AlertDialog.Builder d2 = new AlertDialog.Builder(ctx);
                    d2.setTitle(GameApp.T("رد من " + from, "Reply from " + from));
                    d2.setView(U.msg(detail));
                    d2.setPositiveButton(GameApp.T("رد عليه", "Reply"), (dd, ww) -> {
                        final EditText replyEd = U.edit(ctx, GameApp.T("اكتب ردك لـ " + from + "...", "Type your reply to " + from + "..."));
                        AlertDialog.Builder rb = new AlertDialog.Builder(ctx);
                        rb.setTitle(GameApp.T("رد على " + from, "Reply to " + from));
                        rb.setView(replyEd);
                        rb.setPositiveButton(GameApp.T("إرسال", "Send"), (ddd, www) -> {
                            String rtxt = replyEd.getText().toString().trim();
                            if (rtxt.isEmpty()) return;
                            new Thread(() -> {
                                try {
                                    long time2 = System.currentTimeMillis();
                                    JSONObject rec = new JSONObject();
                                    rec.put("from", staffId);
                                    rec.put("from_name", GameApp.player == null ? "Staff" : GameApp.player.name);
                                    rec.put("msg", rtxt);
                                    rec.put("time", time2);
                                    Db.put("staff_msgs/" + Db.encode(r.optString("to", "")) + "/" + time2, rec);
                                    UI.post(() -> U.toast(GameApp.T("تم إرسال الرد!", "Reply sent!")));
                                } catch (Exception e) {
                                    UI.post(() -> U.toast(GameApp.T("فشل إرسال الرد!", "Failed to send reply!")));
                                }
                            }).start();
                        });
                        rb.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                        rb.show();
                    });
                    d2.setNegativeButton(GameApp.T("حذف", "Delete"), (dd, ww) -> {
                        new Thread(() -> {
                            Db.delete("staff_replies/" + Db.encode(staffId) + "/" + replyId);
                            UI.post(() -> U.toast(GameApp.T("تم حذف الرد.", "Reply deleted.")));
                        }).start();
                    });
                    d2.setNeutralButton(GameApp.T("إغلاق", "Close"), null);
                    d2.show();
                });
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
            });
        }).start();
    }

    static String[] resolvePlayerByNameOrId(String nameOrId) {
        JSONObject all = Db.get("players");
        if (all == null) return null;
        // Try exact key match
        JSONObject p = all.optJSONObject(nameOrId);
        if (p != null) return new String[]{nameOrId, p.optString("name", nameOrId)};
        // Search by name or id
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject o = all.optJSONObject(k);
            if (o == null) continue;
            if (nameOrId.equalsIgnoreCase(o.optString("name", ""))) return new String[]{k, o.optString("name", k)};
            if (nameOrId.equalsIgnoreCase(o.optString("id", ""))) return new String[]{k, o.optString("name", k)};
            if (nameOrId.equalsIgnoreCase(o.optString("username", ""))) return new String[]{k, o.optString("name", k)};
        }
        return null;
    }
}
