package com.lli.com.core;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.ui.U;

public class Achievements {

    private static final String[] IDS = {
            "first_blood", "hunter_10", "slayer_100", "beast_500",
            "gold_1k", "gold_100k", "gold_1m",
            "lvl_10", "lvl_50", "lvl_100",
            "diamond_1", "diamond_10",
            "win_5", "win_50"
    };
    private static final String[] TYPE = {
            "kill", "kill", "kill", "kill",
            "gold", "gold", "gold",
            "level", "level", "level",
            "diamond", "diamond",
            "win", "win"
    };
    private static final long[] TARGET = {
            1, 10, 100, 500,
            1000, 100000, 1000000,
            10, 50, 100,
            1, 10,
            5, 50
    };
    private static final String[] AR_T = {
            "أول صيد", "صياد مبتدئ", "حاصد الأرواح", "سيد الصيد",
            "أول أرباح", "تاجر المدينة", "المليونير",
            "الخطوة الأولى", "بطل أسطوري", "حاصل الـ 100",
            "لمسة ألماس", "جامع الجواهر",
            "أول الانتصارات", "سيد الميدان"
    };
    private static final String[] EN_T = {
            "First Blood", "Novice Hunter", "Soul Reaper", "Master of the Hunt",
            "First Earnings", "City Merchant", "The Millionaire",
            "First Step", "Legendary Hero", "Level 100 Master",
            "Diamond Touch", "Gem Collector",
            "First Victories", "Master of the Field"
    };
    private static final String[] AR_D = {
            "اقتل أول وحش في حياتك.", "اقتل 10 وحوش.", "اقتل 100 وحش.", "اقتل 500 وحش.",
            "اجمع 1,000 ذهب.", "اجمع 100,000 ذهب.", "اجمع 1,000,000 ذهب.",
            "ارتقِ إلى المستوى 10.", "ارتقِ إلى المستوى 50.", "ارتقِ إلى المستوى 100.",
            "امتلك ألماسة واحدة.", "امتلك 10 ألماسات.",
            "اربح 5 معارك.", "اربح 50 معركة."
    };
    private static final String[] EN_D = {
            "Kill your first monster.", "Kill 10 monsters.", "Kill 100 monsters.", "Kill 500 monsters.",
            "Collect 1,000 gold.", "Collect 100,000 gold.", "Collect 1,000,000 gold.",
            "Reach level 10.", "Reach level 50.", "Reach level 100.",
            "Own 1 diamond.", "Own 10 diamonds.",
            "Win 5 battles.", "Win 50 battles."
    };
    private static final long[] R_GOLD = {
            1000, 5000, 20000, 100000,
            2000, 50000, 500000,
            3000, 150000, 1000000,
            5000, 0,
            2000, 30000
    };
    private static final long[] R_CRY = {
            0, 0, 5, 15,
            0, 5, 20,
            0, 10, 50,
            0, 10,
            0, 10
    };
    private static final long[] R_EXP = {
            0, 0, 0, 0,
            0, 0, 0,
            0, 0, 0,
            0, 0,
            0, 0
    };
    private static final long[] R_DIA = {
            0, 0, 0, 0,
            0, 0, 1,
            0, 1, 5,
            0, 0,
            0, 0
    };

    private static long stat(String key) {
        Long v = GameApp.player.achStats.get(key);
        return v == null ? 0 : v;
    }

    private static void setStat(String key, long val) {
        GameApp.player.achStats.put(key, val);
    }

    public static void syncStatic() {
        PlayerData p = GameApp.player;
        long target = Math.max(0, p.level - 1);
        if (stat("level") < target) setStat("level", target);
    }

    public static void progress(String type, long amount) {
        if (amount <= 0) return;
        PlayerData p = GameApp.player;
        if (p.achStats == null) p.achStats = new java.util.HashMap<>();
        boolean fresh = false;
        for (int i = 0; i < IDS.length; i++) {
            if (!isClaimed(i) && TYPE[i].equals(type)) {
                long nv = stat(IDS[i]) + amount;
                setStat(IDS[i], nv);
                if (stat(IDS[i]) < TARGET[i] && nv >= TARGET[i]) fresh = true;
            }
        }
        if (fresh) {
            GameApp.sound.playSnd("ach_unlock.mp3");
            GameApp.speakTts(GameApp.T("إنجاز جديد! افتح نظام الإنجازات لاستلام مكافأتك.", "New achievement! Open the achievements system to claim your reward."));
            U.toast(GameApp.T("إنجاز جديد!", "New achievement!"));
        }
    }

    private static boolean isClaimed(int i) {
        return GameApp.player.achClaimed.contains(IDS[i]);
    }

    private static boolean isComplete(int i) {
        return stat(IDS[i]) >= TARGET[i];
    }

