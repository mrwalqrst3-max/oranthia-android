package com.lli.com.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;
import com.lli.com.core.ServerTime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WorldBossSystem {
    private static final String BOSS_PATH = "world_boss/current_status.json";
    private static final String[] BOSS_NAMES = {
            "غول الغابات الناشئ", "ذئب الثلج العاوي", "عنكبوت الكهوف السام", "هيكل عظمي مدرع", "ساحر الظلام المبتدئ",
            "تنين النار الصغير", "عملاق الصخور الثائر", "شبح القصر المهجور", "فارس الموت المتجول", "ملك العقارب الرملي",
            "حارس البوابة الجحيمية", "مستحضر الأرواح الخبيث", "وحش البحيرة الغامض", "سيد الرعد الغاضب", "شيطان الرماد المستعر",
            "تنين الجليد الأزرق", "محارب الفراغ المظلم", "كراكن الأعماق السحيقة", "مدمر الأسوار العظيم", "ملك العمالقة القديم",
            "سيد العناصر المتمرس", "تنين البرق الأسطوري", "حاكم الأبعاد المنسي", "فارس النور الساقط", "شبح الإمبراطور الراحل",
            "وحش الكوابيس المرعب", "سيد الظلال الأبدية", "تنين الكسوف المظلم", "حارس العرش السماوي", "مدمر العوالم الناشئ",
            "ملك الشياطين الأعلى", "تنين الأزل الذهبي", "سيد الوجود المطلق", "محطم الأقدار الكوني", "قاهر الفناء العظيم",
            "حاكم الزمان والمكان", "روح الكون الثائرة", "تنين النهاية الأسود", "سيد الخلود والأزل", "إمبراطور الغزو المطلق"
    };

    private static final String[] BOSS_NAMES_EN = {
            "Rising Forest Ogre", "Howling Snow Wolf", "Venomous Cave Spider", "Armored Skeleton", "Novice Dark Mage",
            "Young Fire Dragon", "Raging Rock Giant", "Haunted Palace Ghost", "Wandering Death Knight", "Sand Scorpion King",
            "Hell Gate Guardian", "Malicious Necromancer", "Mysterious Lake Monster", "Angry Thunder Lord", "Blazing Ash Demon",
            "Blue Ice Dragon", "Dark Void Warrior", "Abyssal Kraken", "Great Wallbreaker", "Ancient Giant King",
            "Experienced Elemental Lord", "Legendary Lightning Dragon", "Forgotten Dimension Ruler", "Fallen Knight of Light", "Ghost of the Departed Emperor",
            "Terrifying Nightmare Beast", "Eternal Shadows Lord", "Dark Eclipse Dragon", "Heavenly Throne Guardian", "Rising World Destroyer",
            "Supreme Demon King", "Golden Eternity Dragon", "Absolute Existence Lord", "Cosmic Destiny Breaker", "Great Vanquisher of Doom",
            "Ruler of Time and Space", "Raging Spirit of the Universe", "Black End Dragon", "Lord of Immortality and Eternity", "Emperor of Absolute Conquest"
    };

    private static String bossName(String ar) {
        if (ar == null) return ar;
        for (int i = 0; i < BOSS_NAMES.length; i++) {
            if (BOSS_NAMES[i].equals(ar)) return GameApp.T(ar, BOSS_NAMES_EN[i]);
        }
        return ar;
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static void openWorldBossUI() {
        final Context ctx = GameApp.uiCtx();
        U.toast(GameApp.T("جاري الاتصال بساحة المعركة...", "Connecting to the battlefield..."));
        new Thread(() -> {
            JSONObject boss = Db.get(BOSS_PATH);
            long curTime = ServerTime.now();
            if (boss == null || (boss.optBoolean("is_dead", false) && curTime >= boss.optLong("respawn_time", 0))) {
                int index = (boss != null ? boss.optInt("index", 0) : 0) + 1;
                spawnNewWorldBoss(index, () -> UI.post(() -> openWorldBossUI()));
            } else {
                final JSONObject b = boss;
                UI.post(() -> showBossStatusUI(b));
            }
        }).start();
    }

    private static void spawnNewWorldBoss(int index, Runnable onDone) {
        if (index > BOSS_NAMES.length) index = 1;
        final int finalIndex = index;
        new Thread(() -> {
            long multiplier = pow10(finalIndex - 1);
            JSONObject nb = new JSONObject();
            try {
                nb.put("index", finalIndex);
                nb.put("name", BOSS_NAMES[finalIndex - 1]);
                nb.put("max_hp", 1000000L * multiplier);
                nb.put("current_hp", 1000000L * multiplier);
                nb.put("atk", 1000L * multiplier);
                nb.put("reward_gold", 1000000L * finalIndex);
                nb.put("reward_crystals", 5L * finalIndex);
                nb.put("reward_diamonds", (long) finalIndex);
                nb.put("is_dead", false);
                nb.put("damage_list", new JSONObject());
                nb.put("spawn_time", ServerTime.now());
            } catch (Exception ignored) {}
            Db.put(BOSS_PATH, nb);
            GameApp.sound.playSnd("boss_growl.mp3");
            UI.post(onDone);
        }).start();
    }

    private static long pow10(int e) {
        long r = 1;
        for (int i = 0; i < e; i++) r *= 10;
        return r;
    }

    private static void showBossStatusUI(final JSONObject boss) {
        final Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        if (boss.optBoolean("is_dead", false)) {
            long waitTime = boss.optLong("respawn_time", 0) - ServerTime.now();
            if (waitTime > 0) {
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("الزعيم العالمي:", "World Boss:") + bossName(boss.optString("name", GameApp.T("غير معروف", "Unknown"))));
                b.setView(U.msg(GameApp.T("الحالة: ميت \nالقاتل الأخير:", "Status: Dead \nLast killer:") + (boss.optString("last_attacker", GameApp.T("غير معروف", "Unknown")))
                        + GameApp.T("\n\nالزعيم مهزوم حالياً! سيظهر الزعيم القادم بعد:", "\n\nThe boss is currently defeated! The next boss will appear in:") + NumberUtil.formatRemainingTime(boss.optLong("respawn_time", 0))));
                b.setPositiveButton(GameApp.T("تحديث", "Refresh"), (d, w) -> openWorldBossUI());
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
                return;
            }
        }

        long maxHp = boss.optLong("max_hp", 1);
        long curHp = boss.optLong("current_hp", 0);
        int healthPercent = (int) Math.floor(((curHp) / (double) Math.max(maxHp, 1)) * 100);
        String msg = GameApp.T("الاسم:", "Name:") + bossName(boss.optString("name", GameApp.T("غير معروف", "Unknown"))) + GameApp.T("(رقم", "(Number") + boss.optInt("index", 0) + ")\n"
                + GameApp.T("الصحة:", "Health:") + NumberUtil.formatNumber(curHp) + "/" + NumberUtil.formatNumber(maxHp) + "(" + healthPercent + "%)\n"
                + GameApp.T("القوة الهجومية:", "Attack Power:") + NumberUtil.formatNumber(boss.optLong("atk", 0)) + "\n\n"
                + GameApp.T("المحاولات المجانية المتبقية:", "Free attempts left:") + Math.max(0, 5 - p.dailyInvasionFree) + "\n"
                + GameApp.T("المحاولات المدفوعة المتبقية:", "Paid attempts left:") + Math.max(0, 5 - p.dailyInvasionPaid);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الزعيم العالمي النشط", "Active World Boss"));
        b.setView(U.msg(msg));
        b.setPositiveButton(GameApp.T("هجوم!", "Attack!"), (d, w) -> attackWorldBoss(boss));
        b.setNeutralButton(GameApp.T("لوحة متصدري الضرر", "Damage Leaderboard"), (d, w) -> showBossDamageLeaderboard(boss.optJSONObject("damage_list")));
        b.setNegativeButton(GameApp.T("تحديث", "Refresh"), (d, w) -> openWorldBossUI());
        b.show();
    }

    private static void attackWorldBoss(final JSONObject boss) {
        final Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        String curDay = String.valueOf(ServerTime.now() / 86400);
        if (!p.lastDailyReset.equals(curDay)) {
            p.dailyInvasionFree = 0;
            p.dailyInvasionPaid = 0;
            p.lastDailyReset = curDay;
        }
        int freeLeft = Math.max(0, 5 - p.dailyInvasionFree);
        int paidLeft = Math.max(0, 5 - p.dailyInvasionPaid);

        if (freeLeft <= 0 && paidLeft <= 0) {
            U.alert(ctx, null, GameApp.T("لقد استنفدت جميع محاولاتك لليوم (5 مجانية و 5 مدفوعة)! يرجى الانتظار حتى الغد (24 ساعة) لإعادة تعيين جميع المحاولات.", "You have exhausted all your attempts for today (5 free and 5 paid)! Please wait until tomorrow (24 hours) for all attempts to reset."), GameApp.T("حسنا", "OK"), null);
            return;
        }

        if (freeLeft > 0) {
            long curTime = ServerTime.now();
            long waitNeeded = 3600 - (curTime - p.lastInvasionTime);
            if (waitNeeded > 0) {
                long mins = (long) Math.ceil(waitNeeded / 60.0);
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setView(U.msg(GameApp.T("يجب الانتظار", "You must wait") + mins + GameApp.T("دقيقة للمحاولة المجانية القادمة. أو استخدم محاولة مدفوعة (20 كريستال و 3 ألماس).", "minutes for the next free attempt. Or use a paid attempt (20 crystals and 3 diamonds).")));
                b.setPositiveButton(GameApp.T("استخدام محاولة مدفوعة", "Use a Paid Attempt"), (d, w) -> usePaidAttempt(boss, true));
                b.setNegativeButton(GameApp.T("انتظر", "Wait"), null);
                b.show();
                return;
            }
            p.dailyInvasionFree = Math.min(5, p.dailyInvasionFree + 1);
            p.lastInvasionTime = curTime;
            executeBossAttack(boss);
        } else {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("محاولة مدفوعة", "Paid Attempt"));
            b.setView(U.msg(GameApp.T("هل تريد استخدام محاولة مدفوعة مقابل 20 كريستال و 3 ألماس؟", "Do you want to use a paid attempt for 20 crystals and 3 diamonds?")));
            b.setPositiveButton(GameApp.T("نعم", "Yes"), (d, w) -> usePaidAttempt(boss, false));
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        }
    }

    private static void usePaidAttempt(final JSONObject boss, boolean checkWaitDialog) {
        final Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        int paidLeft = Math.max(0, 5 - p.dailyInvasionPaid);
        if (paidLeft <= 0) {
            U.alert(ctx, null, GameApp.T("لقد استنفدت جميع المحاولات المدفوعة!", "You have exhausted all your paid attempts!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        if (p.crystals >= 20 && p.diamonds >= 3) {
            p.crystals -= 20;
            p.diamonds -= 3;
            p.dailyInvasionPaid = Math.min(5, p.dailyInvasionPaid + 1);
            executeBossAttack(boss);
        } else {
            U.alert(ctx, null, GameApp.T("ليس لديك موارد كافية (20 كريستال و 3 ألماس)!", "You don't have enough resources (20 crystals and 3 diamonds)!"), GameApp.T("حسنا", "OK"), null);
        }
    }

    private static void executeBossAttack(final JSONObject boss) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final double damage = Math.floor(p.stats.strength * (NumberUtil.rand(80, 120) / 100.0));
        new Thread(() -> {
            JSONObject currentBoss = Db.get(BOSS_PATH);
            if (currentBoss == null || currentBoss.optBoolean("is_dead", false)) {
                UI.post(() -> {
                    GameApp.speakTts(GameApp.T("الزعيم مات أو حدث خطأ.", "The boss is dead or an error occurred."));
                    openWorldBossUI();
                });
                return;
            }
            try {
                double newHp = Math.max(0, currentBoss.optDouble("current_hp", 0) - damage);
                currentBoss.put("current_hp", newHp);
                JSONObject dmgList = currentBoss.optJSONObject("damage_list");
                if (dmgList == null) dmgList = new JSONObject();
                JSONObject myRecord = dmgList.optJSONObject(p.username);
                if (myRecord == null) myRecord = new JSONObject();
                myRecord.put("dmg", myRecord.optDouble("dmg", 0) + damage);
                myRecord.put("player_name", p.name);
                dmgList.put(p.username, myRecord);
                currentBoss.put("damage_list", dmgList);

                final StringBuilder finalMsg = new StringBuilder();
                if (newHp <= 0) {
                    currentBoss.put("is_dead", true);
                    currentBoss.put("respawn_time", ServerTime.now() + 3600);
                    currentBoss.put("last_attacker", p.name);
                    p.gold += currentBoss.optLong("reward_gold", 0);
                    p.crystals += currentBoss.optLong("reward_crystals", 0);
                    p.diamonds += currentBoss.optLong("reward_diamonds", 0);
                    GameApp.sound.playSnd("thunder.mp3");
                    GameApp.sound.playSnd("victory.mp3");
                    GameApp.sound.playSnd("kill_enemy.mp3");
                    GameApp.speakTts(GameApp.T("مبروك! قتلت الزعيم وحصلت على الجوائز!", "Congratulations! You killed the boss and got the rewards!"));
                    finalMsg.append(GameApp.T("مبروك! لقد قتلت الزعيم وحصلت على الجوائز!", "Congratulations! You killed the boss and received the rewards!"));
                } else {
                    double counterDmg = Math.floor(currentBoss.optDouble("atk", 1000) * (NumberUtil.rand(50, 100) / 100.0));
                    double pDef = p.stats.endurance + p.tempStats.endurance;
                    counterDmg = Math.max(10, counterDmg - Math.floor(pDef / 2.0));
                    p.stats.hp = Math.max(1, p.stats.hp - counterDmg);
                    GameApp.sound.playSnd("sword_hit.mp3");
                    GameApp.sound.playSnd("boss_growl.mp3");
                    GameApp.speakTts(GameApp.T("ضربت الزعيم بـ", "You hit the boss for") + NumberUtil.formatNumber(damage) + GameApp.T("ضرر!", "damage!"));
                    finalMsg.append(GameApp.T("ضربت الزعيم بـ", "You hit the boss for")).append(NumberUtil.formatNumber(damage))
                            .append(GameApp.T("ضرر!\n\n رد الزعيم الهجوم وسبب لك", "damage!\n\nThe boss retaliated and dealt you"))
                            .append(NumberUtil.formatNumber(counterDmg)).append(GameApp.T("ضرر!", "damage!"));
                }

                Db.put(BOSS_PATH, currentBoss);
                final JSONObject fBoss = currentBoss;
                final String fMsg = finalMsg.toString();
                UI.post(() -> {
                    SaveSystem.saveAndRefresh();
                    AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                    b.setTitle(GameApp.T("نتيجة الهجوم", "Attack Result"));
                    b.setView(U.msg(fMsg));
                    b.setPositiveButton(GameApp.T("حسنا", "OK"), (d, w) -> showBossStatusUI(fBoss));
                    b.show();
                });
            } catch (Exception e) {
                UI.post(() -> openWorldBossUI());
            }
        }).start();
    }

    private static void showBossDamageLeaderboard(JSONObject damageList) {
        final Context ctx = GameApp.uiCtx();
        if (damageList == null) {
            U.alert(ctx, null, GameApp.T("لم يسجل أحد ضرراً بعد", "No one has dealt damage yet"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        List<JSONObject> list = new ArrayList<>();
        JSONArray names = damageList.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                JSONObject rec = damageList.optJSONObject(names.optString(i));
                if (rec != null) {
                    list.add(rec);
                }
            }
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Double.compare(b.optDouble("dmg", 0), a.optDouble("dmg", 0));
            }
        });
        List<String> items = new ArrayList<>();
        for (int i = 0; i < list.size() && i < 10; i++) {
            JSONObject v = list.get(i);
            items.add((i + 1) + "." + v.optString("player_name", GameApp.T("غير معروف", "Unknown")) + ":" + NumberUtil.formatNumber(v.optDouble("dmg", 0)) + GameApp.T("ضرر", "damage"));
        }
        if (items.isEmpty()) {
            U.alert(ctx, null, GameApp.T("لم يسجل أحد ضرراً بعد", "No one has dealt damage yet"), GameApp.T("حسنا", "OK"), null);
        } else {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("متصدري ضرر الزعيم", "Top Boss Damage Dealers"));
            b.setItems(items.toArray(new String[0]), null);
            b.setPositiveButton(GameApp.T("موافق", "OK"), null);
            b.show();
        }
    }
}
