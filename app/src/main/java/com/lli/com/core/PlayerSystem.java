package com.lli.com.core;

import android.app.Activity;
import android.app.AlertDialog;

import com.lli.com.GameApp;
import com.lli.com.ui.GameNav;
import com.lli.com.ui.U;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlayerSystem {

    public static void initNewPlayer() {
        GameApp.player = new PlayerData();
        GameApp.player.steps = 0;
        GameApp.player.targetSteps = NumberUtil.rand(5, 8);
        GameApp.player.stats = new Stats();
        GameApp.player.tempStats = new TempStats();
        GameApp.player.lockedChests.clear();
        GameApp.player.currentQuest = new Quest();
        SaveSystem.save();
    }

    public static double getFullLevelExp(int level) {
        return 2000 * Math.pow(1.2, level - 1);
    }

    public static void addExp(double amount) {
        if (amount <= 0) return;
        PlayerData p = GameApp.player;
        double fullExpNeeded = getFullLevelExp(p.level);
        double percentageGain = (amount / fullExpNeeded) * 100;
        p.exp += percentageGain;
        while (p.exp >= 100) {
            double overflowPercentage = p.exp - 100;
            double overflowPoints = (overflowPercentage / 100) * getFullLevelExp(p.level);
            p.level = p.level + 1;
            p.exp = (overflowPoints / getFullLevelExp(p.level)) * 100;
            Achievements.progress("level", 1);
            p.stats.maxHp += 500;
            p.stats.hp = p.stats.maxHp;
            p.stats.strength += 5;
            p.stats.endurance += 3;
            p.points += 4;
            final int lvl = p.level;
            GameApp.sound.playSnd("levelup.mp3");
            GameApp.sound.playSnd("reward_get.mp3");
            AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
            b.setTitle(GameApp.T("ترقية مستوى جديد!", "Level Up!"));
            b.setView(U.msg(GameApp.T("مبروك يا بطل! لقد وصلت إلى المستوى", "Congratulations, hero! You reached level") + lvl +
                    GameApp.T("\nتمت زيادة صحتك القصوى وتعبئتها!\nحصلت على 4 نقاط تطوير جديدة.", "\nYour max HP increased and was refilled!\nYou got 4 new upgrade points.")));
            b.setPositiveButton(GameApp.T("عظيم", "Awesome"), (d, w) -> {});
            b.show();
        }
        SaveSystem.saveAndRefresh();
    }

    public static void addPlayerNews(String msg) {
        PlayerData p = GameApp.player;
        if (p.news == null) p.news = new java.util.ArrayList<>();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        p.news.add(0, "[" + timestamp + "]" + msg);
        while (p.news.size() > 50) p.news.remove(p.news.size() - 1);
    }

    public static void addPlayerNews(String arMsg, String enMsg) {
        if (arMsg == null) arMsg = "";
        if (enMsg == null) enMsg = "";
        addPlayerNews(arMsg + com.lli.com.core.BattleLog.SEP + enMsg);
    }

    public static String displayNews(String rawLine) {
        if (rawLine == null) return "";
        return com.lli.com.core.BattleLog.display(rawLine);
    }

    public static void useDiamond(String type) {
        PlayerData p = GameApp.player;
        if (p.diamonds <= 0) {
            U.alert(GameApp.uiCtx(), null, GameApp.T("لا تملك ألماس كافٍ!", "You don't have enough diamonds!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        if (type.equals("power")) {
            if (p.diamonds < 5) {
                U.alert(GameApp.uiCtx(), null, GameApp.T("تحتاج 5 ألماس للتعزيز!", "You need 5 diamonds for the boost!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            p.diamonds -= 5;
            p.stats.strength += 50;
            p.stats.endurance += 25;
            U.alert(GameApp.uiCtx(), GameApp.T("تم التعزيز", "Boost Applied"), GameApp.T("تم استخدام 5 ألماس:\n+50 هجوم دائم\n+25 دفاع دائم", "Used 5 diamonds:\n+50 permanent attack\n+25 permanent defense"), GameApp.T("رائع", "Great"), null);
        } else if (type.equals("full_heal")) {
            if (p.stats.hp >= p.stats.maxHp) {
                U.alert(GameApp.uiCtx(), GameApp.T("تنبيه", "Notice"), GameApp.T("لا يمكنك استخدام هذه الميزة لأن طاقتك ممتلئة بالفعل!", "You can't use this feature because your energy is already full!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            if (p.diamonds < 3) {
                U.alert(GameApp.uiCtx(), null, GameApp.T("تحتاج 3 ألماس للتجديد!", "You need 3 diamonds for the refill!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            p.diamonds -= 3;
            p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
            U.alert(GameApp.uiCtx(), GameApp.T("تم التجديد", "Refilled"), GameApp.T("تم استخدام 3 ألماس واستعادة كامل طاقتك!", "Used 3 diamonds and restored all your energy!"), GameApp.T("رائع", "Great"), null);
        } else if (type.equals("luck_boost")) {
            if (p.diamonds < 5) {
                U.alert(GameApp.uiCtx(), null, GameApp.T("تحتاج 5 ألماس للتعزيز!", "You need 5 diamonds for the boost!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            p.diamonds -= 5;
            p.stats.luck += 10;
            U.alert(GameApp.uiCtx(), GameApp.T("تم التعزيز", "Boost Applied"), GameApp.T("تم استخدام 5 ألماس:\n+10 حظ دائم", "Used 5 diamonds:\n+10 permanent luck"), GameApp.T("رائع", "Great"), null);
        } else if (type.equals("instant_level")) {
            if (p.diamonds >= 30) {
                p.diamonds -= 30;
                p.level += 1;
                p.points += 4;
                p.exp = 0;
                p.stats.maxHp += 50;
                p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
                U.alert(GameApp.uiCtx(), GameApp.T("مستوى جديد!", "New Level!"), GameApp.T("تم استخدام 30 ألماس:\nوصلت للمستوى", "Used 30 diamonds:\nYou reached level") + p.level +
                        GameApp.T("\nحصلت على 4 نقاط تطوير وبونص صحة!", "\nYou got 4 upgrade points and an HP bonus!"), GameApp.T("عظيم", "Awesome"), null);
            } else {
                U.alert(GameApp.uiCtx(), null, GameApp.T("تحتاج 30 ألماس للارتقاء الفوري!", "You need 30 diamonds for an instant level up!"), GameApp.T("حسنا", "OK"), null);
            }
        }
        SaveSystem.saveAndRefresh();
    }

    public static void endBattleProcessing(boolean isDragon) {
        PlayerData p = GameApp.player;
        if (p.tempStats != null) {
            if (p.tempStats.isTimeType()) {
                p.tempStats.expireIfPast();
            } else if (p.tempStats.battlesLeft > 0) {
                p.tempStats.battlesLeft -= 1;
                if (p.tempStats.battlesLeft <= 0) {
                    p.tempStats = new TempStats();
                }
            }
        } else {
            p.tempStats = new TempStats();
        }
        p.clampHpToMax();
        if (p.currentQuest != null && p.currentQuest.active) {
            if (p.currentQuest.type.equals("kill") || (p.currentQuest.type.equals("kill_dragon") && isDragon)) {
                p.currentQuest.current += 1;
                checkQuestCompletion();
            }
        }
        if (p.lockedChests != null) {
            for (ChestData chest : p.lockedChests) {
                if (chest.done < chest.req) chest.done += 1;
            }
        }
        SaveSystem.saveAndRefresh();
    }

    public static void checkQuestCompletion() {
        PlayerData p = GameApp.player;
        if (p.currentQuest != null && p.currentQuest.active) {
            if (p.currentQuest.current >= p.currentQuest.target) {
                GameApp.sound.playSnd("reward.mp3");
                U.alert(GameApp.uiCtx(), null, GameApp.T("!!! أنجزت المهمة! اذهب لنقابة المرتزقة لاستلام جائزتك !!!", "!!! Quest completed! Go to the Mercenary Guild to claim your reward !!!"), GameApp.T("حسنا", "OK"), null);
            }
        }
    }
}
