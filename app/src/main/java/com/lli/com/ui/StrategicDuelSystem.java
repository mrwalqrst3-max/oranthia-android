package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.ItemData;
import com.lli.com.core.ItemGenerator;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class StrategicDuelSystem {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String PVP_PATH = "pvp_requests/";
    private static final String USER_PATH = "players/";
    private static final long PVP_POLL_MS = 1000;
    private static boolean pvpActive = false;
    private static boolean pvpWaiting = false;
    private static boolean duelRequestBusy = false;
    private static boolean incomingDuelShowing = false;
    public static volatile boolean arenaBattleMode = false;
    public static volatile String arenaBattleId = "";

    public static boolean isPvpActive() {
        return pvpActive;
    }

    private static final String[] S_TYPES = {"هجومي", "دفاعي", "تكتيكي", "مضاد", "شفاء"};
    private static final String[] S_ACTIONS = {"ضربة", "مناورة", "وقفة", "خدعة", "اندفاع", "تراجع", "تركيز", "انفجار"};
    private static final String[] S_ELEMENTS = {"النار", "الجليد", "البرق", "الرياح", "الظلام", "الضوء", "الأرض", "الطاقة"};

    private static final String[] S_TYPES_EN = {"Offensive", "Defensive", "Tactical", "Counter", "Healing"};
    private static final String[] S_ACTIONS_EN = {"Strike", "Maneuver", "Stance", "Trick", "Rush", "Retreat", "Focus", "Burst"};
    private static final String[] S_ELEMENTS_EN = {"Fire", "Ice", "Lightning", "Wind", "Darkness", "Light", "Earth", "Energy"};

    private static class Strategy {
        String name;
        String type;
        double atk;
        double def;
        double hpGain;
        String desc;
        int id;
        int freezeTurns;
        boolean doubleAttack;

        Strategy(String name, String type, double atk, double def, double hpGain, String desc, int id, int freezeTurns, boolean doubleAttack) {
            this.name = name;
            this.type = type;
            this.atk = atk;
            this.def = def;
            this.hpGain = hpGain;
            this.desc = desc;
            this.id = id;
            this.freezeTurns = freezeTurns;
            this.doubleAttack = doubleAttack;
        }

        Strategy(String name, String type, double atk, double def, double hpGain, String desc, int id) {
            this(name, type, atk, def, hpGain, desc, id, 0, false);
        }
    }

    private static final List<Strategy> POOL = new ArrayList<>();

    static {
        generateStrategies();
    }

    private static void generateStrategies() {
        POOL.clear();
        for (int i = 1; i <= 720; i++) {
            int it = (i - 1) % S_TYPES.length;
            int ia = ((int) Math.floor((i - 1) / S_TYPES.length)) % S_ACTIONS.length;
            int ie = ((int) Math.floor((i - 1) / (S_TYPES.length * S_ACTIONS.length))) % S_ELEMENTS.length;
            String stype = S_TYPES[it];
            String stypeEn = S_TYPES_EN[it];
            String sact = S_ACTIONS[ia];
            String sactEn = S_ACTIONS_EN[ia];
            String sele = S_ELEMENTS[ie];
            String seleEn = S_ELEMENTS_EN[ie];
            String name = GameApp.isEn
                    ? (sactEn + " " + seleEn + " (" + stypeEn + ") (S-" + i + ")")
                    : (sact + " " + sele + " الـ" + stype + " (S-" + i + ")");
            double atkMod = 1.0, defMod = 1.0, hpGain = 0;
            String desc = "";
            if (stype.equals("هجومي")) {
                atkMod = 1.15 + (i / 5000.0);
                defMod = 0.9;
                desc = GameApp.T("تركز على الهجوم لزيادة الضرر بنسبة 15% مع تراجع بسيط في الدفاع.", "Focuses on offense to increase damage by 15% with a slight reduction in defense.");
            } else if (stype.equals("دفاعي")) {
                atkMod = 0.85;
                defMod = 1.2 + (i / 5000.0);
                desc = GameApp.T("تركز على الحماية لتقليل الضرر المتلقى بنسبة 20% مع تراجع بسيط في الهجوم.", "Focuses on protection to reduce incoming damage by 20% with a slight reduction in offense.");
            } else if (stype.equals("تكتيكي")) {
                atkMod = 1.05;
                defMod = 1.05;
                desc = GameApp.T("توازن بين الهجوم والدفاع بنسبة 5% لكل منهما، استراتيجية آمنة.", "Balances offense and defense by 5% each, a safe strategy.");
            } else if (stype.equals("مضاد")) {
                atkMod = 1.1;
                defMod = 0.95;
                desc = GameApp.T("هجوم سريع ومفاجئ يزيد الضرر بنسبة 10% مع تراجع طفيف في الدفاع.", "A quick, surprise attack that increases damage by 10% with a slight drop in defense.");
            } else if (stype.equals("شفاء")) {
                atkMod = 0.5;
                defMod = 1.1;
                hpGain = 0.15 + (i / 10000.0);
                desc = GameApp.T("تركز على استعادة الصحة بنسبة", "Focuses on restoring health by") + Math.floor(hpGain * 100) + GameApp.T("% مع تقليل الهجوم.", "% while reducing offense.");
            }
            POOL.add(new Strategy(name, stype, atkMod, defMod, hpGain, desc, i));
        }
        POOL.add(new Strategy(GameApp.T("تجميد جليدي", "Icy Freeze"), "تكتيكي", 0.35, 0.9, 0,
                GameApp.T("جمّد الخصم لمدة دورين كاملين - أول ما يجي دور الخصم المجمد لا يستطيع اللعب ويتم تخطي دوره تلقائياً.", "Freeze the opponent for two full turns - when the frozen player's turn comes they cannot act and their turn is skipped automatically."),
                1001, 2, false));
        POOL.add(new Strategy(GameApp.T("اندفاع الهجوم المزدوج", "Double Strike Rush"), "هجومي", 0.75, 0.9, 0,
                GameApp.T("هجومك الوحشي يستمر لدورين متتاليين وتمنح دوراً إضافياً للهجوم قبل رد الخصم.", "Your ferocious attack lasts two consecutive turns and grants an extra attacking turn before the opponent can respond."),
                1002, 0, true));
    }

    public static void regenerate() {
        generateStrategies();
    }

    private static Strategy randomStrategy() {
        return POOL.get(NumberUtil.rand(0, POOL.size() - 1));
    }

    private static List<Strategy> randomStrategies(int count) {
        List<Strategy> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(randomStrategy());
        return out;
    }

    public static void openStrategicDuelMenu() {
        if (pvpActive) {
            U.toast(GameApp.T("هناك مبارزة قيد التنفيذ الآن!", "A duel is already in progress!"));
            return;
        }
        Context ctx = GameApp.uiCtx();
        String[] opts = {GameApp.T("مبارزة لاعب أونلاين (PVP)", "Duel an Online Player (PVP)"), GameApp.T("طلبات المبارزة الواردة", "Incoming Duel Requests")};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("المبارزة الاستراتيجية", "Strategic Duel"));
        b.setItems(opts, (d, i) -> {
            if (i == 0) {
                openOnlineDuelUI();
            } else {
                openIncomingDuelRequests();
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    public static void openOnlineDuelUI() {
        if (pvpActive) {
            U.toast(GameApp.T("هناك مبارزة قيد التنفيذ الآن!", "A duel is already in progress!"));
            return;
        }
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("جاري البحث", "Searching"), GameApp.T("جاري إحضار اللاعبين المتصلين...", "Fetching online players..."), true);
        new Thread(() -> {
            JSONObject allPlayers = Db.get(USER_PATH.substring(0, USER_PATH.length() - 1));
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
                    if (lastOnline > 0 && (curTime - lastOnline < 120)) {
                        online.add(new String[]{id, data.optString("name", id)});
                    }
                }
            }
            UI.post(() -> {
                progress.dismiss();
                if (online.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبون متصلون حالياً. حاول مرة أخرى لاحقاً!", "No players are currently online. Try again later!"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                List<String> names = new ArrayList<>();
                for (String[] p : online) names.add(p[1]);
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اختر خصماً للمبارزة", "Choose an Opponent to Duel"));
                b.setItems(names.toArray(new String[0]), (d, idx) -> sendDuelRequest(online.get(idx)[0], online.get(idx)[1]));
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    private static void sendDuelRequest(final String targetId, final String targetName) {
        if (duelRequestBusy) {
            U.toast(GameApp.T("هناك طلب مبارزة قيد الإرسال...", "A duel request is already being sent..."));
            return;
        }
        duelRequestBusy = true;
        final Context ctx = GameApp.uiCtx();
        final JSONObject request = new JSONObject();
        try {
            request.put("from_id", GameApp.player.username);
            request.put("from_name", GameApp.player.name);
            request.put("status", "pending");
            request.put("time", System.currentTimeMillis() / 1000);
        } catch (Exception ignored) {}
        final ProgressDialog progress = U.progress(ctx, GameApp.T("إرسال طلب", "Sending Request"), GameApp.T("جاري إرسال طلب المبارزة لـ", "Sending duel request to") + targetName + "...", true);
        new Thread(() -> {
            Db.put(PVP_PATH + Db.encode(targetId), request);
            UI.post(() -> {
                progress.dismiss();
                final AlertDialog[] waitingHolder = new AlertDialog[1];
                final AlertDialog waiting = new AlertDialog.Builder(ctx)
                        .setTitle(GameApp.T("طلب مرسل", "Request Sent"))
                        .setView(U.msg(GameApp.T("ينتظر رد", "Waiting for a reply from") + targetName + "..."))
                        .setNegativeButton(GameApp.T("إلغاء الطلب", "Cancel Request"), (d, w) -> {
                            duelRequestBusy = false;
                            d.dismiss();
                            new Thread(() -> Db.delete(PVP_PATH + Db.encode(targetId))).start();
                        })
                        .create();
                waitingHolder[0] = waiting;
                waiting.show();
                final int[] timeoutCount = {0};
                final Handler ticker = new Handler(Looper.getMainLooper());
                ticker.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        timeoutCount[0]++;
                        if (timeoutCount[0] > 15) {
                            duelRequestBusy = false;
                            new Thread(() -> Db.delete(PVP_PATH + Db.encode(targetId))).start();
                            if (waitingHolder[0] != null) waitingHolder[0].dismiss();
                            U.alert(ctx, null, GameApp.T("لم يستجب اللاعب للطلب.", "The player did not respond to the request."), GameApp.T("حسنا", "OK"), null);
                            return;
                        }
                        new Thread(() -> {
                            JSONObject res = Db.get(PVP_PATH + Db.encode(targetId));
                            UI.post(() -> {
                                if (res == null) {
                                    ticker.postDelayed(this, 2000);
                                    return;
                                }
                                String status = res.optString("status", "");
                                if (status.equals("accepted")) {
                                    ticker.removeCallbacksAndMessages(null);
                                    if (waitingHolder[0] != null) waitingHolder[0].dismiss();
                                    U.alert(ctx, GameApp.T("تم القبول!", "Accepted!"), GameApp.T("قبل", "") + targetName + GameApp.T("تحديك! اضغط بدء ليدخل الطرفان ساحة المبارزة.", "accepted your challenge! Press Start for both sides to enter the duel arena."), GameApp.T("بدء المبارزة", "Start Duel"), v -> {
                                        GameApp.sound.playSnd("pre_duel.mp3");
                                        duelRequestBusy = false;
                                        try {
                                            res.put("status", "started");
                                        } catch (Exception ignored) {}
                                        new Thread(() -> {
                                            final JSONObject enemy = Db.get(USER_PATH + Db.encode(targetId));
                                            if (enemy == null) { UI.post(() -> U.toast(GameApp.T("تعذر تحميل بيانات الخصم.", "Failed to load the opponent's data."))); return; }
                                            final PlayerData pd = GameApp.player;
                                            JSONObject eStats = enemy.optJSONObject("stats");
                                            double eMaxHp = Math.floor(eStats != null ? eStats.optDouble("max_hp", 100) : 100);
                                            double eHp = Math.floor(eStats != null ? eStats.optDouble("hp", eMaxHp) : eMaxHp);
                                            double eAtk = Math.floor(enemy.optLong("total_power", 100));
                                            double eDef = Math.floor(eStats != null ? eStats.optDouble("endurance", 10) : 10);
                                            double myMaxHp = pd.stats.maxHp > 0 ? pd.stats.maxHp : pd.stats.hp;
                                            JSONObject rec = new JSONObject();
                                            try {
                                                rec.put("status", "started");
                                                rec.put("initiator_id", pd.username);
                                                rec.put("from_id", pd.username);
                                                rec.put("from_name", pd.name);
                                                rec.put("to_id", targetId);
                                                rec.put("to_name", enemy.optString("name", targetId));
                                                rec.put("from_maxhp", myMaxHp);
                                                rec.put("from_hp", pd.stats.hp);
                                                rec.put("from_atk", pd.totalPower > 0 ? pd.totalPower : 1000);
                                                rec.put("from_def", pd.stats.endurance);
                                                rec.put("to_maxhp", eMaxHp);
                                                rec.put("to_hp", eHp);
                                                rec.put("to_atk", eAtk);
                                                rec.put("to_def", eDef);
                                                rec.put("turn", pd.username);
                                                rec.put("seq", 0);
                                                rec.put("over", false);
                                                rec.put("winner", "");
                                                rec.put("last_move_time", System.currentTimeMillis() / 1000);
                                                rec.put("last", new JSONObject());
                                            } catch (Exception ignored2) {}
                                            Db.put(PVP_PATH + Db.encode(targetId), rec);
                                            final JSONObject ed = enemy;
                                            UI.post(() -> startStrategicDuel(true, ed, true, pd.username));
                                        }).start();
                                    });
                                } else if (status.equals("rejected")) {
                                    duelRequestBusy = false;
                                    ticker.removeCallbacksAndMessages(null);
                                    new Thread(() -> Db.delete(PVP_PATH + Db.encode(targetId))).start();
                                    if (waitingHolder[0] != null) waitingHolder[0].dismiss();
                                    U.alert(ctx, null, GameApp.T("رفض", "") + targetName + GameApp.T("طلب المبارزة.", "rejected the duel request."), GameApp.T("حسنا", "OK"), null);
                                } else {
                                    ticker.postDelayed(this, 2000);
                                }
                            });
                        }).start();
                    }
                }, 2000);
            });
        }).start();
    }

    public static void openIncomingDuelRequests() {
        if (pvpActive) {
            U.toast(GameApp.T("هناك مبارزة قيد التنفيذ الآن!", "A duel is already in progress!"));
            return;
        }
        if (incomingDuelShowing) {
            U.toast(GameApp.T("يوجد طلب مبارزة معروض حالياً.", "A duel request is already displayed."));
            return;
        }
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("جاري التحميل", "Loading"), GameApp.T("جاري البحث عن طلبات مبارزة...", "Searching for duel requests..."), true);
        final String selfId = GameApp.player.username;
        new Thread(() -> {
            JSONObject req = Db.get(PVP_PATH + Db.encode(selfId));
            UI.post(() -> {
                progress.dismiss();
                if (req == null) {
                    U.alert(ctx, null, GameApp.T("لا توجد طلبات مبارزة حالياً.", "There are no duel requests right now."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                showIncomingDuelDialog(req);
            });
        }).start();
    }

    public static void showIncomingDuelRequest() {
        if (pvpActive || pvpWaiting || incomingDuelShowing) return;
        final Context ctx = GameApp.uiCtx();
        final String selfId = GameApp.player.username;
        new Thread(() -> {
            JSONObject req = Db.get(PVP_PATH + Db.encode(selfId));
            if (req == null) return;
            String status = req.optString("status", "");
            long reqTime = req.optLong("time", 0);
            long age = System.currentTimeMillis() / 1000 - reqTime;
            if (status.equals("started") && age < 180) {
                UI.post(() -> {
                    if (pvpActive) return;
                    waitForDuelStart(req.optString("from_name", GameApp.T("الخصم", "the opponent")));
                });
                return;
            }
            if (!status.equals("pending") || age >= 60) return;
            UI.post(() -> {
                if (pvpActive) return;
                showIncomingDuelDialog(req);
            });
        }).start();
    }

    private static void showIncomingDuelDialog(final JSONObject req) {
        if (incomingDuelShowing || pvpActive || pvpWaiting) return;
        incomingDuelShowing = true;
        final Context ctx = GameApp.uiCtx();
        String status = req.optString("status", "");
        if (status.equals("pending")) {
            final String fromName = req.optString("from_name", GameApp.T("لاعب", "Player"));
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("طلب مبارزة وارد!", "Incoming Duel Request!"));
            b.setView(U.msg(GameApp.T("اللاعب:", "Player:") + fromName + GameApp.T("\nيريد مبارزتك استراتيجياً.", "\nwants to duel you strategically.")));
            b.setPositiveButton(GameApp.T("قبول التحدي", "Accept Challenge"), (d, w) -> {
                incomingDuelShowing = false;
                try {
                    req.put("status", "accepted");
                } catch (Exception ignored) {}
                new Thread(() -> Db.put(PVP_PATH + Db.encode(GameApp.player.username), req)).start();
                waitForDuelStart(fromName);
            });
            b.setNegativeButton(GameApp.T("رفض", "Reject"), (d, w) -> {
                incomingDuelShowing = false;
                new Thread(() -> {
                    JSONObject rj = new JSONObject();
                    try {
                        rj.put("status", "rejected");
                    } catch (Exception ignored) {}
                    Db.put(PVP_PATH + Db.encode(GameApp.player.username), rj);
                }).start();
            });
            b.setNeutralButton(GameApp.T("إغلاق", "Close"), null);
            final AlertDialog dialog = b.create();
            dialog.setOnDismissListener(d -> incomingDuelShowing = false);
            dialog.show();
        } else if (status.equals("started")) {
            waitForDuelStart(req.optString("from_name", GameApp.T("الخصم", "the opponent")));
        }
    }

    private static void waitForDuelStart(final String fromName) {
        if (pvpActive || pvpWaiting) return;
        pvpWaiting = true;
        final Context ctx = GameApp.uiCtx();
        final String selfId = GameApp.player.username;
        final AlertDialog[] waitingDialog = {null};
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50, 50, 50, 50);
        TextView title = new TextView(ctx);
        title.setText(GameApp.T("ساحة الانتظار", "Waiting Arena"));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView info = new TextView(ctx);
        info.setText(GameApp.T("بانتظار أن يبدأ", "Waiting for") + fromName + GameApp.T("المبارزة...\n\nستنقلك الساحة تلقائياً إلى ساحة المبارزة عند البدء.", "to start the duel...\n\nThe arena will automatically take you to the duel arena when it starts."));
        info.setTextSize(15);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 20, 0, 0);
        root.addView(info);
        ProgressBar bar = new ProgressBar(ctx);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins(0, 30, 0, 0);
        bar.setLayoutParams(barLp);
        root.addView(bar);
        waitingDialog[0] = new AlertDialog.Builder(ctx)
                .setView(root)
                .setCancelable(false)
                .create();
        waitingDialog[0].show();

        final Handler poller = new Handler(Looper.getMainLooper());
        final long startTime = System.currentTimeMillis();
        poller.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    final JSONObject req = Db.get(PVP_PATH + Db.encode(selfId));
                    UI.post(() -> {
                        if (!pvpWaiting) {
                            poller.removeCallbacksAndMessages(null);
                            dismissWaitingDialog(waitingDialog);
                            return;
                        }
                        if (req != null && req.optString("status", "").equals("started")) {
                            poller.removeCallbacksAndMessages(null);
                            dismissWaitingDialog(waitingDialog);
                            pvpEnterFromRequest(req.optString("from_id", ""), req.optString("from_name", fromName), 0);
                        } else if (System.currentTimeMillis() - startTime > 30000) {
                            poller.removeCallbacksAndMessages(null);
                            pvpWaiting = false;
                            dismissWaitingDialog(waitingDialog);
                            new Thread(() -> Db.delete(PVP_PATH + Db.encode(selfId))).start();
                            U.alert(ctx, null, GameApp.T("لم يبدأ الخصم المبارزة خلال 30 ثانية. تم إلغاء الطلب تلقائياً.", "The opponent did not start the duel within 30 seconds. The request was cancelled automatically."), GameApp.T("حسنا", "OK"), null);
                        } else {
                            poller.postDelayed(this, PVP_POLL_MS);
                        }
                    });
                }).start();
            }
        }, PVP_POLL_MS);
    }

    private static void dismissWaitingDialog(final AlertDialog[] holder) {
        if (holder[0] != null) {
            try { holder[0].dismiss(); } catch (Exception ignored) {}
            holder[0] = null;
        }
    }

    private static void pvpEnterFromRequest(final String enemyId, final String enemyName, final int attempt) {
        new Thread(() -> {
            final JSONObject enemy = Db.get(USER_PATH + Db.encode(enemyId));
            UI.post(() -> {
                if (enemy == null && attempt < 5) {
                    UI.postDelayed(() -> pvpEnterFromRequest(enemyId, enemyName, attempt + 1), 800);
                } else if (enemy != null) {
                    pvpWaiting = false;
                    startStrategicDuel(true, enemy, false, enemyId);
                } else {
                    pvpWaiting = false;
                    U.alert(GameApp.uiCtx(), null, GameApp.T("تعذر تحميل بيانات الخصم. حاول مرة أخرى.", "Failed to load the opponent's data. Try again."), GameApp.T("حسنا", "OK"), null);
                }
            });
        }).start();
    }

    public static void startStrategicDuel(final boolean isPvp, final JSONObject enemyData, final boolean isInitiator, final String initiatorId) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        if (isPvp && enemyData == null) {
            U.alert(ctx, null, GameApp.T("خطأ: بيانات الخصم غير متوفرة للمبارزة.", "Error: Opponent data is unavailable for the duel."), GameApp.T("موافق", "OK"), null);
            return;
        }
        if (player.stats.hp <= 0) {
            U.toast(GameApp.T("صحتك منخفضة جداً!", "Your health is too low!"));
            return;
        }
        if (isPvp) {
            pvpQuotaGate(ctx, () -> startPvpDuel(ctx, player, enemyData, isInitiator, initiatorId));
            return;
        }

        final double[] pPower = {player.totalPower > 0 ? player.totalPower : 1000};
        final double[] monsterHp = {0};
        final double[] monsterMaxHp = {0};
        final double[] monsterAtk = {0};
        final String[] monsterName = {GameApp.T("وحش خرافي", "Mythic Monster")};
        final double[] playerHp = {player.stats.hp};
        final double[] playerMaxHp = {player.stats.maxHp > 0 ? player.stats.maxHp : player.stats.hp};
        final boolean[] itemUsedThisTurn = {false};
        final String[] turnOwner = {isInitiator ? "player" : "enemy"};
        final double[] tribeAtkBonus = {1.0};
        final int[] enemyFreeze = {0};
        final int[] myFreeze = {0};
        final boolean[] extraAttack = {false};
        final long[] battleStart = {System.currentTimeMillis()};

        if (isPvp) {
            JSONObject eStats = enemyData.optJSONObject("stats");
            monsterHp[0] = Math.floor(eStats != null ? eStats.optDouble("hp", 100) : 100);
            monsterMaxHp[0] = Math.floor(eStats != null ? eStats.optDouble("max_hp", monsterHp[0]) : monsterHp[0]);
            monsterAtk[0] = Math.floor((enemyData.optLong("total_power", 100)) / 10.0);
            monsterName[0] = enemyData.optString("name", GameApp.T("خصم مجهول", "Unknown Opponent"));
        } else {
            double hpMult = 1.15 + (NumberUtil.rand(0, 10) / 100.0);
            monsterHp[0] = Math.floor(playerMaxHp[0] * hpMult);
            monsterMaxHp[0] = monsterHp[0];
            monsterAtk[0] = Math.floor(pPower[0] / 5 + (player.level * 30) + 200);
            monsterName[0] = GameApp.T("وحش خرافي", "Mythic Monster");
        }

        if (player.clan != null && !player.clan.equals("لا يوجد")) {
            new Thread(() -> {
                JSONObject tribe = Db.get("tribes_system/" + Db.encode(player.clan));
                if (tribe != null) {
                    JSONObject skills = tribe.optJSONObject("skills");
                    if (skills != null) {
                        double atkLv = skills.optDouble("atk_boost", 0);
                        tribeAtkBonus[0] = 1.0 + (atkLv * 0.05);
                    }
                }
            }).start();
        }

        final java.util.function.BiConsumer<Strategy, Strategy>[] processHolder = new java.util.function.BiConsumer[1];
        final Runnable[] showDuelUIRef = new Runnable[1];

        final java.util.function.Consumer<String> finishVictory = (msg) -> {
            GameApp.sound.playSnd("kill_enemy.mp3");
            double perf = Math.max(0, Math.min(1, playerHp[0] / Math.max(1, playerMaxHp[0])));
            long base = isPvp ? (player.level * 5000L) : (50000L + (player.level * 1000L));
            long goldReward = base + (long) (perf * base);
            long cryReward = NumberUtil.rand(2, 8) + (long) (perf * 5);
            ItemData rewardItem = ItemGenerator.generateRandomItem(player.level, 0);
            player.gold += goldReward;
            player.crystals += cryReward;
            player.inventory.add(rewardItem);
            player.stats.hp = Math.max(1, playerHp[0]);
            SaveSystem.saveAndRefresh();
            final long fGold = goldReward;
            final long fCry = cryReward;
            final ItemData fItem = rewardItem;
            new AlertDialog.Builder(ctx)
                    .setTitle(GameApp.T("انتصار مستحق!", "Well-Earned Victory!"))
                    .setView(U.msg(msg))
                    .setPositiveButton(GameApp.T("استلام المكافآت", "Claim Rewards"), (d, w) -> {
                        d.dismiss();
                        new AlertDialog.Builder(ctx)
                                .setTitle(GameApp.T("مكافآت الانتصار", "Victory Rewards"))
                                .setView(U.msg(GameApp.T("لقد استلمت:\n\n- معدة:", "You received:\n\n- Item:") + fItem.name + "(" + fItem.rarity + ")" + GameApp.T("\n- ذهب:", "\n- Gold:") + NumberUtil.formatNumber(fGold) + GameApp.T("\n- كريستال:", "\n- Crystal:") + fCry))
                                .setPositiveButton(GameApp.T("تم", "Done"), null)
                                .setCancelable(false)
                                .show();
                    })
                    .setCancelable(false)
                    .show();
        };

        final java.util.function.Consumer<String> finishDefeat = (msg) -> {
            player.stats.hp = 1;
            SaveSystem.saveAndRefresh();
            new AlertDialog.Builder(ctx)
                    .setTitle(GameApp.T("هزيمة!", "Defeat!"))
                    .setView(U.msg(msg + GameApp.T("\n\nلقد سقطت في المعركة. صحتك الآن حرجة جداً!", "\n\nYou fell in battle. Your health is now critical!")))
                    .setPositiveButton(GameApp.T("العودة", "Return"), (d, w) -> d.dismiss())
                    .setCancelable(false)
                    .show();
            GameApp.sound.playSnd("death_defeat.mp3");
        };

        java.util.function.BiConsumer<Strategy, Strategy> processDuelResult = new java.util.function.BiConsumer<Strategy, Strategy>() {
            @Override
            public void accept(final Strategy pStrat, final Strategy mStrat) {
                StringBuilder resultMsg = new StringBuilder();
                double pDmg = 0, mDmg = 0, pHeal = 0, mHeal = 0;

                if (turnOwner[0].equals("player")) {
                    if (myFreeze[0] > 0) {
                        myFreeze[0]--;
                        String fmsg = GameApp.T("أنت مجمد! ❄️ خسرت دورك هذا الدور.", "You are frozen! ❄️ You lost this turn.");
                        turnOwner[0] = "enemy";
                        player.stats.hp = playerHp[0];
                        new AlertDialog.Builder(ctx)
                                .setTitle(GameApp.T("مجمّد!", "Frozen!"))
                                .setView(U.msg(fmsg + GameApp.T("\n\nتم تخطي دورك... الآن دور الخصم.", "\n\nYour turn was skipped... now it's the enemy's turn.")))
                                .setPositiveButton(GameApp.T("متابعة", "Continue"), (d, w) -> {
                                    Strategy foeMove = randomStrategy();
                                    UI.postDelayed(() -> {
                                        if (processHolder[0] != null) processHolder[0].accept(null, foeMove);
                                    }, 300);
                                })
                                .setCancelable(false)
                                .show();
                        return;
                    }
                    double mDefFactor = 1 - ((pStrat.def - 1) * 0.2);
                    mDefFactor = Math.max(0.6, mDefFactor);
                    pDmg = Math.floor((pPower[0] / (isPvp ? 10 : 15)) * pStrat.atk * mDefFactor * tribeAtkBonus[0] + NumberUtil.rand(10, 50));
                    if (pStrat.hpGain > 0) {
                        pHeal = Math.floor(playerMaxHp[0] * pStrat.hpGain);
                        playerHp[0] = Math.min(playerMaxHp[0], playerHp[0] + pHeal);
                    }
                    monsterHp[0] = monsterHp[0] - pDmg;

                    resultMsg.append(GameApp.T("تم تنفيذ استراتيجيتك بنجاح!\n", "Your strategy was executed successfully!\n"));
                    resultMsg.append(GameApp.T("استخدمت:", "You used:")).append(pStrat.name).append("\n");
                    if (pDmg > 0) resultMsg.append(GameApp.T("هاجمت الخصم وسببت له ضرراً قدره:", "You attacked the enemy and dealt damage of:")).append((long) Math.floor(pDmg)).append("\n");
                    if (pHeal > 0) resultMsg.append(GameApp.T("استعدت صحة قدرها:", "You recovered health of:")).append((long) Math.floor(pHeal)).append("\n");
                    if (pStrat.freezeTurns > 0) {
                        enemyFreeze[0] = pStrat.freezeTurns;
                        resultMsg.append(GameApp.T("❄️ جمّدت الخصم!", "❄️ You froze the enemy!"));
                        resultMsg.append(GameApp.T(" لن يستطيع اللعب لمدة ", " The enemy cannot act for ")).append(pStrat.freezeTurns).append(GameApp.T(" من أدواره وسيتم تخطيها تلقائياً.", " of its turns; they will be skipped automatically."));
                    }
                    if (pStrat.doubleAttack) {
                        extraAttack[0] = true;
                        resultMsg.append(GameApp.T("⚡ هجومك المزدوج سيمنحك دوراً إضافياً للهجوم!", "⚡ Your double strike grants you an extra attacking turn!"));
                    }
                    resultMsg.append(GameApp.T("\nصحتك:", "\nYour HP:")).append((long) Math.max(0, playerHp[0]))
                            .append(GameApp.T("\nصحة الخصم:", "\nEnemy HP:")).append((long) Math.max(0, monsterHp[0]));

                    final String msg = resultMsg.toString();
                    if (monsterHp[0] <= 0) {
                        finishVictory.accept(msg);
                        return;
                    }
                    player.stats.hp = playerHp[0];
                    if (extraAttack[0]) {
                        extraAttack[0] = false;
                        turnOwner[0] = "player";
                        new AlertDialog.Builder(ctx)
                                .setTitle(GameApp.T("هجوم مزدوج!", "Double Strike!"))
                                .setView(U.msg(msg + GameApp.T("\n\nدور إضافي لك! هجومك المستمر يسمح لك بالهجوم مرة أخرى.", "\n\nExtra turn for you! Your ongoing attack lets you strike once more.")))
                                .setPositiveButton(GameApp.T("متابعة", "Continue"), (d, w) -> {
                                    itemUsedThisTurn[0] = false;
                                    UI.postDelayed(() -> {
                                        if (showDuelUIRef[0] != null) showDuelUIRef[0].run();
                                    }, 300);
                                })
                                .setCancelable(false)
                                .show();
                        return;
                    }
                    turnOwner[0] = "enemy";
                    player.stats.hp = playerHp[0];
                    new AlertDialog.Builder(ctx)
                            .setTitle(GameApp.T("نتيجة هجومك", "Your Attack Result"))
                            .setView(U.msg(msg + GameApp.T("\n\nالآن دور الخصم في الهجوم...", "\n\nNow it's the enemy's turn to attack...")))
                            .setPositiveButton(GameApp.T("متابعة", "Continue"), (d, w) -> {
                                Strategy foeMove = randomStrategy();
                                UI.postDelayed(() -> {
                                    if (processHolder[0] != null) processHolder[0].accept(null, foeMove);
                                }, 300);
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    if (enemyFreeze[0] > 0) {
                        enemyFreeze[0]--;
                        turnOwner[0] = "player";
                        player.stats.hp = playerHp[0];
                        StringBuilder fmsg = new StringBuilder(GameApp.T("❄️ الخصم مجمد!", "❄️ The enemy is frozen!"));
                        fmsg.append(GameApp.T(" لا يستطيع الهجوم، تم تخطي دوره.", " It cannot attack; its turn was skipped."));
                        fmsg.append(GameApp.T("\n\nصحتك: ", "\n\nYour HP: ")).append((long) Math.max(0, playerHp[0]));
                        fmsg.append(GameApp.T("\nصحة الخصم: ", "\nEnemy HP: ")).append((long) Math.max(0, monsterHp[0]));
                        new AlertDialog.Builder(ctx)
                                .setTitle(GameApp.T("الخصم مجمد", "Enemy Frozen"))
                                .setView(U.msg(fmsg.toString() + GameApp.T("\n\nدورك الآن! اختر استراتيجيتك.", "\n\nIt's your turn now! Choose your strategy.")))
                                .setPositiveButton(GameApp.T("متابعة", "Continue"), (d, w) -> {
                                    itemUsedThisTurn[0] = false;
                                    UI.postDelayed(() -> {
                                        if (showDuelUIRef[0] != null) showDuelUIRef[0].run();
                                    }, 300);
                                })
                                .setCancelable(false)
                                .show();
                        return;
                    }
                    double pDefFactor = 1 - ((mStrat.def - 1) * 0.2);
                    pDefFactor = Math.max(0.6, pDefFactor);
                    mDmg = Math.floor((monsterAtk[0] / (isPvp ? 5 : 3)) * mStrat.atk * pDefFactor + NumberUtil.rand(5, 30));
                    double pAgi = com.lli.com.core.BattleDecay.effective(player.stats.agility, (System.currentTimeMillis() - battleStart[0]) / 1000.0);
                    double armorPierce = Math.floor(monsterAtk[0] * 0.1);
                    mDmg = mDmg + armorPierce;
                    if (pAgi > 1000) {
                        double agiPenalty = Math.min(0.9, (pAgi - 1000) / 100000.0);
                        mDmg = mDmg * (1 + agiPenalty);
                    }
                    double minDmg = Math.max(playerMaxHp[0] * 0.01, monsterAtk[0] * 0.05);
                    mDmg = Math.max(mDmg, minDmg);
                    if (mStrat.hpGain > 0) {
                        mHeal = Math.floor(monsterMaxHp[0] * mStrat.hpGain);
                        monsterHp[0] = Math.min(monsterMaxHp[0], monsterHp[0] + mHeal);
                    }
                    playerHp[0] = playerHp[0] - mDmg;
                    player.stats.hp = playerHp[0];

                    resultMsg.append(GameApp.T("قام الخصم بالهجوم عليك!\n", "The enemy attacked you!\n"));
                    resultMsg.append(GameApp.T("استخدم الخصم:", "The enemy used:")).append(mStrat.name).append("\n");
                    if (mDmg > 0) resultMsg.append(GameApp.T("لقد تلقيت ضرراً قدره:", "You received damage of:")).append((long) Math.floor(mDmg)).append("\n");
                    if (mHeal > 0) resultMsg.append(GameApp.T("استعاد الخصم صحة قدرها:", "The enemy recovered health of:")).append((long) Math.floor(mHeal)).append("\n");
                    if (mStrat.freezeTurns > 0) {
                        myFreeze[0] = mStrat.freezeTurns;
                        resultMsg.append(GameApp.T("❄️ الخصم جمّدك!", "❄️ The enemy froze you!"));
                        resultMsg.append(GameApp.T(" لن تستطيع اللعب لمدة ", " You cannot act for ")).append(mStrat.freezeTurns).append(GameApp.T(" من أدوارك وسيتم تخطيها.", " of your turns; they will be skipped."));
                    }
                    resultMsg.append(GameApp.T("\nصحتك:", "\nYour HP:")).append((long) Math.max(0, playerHp[0]))
                            .append(GameApp.T("\nصحة الخصم:", "\nEnemy HP:")).append((long) Math.max(0, monsterHp[0]));

                    final String msg = resultMsg.toString();
                    if (playerHp[0] <= 0) {
                        finishDefeat.accept(msg);
                        return;
                    }
                    turnOwner[0] = "player";
                    new AlertDialog.Builder(ctx)
                            .setTitle(GameApp.T("هجوم الخصم", "Enemy Attack"))
                            .setView(U.msg(msg + GameApp.T("\n\nدورك الآن! اختر استراتيجيتك.", "\n\nIt's your turn now! Choose your strategy.")))
                            .setPositiveButton(GameApp.T("متابعة", "Continue"), (d, w) -> {
                                itemUsedThisTurn[0] = false;
                                UI.postDelayed(() -> {
                                    if (showDuelUIRef[0] != null) showDuelUIRef[0].run();
                                }, 300);
                            })
                            .setCancelable(false)
                            .show();
                }
            }
        };
        processHolder[0] = processDuelResult;

        showDuelUIRef[0] = () -> {
            final List<Strategy> strats = randomStrategies(4);
            final LinearLayout layout = new LinearLayout(ctx);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(40, 40, 40, 40);
            final ScrollView scroll = new ScrollView(ctx);
            final LinearLayout btnContainer = new LinearLayout(ctx);
            btnContainer.setOrientation(LinearLayout.VERTICAL);

            final AlertDialog[] dialog = {null};

            for (final Strategy s : strats) {
                Button btn = new Button(ctx);
                btn.setText(s.name + "\n" + s.desc + "\n" + GameApp.T("[هجوم: x", "[Attack: x") + String.format(Locale.US, "%.1f", s.atk) + GameApp.T("| دفاع: x", "| Defense: x") + String.format(Locale.US, "%.1f", s.def) + "]");
                btn.setAllCaps(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 10, 0, 10);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> {
                    GameApp.sound.playSnd("click.mp3");
                    if (!turnOwner[0].equals("player")) {
                        U.toast(GameApp.T("ليس دورك! انتظر الخصم.", "Not your turn! Wait for the opponent."));
                        return;
                    }
                    if (dialog[0] != null) dialog[0].dismiss();
                    final Strategy mStrat = randomStrategy();
                    if (isPvp) {
                        final ProgressDialog wait = U.progress(ctx, GameApp.T("مبارزة استراتيجية", "Strategic Duel"), GameApp.T("جاري تنفيذ استراتيجيتك وانتظار رد الخصم...", "Executing your strategy and waiting for the opponent's response..."), true);
                        UI.postDelayed(() -> {
                            wait.dismiss();
                            UI.postDelayed(() -> {
                                if (processHolder[0] != null) processHolder[0].accept(s, mStrat);
                            }, 300);
                        }, NumberUtil.rand(1500, 2500));
                    } else {
                        UI.postDelayed(() -> {
                            if (processHolder[0] != null) processHolder[0].accept(s, mStrat);
                        }, 300);
                    }
                });
                btnContainer.addView(btn);
            }
            scroll.addView(btnContainer);
            layout.addView(scroll);

            layout.addView(U.msg(String.format(Locale.US, GameApp.T("صحتك: %d\nدور: %s\nاختر استراتيجيتك:", "Your HP: %d\nTurn: %s\nChoose your strategy:"), (long) Math.floor(playerHp[0]), turnOwner[0].equals("player") ? GameApp.T("دورك", "Your Turn") : GameApp.T("دور الخصم", "Enemy's Turn"))), 0);
            dialog[0] = new AlertDialog.Builder(ctx)
                    .setTitle(String.format(Locale.US, GameApp.T("مبارزة %s (HP: %d)", "Duel %s (HP: %d)"), monsterName[0], (long) Math.max(0, monsterHp[0])))
                    .setView(layout)
                    .setNegativeButton(GameApp.T("انسحاب", "Retreat"), null)
                    .create();
            dialog[0].show();
        };

        showDuelUIRef[0].run();
    }

    private static void pvpQuotaGate(final Context ctx, final Runnable onAllowed) {
        final PlayerData p = GameApp.player;
        long now = System.currentTimeMillis() / 1000;
        if (p.pvpHourStart == 0 || (now - p.pvpHourStart) >= 600) {
            p.pvpHourStart = now;
            p.pvpMatchesUsed = 0;
        }
        if (p.pvpMatchesUsed < 2) {
            p.pvpMatchesUsed++;
            SaveSystem.save();
            onAllowed.run();
            return;
        }
        if (p.crystals >= 10 && p.diamonds >= 11) {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("حد المبارزات", "Duel Limit"));
            b.setView(U.msg(GameApp.T("لقد استنفدت حصتك المجانية (2 مبارزات كل 10 دقائق).\n\nللعب مبارزة إضافية الآن يجب دفع:\n- 10 كريستال\n- 11 ألماسة\n\nهل تريد المتابعة؟", "You have used up your free quota (2 duels per 10 minutes).\n\nTo play an extra duel now you must pay:\n- 10 Crystals\n- 11 Diamonds\n\nDo you want to continue?")));
            b.setPositiveButton(GameApp.T("دفع واللعب", "Pay & Play"), (d, w) -> {
                p.crystals -= 10;
                p.diamonds -= 11;
                p.pvpMatchesUsed++;
                GameApp.sound.playSnd("buy.mp3");
                SaveSystem.saveAndRefresh();
                onAllowed.run();
            });
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        } else {
            U.alert(ctx, GameApp.T("حد المبارزات", "Duel Limit"),
                    GameApp.T("لقد استنفدت حصتك المجانية (2 مبارزات كل 10 دقائق).\n\nمواردك غير كافية للدفع (تحتاج 10 كريستال + 11 ألماسة).\n\nستتجدد الحصة المجانية خلال:", "You have used up your free quota (2 duels per 10 minutes).\n\nYour resources are insufficient to pay (you need 10 crystals + 11 diamonds).\n\nYour free quota will refresh in:") + NumberUtil.formatRemainingTime(p.pvpHourStart + 600),
                    GameApp.T("حسنا", "OK"), null);
        }
    }

    private static class PvpDuel {
        Context ctx;
        PlayerData player;
        String myId;
        String enemyId;
        String enemyName;
        String recordKey;
        String mySide;
        String enemySide;
        double myMaxHp, myHp, myAtk, myDef, enemyMaxHp, enemyHp, enemyAtk, enemyDef, tribeAtkBonus;
        int lastSeq;
        int myFreeze;
        int enemyFreeze;
        boolean showingDialog;
        boolean finished;
        boolean myTurn;
        AlertDialog arenaDialog;
        TextView myHpTv, enemyHpTv, turnTv, statusTv;
        ProgressBar myHpBar, enemyHpBar;
        LinearLayout stratContainer;
        Button bagBtn, surrenderBtn;
        Handler poller = new Handler(Looper.getMainLooper());
    }

    public static void startArenaBattle(final String arenaId, final String opponentId, final String opponentName, final boolean isInitiator, final JSONObject enemyData) {
        if (pvpActive) {
            U.toast(GameApp.T("هناك مبارزة قيد التنفيذ الآن!", "A duel is already in progress!"));
            return;
        }
        arenaBattleMode = true;
        arenaBattleId = arenaId;
        try {
            startPvpDuel(GameApp.uiCtx(), GameApp.player, enemyData, isInitiator, opponentId);
        } catch (Exception e) {
            arenaBattleMode = false;
            arenaBattleId = "";
            U.alert(GameApp.uiCtx(), null, GameApp.T("تعذر بدء معركة الساحة.", "Could not start the arena battle."), GameApp.T("حسنا", "OK"), null);
        }
    }

    private static void startPvpDuel(final Context ctx, final PlayerData player, final JSONObject enemyData, final boolean isInitiator, final String initiatorId) {
        if (pvpActive) return;
        pvpActive = true;
        pvpWaiting = false;
        String eId = initiatorId;
        if (eId == null || eId.isEmpty()) eId = enemyData.optString("username", "");
        if (eId != null && eId.equals(player.username)) eId = enemyData.optString("username", "");
        if (eId == null || eId.isEmpty()) {
            pvpActive = false;
            U.alert(ctx, null, GameApp.T("خطأ في بيانات المبارزة: لا يمكن تحديد الخصم.", "Duel data error: Could not identify the opponent."), GameApp.T("موافق", "OK"), null);
            return;
        }
        final PvpDuel d = new PvpDuel();
        d.ctx = ctx;
        d.player = player;
        d.myId = player.username;
        d.enemyId = eId;
        d.recordKey = (arenaBattleMode && arenaBattleId != null && !arenaBattleId.isEmpty())
                ? "arenas/" + Db.encode(arenaBattleId) + "/battle"
                : PVP_PATH + Db.encode(eId);
        d.myMaxHp = player.stats.maxHp > 0 ? player.stats.maxHp : player.stats.hp;
        d.myHp = player.stats.hp;
        d.myAtk = player.totalPower > 0 ? player.totalPower : 1000;
        d.myDef = player.stats.endurance;
        d.tribeAtkBonus = 1.0;

        JSONObject eStats = enemyData.optJSONObject("stats");
        d.enemyMaxHp = Math.floor(eStats != null ? eStats.optDouble("max_hp", 100) : 100);
        d.enemyHp = Math.floor(eStats != null ? eStats.optDouble("hp", d.enemyMaxHp) : d.enemyMaxHp);
        d.enemyAtk = Math.floor(enemyData.optLong("total_power", 100));
        d.enemyDef = Math.floor(eStats != null ? eStats.optDouble("endurance", 10) : 10);
        d.enemyName = enemyData.optString("name", GameApp.T("خصم مجهول", "Unknown Opponent"));

        if (player.clan != null && !player.clan.equals("لا يوجد")) {
            new Thread(() -> {
                JSONObject tribe = Db.get("tribes_system/" + Db.encode(player.clan));
                if (tribe != null) {
                    JSONObject skills = tribe.optJSONObject("skills");
                    if (skills != null) d.tribeAtkBonus = 1.0 + (skills.optDouble("atk_boost", 0) * 0.05);
                }
            }).start();
        }

        if (isInitiator) {
            d.mySide = "from";
            d.enemySide = "to";
            JSONObject rec = new JSONObject();
            try {
                rec.put("status", "started");
                rec.put("initiator_id", d.myId);
                rec.put("from_id", d.myId);
                rec.put("from_name", player.name);
                rec.put("to_id", d.enemyId);
                rec.put("to_name", d.enemyName);
                rec.put("from_maxhp", d.myMaxHp);
                rec.put("from_hp", d.myHp);
                rec.put("from_atk", d.myAtk);
                rec.put("from_def", d.myDef);
                rec.put("to_maxhp", d.enemyMaxHp);
                rec.put("to_hp", d.enemyHp);
                rec.put("to_atk", d.enemyAtk);
                rec.put("to_def", d.enemyDef);
                rec.put("turn", d.myId);
                rec.put("seq", 0);
                rec.put("over", false);
                rec.put("winner", "");
                rec.put("last_move_time", System.currentTimeMillis() / 1000);
                rec.put("last", new JSONObject());
            } catch (Exception ignored) {}
            new Thread(() -> Db.put(d.recordKey, rec)).start();
            d.lastSeq = 0;
            pvpStartPolling(d);
            pvpShowArena(d, GameApp.T("المبارزة بدأت! دورك الآن.", "The duel has started! It's your turn now."), true);
        } else {
            d.mySide = "to";
            d.enemySide = "from";
            d.recordKey = (arenaBattleMode && arenaBattleId != null && !arenaBattleId.isEmpty())
                    ? "arenas/" + Db.encode(arenaBattleId) + "/battle"
                    : PVP_PATH + Db.encode(d.myId);
            new Thread(() -> {
                JSONObject r0 = Db.get(d.recordKey);
                if (r0 != null && r0.optBoolean("over", false)) {
                    String winner = r0.optString("winner", "");
                    Db.delete(d.recordKey);
                    pvpActive = false;
                    if (winner.equals(d.enemyId)) {
                        d.myHp = r0.optDouble(d.mySide + "_hp", d.myHp);
                        UI.post(() -> pvpFinishDefeat(d, GameApp.T("انتهت المبارزة قبل دخولك الساحة.", "The duel ended before you entered the arena.")));
                    } else {
                        UI.post(() -> U.alert(d.ctx, null, GameApp.T("انتهت المبارزة السابقة.", "The previous duel has ended."), GameApp.T("حسنا", "OK"), null));
                    }
                    return;
                }
                pvpStartPolling(d);
                UI.post(() -> pvpShowArena(d, GameApp.T("المبارزة بدأت! انتظر دورك...", "The duel has started! Wait for your turn..."), false));
            }).start();
        }
    }

    private static void pvpShowArena(final PvpDuel d, final String initialMsg, final boolean myTurn) {
        if (d.arenaDialog != null) return;
        final Context ctx = d.ctx;
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(ctx);
        title.setText(GameApp.T("ساحة المبارزة:", "Duel Arena:") + d.enemyName);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        d.enemyHpTv = new TextView(ctx);
        d.enemyHpTv.setTextSize(17);
        d.enemyHpTv.setTextColor(Color.RED);
        root.addView(d.enemyHpTv);
        d.enemyHpBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(d.enemyHpBar);

        d.myHpTv = new TextView(ctx);
        d.myHpTv.setTextSize(17);
        d.myHpTv.setTextColor(Color.GREEN);
        root.addView(d.myHpTv);
        d.myHpBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(d.myHpBar);

        d.turnTv = new TextView(ctx);
        d.turnTv.setTextSize(19);
        d.turnTv.setGravity(Gravity.CENTER);
        root.addView(d.turnTv);

        d.statusTv = new TextView(ctx);
        d.statusTv.setTextSize(14);
        d.statusTv.setGravity(Gravity.CENTER);
        d.statusTv.setPadding(0, 10, 0, 10);
        root.addView(d.statusTv);

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        d.stratContainer = new LinearLayout(ctx);
        d.stratContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(d.stratContainer);
        root.addView(scroll);

        final LinearLayout bottom = new LinearLayout(ctx);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        d.bagBtn = new Button(ctx);
        d.bagBtn.setText(GameApp.T("الحقيبة", "Bag"));
        d.bagBtn.setAllCaps(false);
        d.bagBtn.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            pvpShowBag(d);
        });
        d.surrenderBtn = new Button(ctx);
        d.surrenderBtn.setText(GameApp.T("انسحاب", "Retreat"));
        d.surrenderBtn.setAllCaps(false);
        d.surrenderBtn.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            pvpSurrender(d);
        });
        bottom.addView(d.bagBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(d.surrenderBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bottom);

        d.arenaDialog = new AlertDialog.Builder(ctx)
                .setView(root)
                .setCancelable(false)
                .create();
        d.arenaDialog.show();
        d.showingDialog = true;
        d.myTurn = myTurn;
        if (d.myTurn) {
            pvpRebuildStrategies(d);
        } else {
            d.stratContainer.removeAllViews();
        }
        pvpUpdateArenaUI(d, initialMsg);
    }

    private static void pvpCloseArena(final PvpDuel d) {
        if (d.arenaDialog != null) {
            try { d.arenaDialog.dismiss(); } catch (Exception ignored) {}
            d.arenaDialog = null;
        }
    }

    private static void pvpUpdateArenaUI(final PvpDuel d, final String statusMsg) {
        if (d.arenaDialog == null) return;
        long myHp = (long) Math.max(0, d.myHp);
        long enHp = (long) Math.max(0, d.enemyHp);
        d.myHpTv.setText(GameApp.T("صحتك:", "Your HP:") + myHp + "/" + (long) Math.max(1, d.myMaxHp));
        d.enemyHpTv.setText(GameApp.T("صحة الخصم:", "Enemy HP:") + enHp + "/" + (long) Math.max(1, d.enemyMaxHp));
        d.myHpBar.setMax((int) Math.max(1, (long) d.myMaxHp));
        d.myHpBar.setProgress((int) Math.max(0, (long) d.myHp));
        d.enemyHpBar.setMax((int) Math.max(1, (long) d.enemyMaxHp));
        d.enemyHpBar.setProgress((int) Math.max(0, (long) d.enemyHp));
        d.turnTv.setText(GameApp.T("الدور:", "Turn:") + (d.myTurn ? GameApp.T("دورك", "Your Turn") : GameApp.T("دور الخصم", "Enemy's Turn")));
        d.turnTv.setTextColor(d.myTurn ? Color.GREEN : Color.RED);
        if (statusMsg != null) d.statusTv.setText(statusMsg);
        for (int i = 0; i < d.stratContainer.getChildCount(); i++) {
            Button b = (Button) d.stratContainer.getChildAt(i);
            b.setEnabled(d.myTurn);
        }
        if (d.bagBtn != null) d.bagBtn.setEnabled(d.myTurn);
    }

    private static void pvpRebuildStrategies(final PvpDuel d) {
        if (d.stratContainer == null) return;
        d.stratContainer.removeAllViews();
        List<Strategy> strats = randomStrategies(4);
        for (final Strategy s : strats) {
            Button btn = new Button(d.ctx);
            btn.setText(s.name + "\n" + s.desc + "\n" + GameApp.T("[هجوم: x", "[Attack: x") + String.format(Locale.US, "%.1f", s.atk) + GameApp.T("| دفاع: x", "| Defense: x") + String.format(Locale.US, "%.1f", s.def) + "]");
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 10, 0, 10);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                GameApp.sound.playSnd("click.mp3");
                pvpExecuteMove(d, s.name, s.atk, s.hpGain, false, s.freezeTurns, s.doubleAttack);
            });
            d.stratContainer.addView(btn);
        }
    }

    private static void pvpShowBag(final PvpDuel d) {
        List<String> items = new ArrayList<>();
        List<ItemData> itemsData = new ArrayList<>();
        for (ItemData it : d.player.inventory) {
            if (it.rarity.equals("نادر") || it.rarity.equals("ملحمي") || it.rarity.equals("أسطوري") || it.rarity.equals("خرافي")) {
                items.add(it.name + GameApp.T("(استخدام)", "(Use)"));
                itemsData.add(it);
            }
        }
        if (items.isEmpty()) {
            U.toast(GameApp.T("لا تملك أدوات مساعدة متاحة!", "You have no support items available!"));
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(d.ctx);
        b.setTitle(GameApp.T("اختر أداة", "Choose an Item"));
        b.setItems(items.toArray(new String[0]), (dd, pos) -> {
            ItemData it = itemsData.get(pos);
            double strBoost = it.boosts.str > 0 ? it.boosts.str : 1;
            pvpExecuteMove(d, GameApp.T("الأداة:", "Item:") + it.name, strBoost, 0, true, 0, false);
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void pvpStartPolling(final PvpDuel d) {
        d.poller.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (d.finished) return;
                pvpPoll(d);
                if (!d.finished) d.poller.postDelayed(this, PVP_POLL_MS);
            }
        }, 400);
    }

    private static void pvpPoll(final PvpDuel d) {
        new Thread(() -> {
            final JSONObject r = Db.get(d.recordKey);
            UI.post(() -> {
                if (d.finished || r == null) return;
                long now = System.currentTimeMillis() / 1000;
                if (r.optBoolean("over", false)) {
                    String winner = r.optString("winner", "");
                    d.finished = true;
                    if (!arenaBattleMode) new Thread(() -> Db.delete(d.recordKey)).start();
                    if (winner.equals(d.myId)) {
                        d.myHp = Math.max(1, r.optDouble(d.mySide + "_hp", 1));
                        GameApp.sound.playSnd("victory.mp3");
                        pvpFinishVictory(d, GameApp.T("حققت النصر على", "You achieved victory over") + d.enemyName + GameApp.T("في المبارزة الاستراتيجية!", "in the strategic duel!"));
                    } else {
                        StringBuilder m = new StringBuilder();
                        JSONObject last = r.optJSONObject("last");
                        if (last != null) {
                            long dmg = last.optLong("dmg", 0);
                            if (dmg > 0 && last.optString("dmg_to", "").equals(d.myId)) {
                                m.append(GameApp.T("تلقيت ضربة من", "You took a hit from")).append(d.enemyName).append(GameApp.T("ونقصت صحتك بمقدار", "and your HP dropped by")).append(dmg).append("!\n");
                                m.append(GameApp.T("الاستراتيجية:", "Strategy:")).append(last.optString("name", "")).append("\n\n");
                            }
                        }
                        m.append(GameApp.T("انتهت المبارزة...", "The duel has ended..."));
                        pvpFinishDefeat(d, m.toString());
                    }
                    return;
                }
                long lastMove = r.optLong("last_move_time", 0);
                if (lastMove > 0 && (now - lastMove) > 120) {
                    String w = r.optString("turn", "").equals(d.myId) ? d.enemyId : d.myId;
                    JSONObject fin = new JSONObject();
                    try {
                        fin.put("over", true);
                        fin.put("winner", w);
                        fin.put("last_move_time", now);
                    } catch (Exception ignored) {}
                    final JSONObject finRec = fin;
                    new Thread(() -> Db.put(d.recordKey, finRec)).start();
                    return;
                }
                double myHp = r.optDouble(d.mySide + "_hp", d.myHp);
                double enHp = r.optDouble(d.enemySide + "_hp", d.enemyHp);
                if (myHp != d.myHp) {
                    d.myHp = myHp;
                    if (d.player.stats.hp != myHp) {
                        d.player.stats.hp = myHp;
                        SaveSystem.save();
                    }
                }
                d.enemyHp = enHp;
                int seq = r.optInt("seq", 0);
                String turn = r.optString("turn", "");
                String msg = null;
                if (seq > d.lastSeq) {
                    d.lastSeq = seq;
                    JSONObject last = r.optJSONObject("last");
                    if (last != null) {
                        long freeze = last.optLong("freeze", 0);
                        long freezeRemaining = last.optLong("freeze_remaining", -1);
                        if (freeze > 0 && !last.optString("by", "").equals(d.myId)) {
                            d.myFreeze = (int) freeze;
                        }
                        if (freezeRemaining >= 0 && !last.optString("by", "").equals(d.myId)) {
                            d.enemyFreeze = (int) freezeRemaining;
                        }
                        if (freezeRemaining >= 0 && last.optString("by", "").equals(d.myId)) {
                            d.enemyFreeze = (int) freezeRemaining;
                        }
                        if (last.optBoolean("freeze_skip", false) && last.optString("by", "").equals(d.myId)) {
                            d.myFreeze = Math.max(0, d.myFreeze - 1);
                        }
                        long dmg = last.optLong("dmg", 0);
                        long heal = last.optLong("heal", 0);
                        String by = last.optString("by", "");
                        String name = last.optString("name", "");
                        if (by.equals(d.myId)) {
                            if (dmg > 0) msg = GameApp.T("وجهت ضربة لـ", "You struck") + d.enemyName + GameApp.T("وتسببت بضرر", "and dealt damage of") + dmg + "!";
                            if (heal > 0) msg = (msg == null ? "" : msg + "\n") + GameApp.T("استعدت صحة قدرها", "You recovered health of") + heal;
                            if (msg == null) msg = GameApp.T("استخدمت:", "You used:") + name;
                        } else {
                            if (dmg > 0) {
                                msg = GameApp.T("تلقيت ضربة من", "You took a hit from") + d.enemyName + GameApp.T("ونقصت صحتك بمقدار", "and your HP dropped by") + dmg + "!";
                                GameApp.sound.playSnd("sword_hit.mp3");
                            } else if (heal > 0) {
                                msg = GameApp.T("استخدم الخصم:", "The enemy used:") + name + GameApp.T("واستعاد صحة قدرها", "and recovered health of") + heal;
                            } else {
                                msg = GameApp.T("استخدم الخصم:", "The enemy used:") + name;
                            }
                        }
                    }
                }
                boolean myTurnNow = turn.equals(d.myId);
                if (myTurnNow != d.myTurn) {
                    d.myTurn = myTurnNow;
                    if (d.myTurn) {
                        if (d.myFreeze > 0) {
                            pvpAutoSkipFrozen(d);
                            return;
                        }
                        pvpRebuildStrategies(d);
                    }
                }
                if (d.arenaDialog != null) pvpUpdateArenaUI(d, msg);
            });
        }).start();
    }

    private static void pvpAutoSkipFrozen(final PvpDuel d) {
        d.myFreeze--;
        d.lastSeq = d.lastSeq + 1;
        final JSONObject rec = new JSONObject();
        try {
            rec.put("status", "started");
            rec.put("turn", d.enemyId);
            rec.put("seq", d.lastSeq);
            rec.put(d.mySide + "_hp", d.myHp);
            rec.put(d.enemySide + "_hp", d.enemyHp);
            JSONObject last = new JSONObject();
            last.put("by", d.myId);
            last.put("freeze_skip", true);
            last.put("freeze_remaining", Math.max(0, d.myFreeze));
            last.put("dmg", 0);
            last.put("name", GameApp.T("تخطي بسبب التجميد", "Frozen skip"));
            rec.put("last", last);
            rec.put("over", false);
            rec.put("winner", "");
            rec.put("last_move_time", System.currentTimeMillis() / 1000);
        } catch (Exception ignored) {}
        final JSONObject recF = rec;
        new Thread(() -> Db.put(d.recordKey, recF)).start();
        d.myTurn = false;
        if (d.arenaDialog != null) pvpUpdateArenaUI(d, GameApp.T("❄️ أنت مجمد! تم تخطي دورك ولا تستطيع اللعب.", "❄️ You are frozen! Your turn was skipped and you cannot act."));
    }

    private static void pvpExecuteMove(final PvpDuel d, final String moveName, final double atkMod, final double hpGainFrac, final boolean isItem, final int freezeTurns, final boolean doubleAttack) {
        final double dmg;
        final double heal;
        if (isItem) {
            dmg = Math.floor(NumberUtil.rand(500, 2000) * atkMod / 100);
            heal = 0;
        } else {
            double defFactor = 1 - ((atkMod - 1) * 0.2);
            defFactor = Math.max(0.6, defFactor);
            dmg = Math.floor((d.myAtk / 10) * atkMod * defFactor * d.tribeAtkBonus + NumberUtil.rand(10, 50)) * (doubleAttack ? 2 : 1);
            heal = hpGainFrac > 0 ? Math.floor(d.myMaxHp * hpGainFrac) : 0;
        }
        if (freezeTurns > 0) d.enemyFreeze = freezeTurns;
        if (heal > 0) {
            d.myHp = Math.min(d.myMaxHp, d.myHp + heal);
            if (d.player.stats.hp != d.myHp) {
                d.player.stats.hp = d.myHp;
                SaveSystem.save();
            }
        }
        double newEnemyHp = d.enemyHp - dmg;
        boolean over = newEnemyHp <= 0;
        if (over) newEnemyHp = 0;
        d.enemyHp = newEnemyHp;
        d.lastSeq = d.lastSeq + 1;
        final double fDmg = dmg;
        final double fHeal = heal;
        final JSONObject rec = new JSONObject();
        try {
            rec.put("status", "started");
            rec.put("turn", d.enemyId);
            rec.put("seq", d.lastSeq);
            rec.put(d.mySide + "_hp", d.myHp);
            rec.put(d.enemySide + "_hp", newEnemyHp);
            JSONObject last = new JSONObject();
            last.put("name", moveName);
            last.put("dmg", (long) fDmg);
            last.put("dmg_to", d.enemyId);
            last.put("heal", (long) fHeal);
            last.put("by", d.myId);
            last.put("freeze", freezeTurns);
            last.put("freeze_remaining", d.enemyFreeze);
            rec.put("last", last);
            rec.put("over", over);
            rec.put("winner", over ? d.myId : "");
            rec.put("last_move_time", System.currentTimeMillis() / 1000);
        } catch (Exception ignored) {}
        new Thread(() -> Db.put(d.recordKey, rec)).start();

        if (fDmg > 0) GameApp.sound.playSnd("sword_hit.mp3");

        d.myTurn = false;
        StringBuilder msg = new StringBuilder(GameApp.T("استخدمت:", "You used:")).append(moveName);
        if (fDmg > 0) msg.append(GameApp.T("\nوجهت ضربة لـ", "\nYou struck")).append(d.enemyName).append(GameApp.T("وتسببت بضرر", "and dealt damage of")).append((long) Math.floor(fDmg)).append("!");
        if (fHeal > 0) msg.append(GameApp.T("\nاستعدت صحة قدرها:", "\nYou recovered health of:")).append((long) Math.floor(fHeal));
        if (freezeTurns > 0) msg.append(GameApp.T("\n❄️ جمّدت الخصم لمدة ", "\n❄️ You froze the opponent for ")).append(freezeTurns).append(GameApp.T(" من أدواره!", " of their turns!"));
        pvpUpdateArenaUI(d, msg.toString());

        if (over) {
            d.finished = true;
            d.player.stats.hp = Math.max(1, d.myHp);
            SaveSystem.saveAndRefresh();
            pvpCloseArena(d);
            pvpFinishVictory(d, msg.toString());
        }
    }

    private static void pvpSurrender(final PvpDuel d) {
        JSONObject rec = new JSONObject();
        try {
            rec.put("over", true);
            rec.put("winner", d.enemyId);
            rec.put("turn", d.enemyId);
            rec.put("last_move_time", System.currentTimeMillis() / 1000);
            rec.put("last", new JSONObject());
        } catch (Exception ignored) {}
        d.finished = true;
        new Thread(() -> Db.put(d.recordKey, rec)).start();
        d.player.stats.hp = Math.max(1, d.myHp);
        SaveSystem.saveAndRefresh();
        pvpCloseArena(d);
        pvpActive = false;
        pvpWaiting = false;
        arenaBattleMode = false;
        arenaBattleId = "";
        new AlertDialog.Builder(d.ctx)
                .setTitle(GameApp.T("انسحبت من المبارزة", "You Withdrew from the Duel"))
                .setView(U.msg(GameApp.T("لقد انسحبت من المبارزة. صحتك كما هي ولم تتغير.", "You withdrew from the duel. Your health remains unchanged.")))
                .setPositiveButton(GameApp.T("حسنا", "OK"), (di, w) -> di.dismiss())
                .setCancelable(false)
                .show();
    }

    private static void pvpFinishVictory(final PvpDuel d, final String msg) {
        GameApp.sound.playSnd("kill_enemy.mp3");
        if (arenaBattleMode) {
            final String aid = arenaBattleId;
            pvpActive = false;
            pvpWaiting = false;
            pvpCloseArena(d);
            try { d.player.stats.hp = Math.max(1, d.myHp); } catch (Exception ignored) {}
            try { SaveSystem.saveAndRefresh(); } catch (Exception ignored) {}
            ArenaSystem.onArenaBattleEnd(aid, d.myId);
            final String fMsg = msg;
            arenaBattleMode = false;
            arenaBattleId = "";
            new AlertDialog.Builder(d.ctx)
                    .setTitle(GameApp.T("انتصار!", "Victory!"))
                    .setView(U.msg(GameApp.T("حققت النصر في معركة الساحة!\n\n", "You achieved victory in the arena battle!\n\n") + fMsg
                            + GameApp.T("\n\nحصلت على جائزة الساحة والذهبية.", "\n\nYou received the arena prize and a medal. Stay in the arena.")))
                    .setPositiveButton(GameApp.T("حسنا", "OK"), (di, w) -> di.dismiss())
                    .setCancelable(false)
                    .show();
            return;
        }
        pvpActive = false;
        pvpWaiting = false;
        pvpCloseArena(d);
        double perf = Math.max(0, Math.min(1, d.myHp / Math.max(1, d.myMaxHp)));
        long base = d.player.level * 5000L;
        long goldReward = base + (long) (perf * base);
        long cryReward = NumberUtil.rand(2, 8) + (long) (perf * 5);
        ItemData rewardItem = ItemGenerator.generateRandomItem(d.player.level, 0);
        d.player.gold += goldReward;
        d.player.crystals += cryReward;
        d.player.inventory.add(rewardItem);
        d.player.stats.hp = Math.max(1, d.myHp);
        SaveSystem.saveAndRefresh();
        final long fGold = goldReward;
        final long fCry = cryReward;
        final ItemData fItem = rewardItem;
        new AlertDialog.Builder(d.ctx)
                .setTitle(GameApp.T("انتصار مستحق!", "Well-Earned Victory!"))
                .setView(U.msg(msg))
                .setPositiveButton(GameApp.T("استلام المكافآت", "Claim Rewards"), (di, w) -> {
                    di.dismiss();
                    new AlertDialog.Builder(d.ctx)
                            .setTitle(GameApp.T("مكافآت الانتصار", "Victory Rewards"))
                            .setView(U.msg(GameApp.T("لقد استلمت:\n\n- معدة:", "You received:\n\n- Item:") + fItem.name + "(" + fItem.rarity + ")" + GameApp.T("\n- ذهب:", "\n- Gold:") + NumberUtil.formatNumber(fGold) + GameApp.T("\n- كريستال:", "\n- Crystal:") + fCry))
                            .setPositiveButton(GameApp.T("تم", "Done"), null)
                            .setCancelable(false)
                            .show();
                })
                .setCancelable(false)
                .show();
    }

    private static void pvpFinishDefeat(final PvpDuel d, final String msg) {
        GameApp.sound.playSnd("death_defeat.mp3");
        if (arenaBattleMode) {
            pvpActive = false;
            pvpWaiting = false;
            pvpCloseArena(d);
            try { d.player.stats.hp = 1; SaveSystem.saveAndRefresh(); } catch (Exception ignored) {}
            final String fMsg = msg;
            arenaBattleMode = false;
            arenaBattleId = "";
            new AlertDialog.Builder(d.ctx)
                    .setTitle(GameApp.T("هزيمة!", "Defeat!"))
                    .setView(U.msg(GameApp.T("خسرت معركة الساحة.\n\n", "You lost the arena battle.\n\n") + fMsg
                            + GameApp.T("\n\nبقيت في الساحة ولا يخرجك منها إلا المالك.", "\n\nYou stayed in the arena; only the host can remove you.")))
                    .setPositiveButton(GameApp.T("حسنا", "OK"), (di, w) -> di.dismiss())
                    .setCancelable(false)
                    .show();
            return;
        }
        pvpActive = false;
        pvpWaiting = false;
        pvpCloseArena(d);
        d.player.stats.hp = 1;
        SaveSystem.saveAndRefresh();
        new AlertDialog.Builder(d.ctx)
                .setTitle(GameApp.T("هزيمة!", "Defeat!"))
                .setView(U.msg(msg + GameApp.T("\n\nلقد سقطت في المعركة. صحتك الآن حرجة جداً!", "\n\nYou fell in battle. Your health is now critical!")))
                .setPositiveButton(GameApp.T("العودة", "Return"), (di, w) -> di.dismiss())
                .setCancelable(false)
                .show();
    }
}