    public static String shortDesc(int i) {
        StringBuilder sb = new StringBuilder(GameApp.T(AR_T[i], EN_T[i]));
        sb.append("").append(Math.min(stat(IDS[i]), TARGET[i])).append("/").append(TARGET[i]);
        return sb.toString();
    }

    private static void claim(int i) {
        PlayerData p = GameApp.player;
        if (!isComplete(i) || isClaimed(i)) return;
        p.achClaimed.add(IDS[i]);
        if (p.battleLog == null) p.battleLog = new java.util.ArrayList<>();
        p.gold += R_GOLD[i];
        p.crystals += R_CRY[i];
        p.diamonds += R_DIA[i];
        if (R_EXP[i] > 0) PlayerSystem.addExp(R_EXP[i]);
        GameApp.sound.playSnd("confirm.mp3");
        String m = GameApp.T("رائع! حصلت على:\n", "Great! You got:\n");
        if (R_GOLD[i] > 0) m += "+" + R_GOLD[i] + GameApp.T("ذهب\n", "gold\n");
        if (R_CRY[i] > 0) m += "+" + R_CRY[i] + GameApp.T("كريستال\n", "crystals\n");
        if (R_DIA[i] > 0) m += "+" + R_DIA[i] + GameApp.T("ألماس\n", "diamonds\n");
        if (R_EXP[i] > 0) m += "+" + R_EXP[i] + GameApp.T("خبرة\n", "exp\n");
        SaveSystem.saveAndRefresh();
        U.alert(GameApp.uiCtx(), GameApp.T("مكافأة الإنجاز", "Achievement Reward"),
                GameApp.T("أنجزت:", "Completed:") + GameApp.T(AR_T[i], EN_T[i]) + "\n" + m,
                GameApp.T("حسنا", "OK"), null);
    }

    public static void openUI() {
        syncStatic();
        final Context ctx = GameApp.uiCtx();
        GameApp.sound.playSnd("log_open.mp3");
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        TextView head = U.text(ctx, GameApp.T("نظام الإنجازات", "Achievements System"), 18, U.GOLD, true);
        lay.addView(head);
        LinearLayout rows = U.linear(ctx, true);
        final ScrollView sc = U.scroll(ctx, rows);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        lay.addView(sc);
        final AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(GameApp.T("الإنجازات", "Achievements"));
        builder.setView(lay);
        builder.setCancelable(true);
        final AlertDialog dialog = builder.show();
        Button close = U.btn(ctx, GameApp.T("إغلاق", "Close"));
        close.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            dialog.dismiss();
        });
        lay.addView(close);

        final Runnable[] rebuild = {null};
        rebuild[0] = () -> {
            rows.removeAllViews();
            for (int i = 0; i < IDS.length; i++) {
                final int idx = i;
                LinearLayout row = U.linear(ctx, true);
                String status;
                if (isClaimed(i)) status = GameApp.T("[مكتمل ]", "[Claimed ]");
                else if (isComplete(i)) status = GameApp.T("[جاهز للاستلام]", "[Ready to claim]");
                else status = "[" + Math.min(stat(IDS[i]), TARGET[i]) + "/" + TARGET[i] + "]";
                String rew = GameApp.T("المكافأة:", "Reward:") + rewardText(i);
                TextView tv = U.text(ctx, "" + GameApp.T(AR_T[i], EN_T[i]) + status + "\n" + GameApp.T(AR_D[i], EN_D[i]) + "\n" + rew, 12, U.WHITE, false);
                tv.setPadding(10, 10, 10, 10);
                row.addView(tv);
                if (!isClaimed(i) && isComplete(i)) {
                    Button cl = U.btn(ctx, GameApp.T("استلام", "Claim"));
                    cl.setOnClickListener(v -> {
                        claim(idx);
                        rebuild[0].run();
                    });
                    row.addView(cl);
                }
                rows.addView(row);
            }
        };
        rebuild[0].run();
        GameApp.speakTts(GameApp.T("نظام الإنجازات. أكمل المهام واكسب المكافآت.", "Achievements system. Complete tasks and earn rewards."));
    }

    private static String rewardText(int i) {
        StringBuilder sb = new StringBuilder();
        if (R_GOLD[i] > 0) sb.append(R_GOLD[i]).append(GameApp.T("ذهب", "gold"));
        if (R_CRY[i] > 0) sb.append(R_CRY[i]).append(GameApp.T("كريستال", "crystals"));
        if (R_DIA[i] > 0) sb.append(R_DIA[i]).append(GameApp.T("ألماس", "diamonds"));
        if (R_EXP[i] > 0) sb.append(R_EXP[i]).append(GameApp.T("خبرة", "exp"));
        return sb.length() == 0 ? GameApp.T("لا شيء", "Nothing") : sb.toString().trim();
    }
}