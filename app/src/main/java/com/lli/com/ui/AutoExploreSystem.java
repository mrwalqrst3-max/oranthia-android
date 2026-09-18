package com.lli.com.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

public class AutoExploreSystem {
    private static Handler tickHandler;

    private static boolean isDarkZone(String loc) {
        return loc.contains("الشياطين") || loc.contains("بوابة الظلام") || loc.contains("عرش الفراغ")
                || loc.contains("المجرة") || loc.contains("أعماق الهاوية") || loc.contains("التنانين");
    }

    public static long zoneMult(String loc) {
        if (loc.contains("الاسود")) return 5;
        if (loc.contains("الشياطين")) return 15;
        if (loc.contains("التنانين")) return 50;
        if (loc.contains("بوابة الظلام")) return 5000;
        if (loc.contains("عرش الفراغ")) return 12000;
        if (loc.contains("المجرة")) return 30000;
        if (loc.contains("جزيرة الأرواح")) return 60;
        if (loc.contains("مملكة السماء")) return 150;
        if (loc.contains("أعماق الهاوية")) return 1000;
        return 1;
    }

    public static boolean isRunning() {
        PlayerData p = GameApp.player;
        return p.autoStart > 0;
    }

    private static long remainingMs() {
        PlayerData p = GameApp.player;
        long target = p.autoStart + p.autoDuration * 60000L;
        return Math.max(0, target - System.currentTimeMillis());
    }

    public static boolean isReady() {
        if (!isRunning()) return false;
        return remainingMs() <= 0;
    }

    private static String zoneName() {
        String loc = GameApp.player.location;
        if (loc == null || loc.equals("المنطقة الآمنة") || loc.isEmpty()) {
            return GameApp.T("ساحة التدريب المجاورة", "Nearby Training Grounds");
        }
        return loc;
    }

    private static String[] durationOptions() {
        return new String[]{
                GameApp.T("5 دقائق (مجاناً)", "5 minutes (free)"),
                GameApp.T("15 دقيقة (30 ذهب)", "15 minutes (30 gold)"),
                GameApp.T("30 دقيقة (80 ذهب)", "30 minutes (80 gold)"),
                GameApp.T("60 دقيقة (200 ذهب)", "60 minutes (200 gold)")
        };
    }

    private static boolean canAfford(int idx) {
        switch (idx) {
            case 1: return GameApp.player.gold >= 30;
            case 2: return GameApp.player.gold >= 80;
            case 3: return GameApp.player.gold >= 200;
            default: return true;
        }
    }

    private static void payCost(int idx) {
        switch (idx) {
            case 1: GameApp.player.gold -= 30; break;
            case 2: GameApp.player.gold -= 80; break;
            case 3: GameApp.player.gold -= 200; break;
        }
    }

    public static void openUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final boolean dark = isDarkZone(p.location);
        final long mult = zoneMult(p.location);

        final LinearLayout lay = U.linear(ctx, true);
        lay.setBackgroundColor(U.BLACK);
        lay.setPadding(30, 30, 30, 30);

        final ArtView art = new ArtView(ctx, dark,
                GameApp.T("مشهد الاستكشاف التلقائي في", "Auto-Exploration scene in") + zoneName() + GameApp.T("- ارسمة متحركة", "- animated graphic"),
                GameApp.T("رسمة متحركة تعرض بطل الاستكشاف في المنطقة.", "Animated graphic showing the hero exploring the zone."), 150);
        lay.addView(art);

        final TextView status = U.text(ctx, "", 14, U.GOLD, false);
        status.setPadding(20, 20, 20, 20);
        status.setTextSize(14);
        lay.addView(status);

        final TextView zoneInfo = U.text(ctx,
                GameApp.T("المنطقة:", "Zone:") + zoneName() + GameApp.T("\nمضاعف الغنيمة:", "\nReward multiplier:") + mult + "x",
                12, U.WHITE, false);
        zoneInfo.setPadding(10, 0, 10, 15);
        lay.addView(zoneInfo);

        final Runnable[] paint = new Runnable[1];
        final boolean[] collected = {false};

        paint[0] = () -> {
            if (isRunning() && !isReady()) {
                long rem = remainingMs();
                long mm = rem / 60000;
                long ss = (rem / 1000) % 60;
                status.setText(GameApp.T("البطل يستكشف...", "The hero is exploring...") + mm + ":" + String.format(java.util.Locale.US, "%02d", ss)
                        + GameApp.T("\nغادر الشاشة وارجع لاحقاً لجمع الغنائم!", "\nLeave the screen and come back later to collect the loot!"));
                if (ss == 0 && mm > 0) GameApp.sound.playSnd("tick.mp3");
            } else if (isReady()) {
                status.setText(GameApp.T("اكتمل الاستكشاف! اضغط (جمع الغنيمة)!", "Exploration complete! Press (Collect the loot)!"));
            } else {
                status.setText(GameApp.T("اختر مدة الاستكشاف ليذهب البطل لجمع الذهب والخبرة تلقائياً.", "Choose an exploration duration and the hero will collect gold and exp automatically."));
            }
            tickHandler.postDelayed(paint[0], 1000);
        };

        if (tickHandler != null) tickHandler.removeCallbacksAndMessages(null);
        tickHandler = new Handler();
        paint[0].run();

        final String[] options = durationOptions();
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            Button b = U.btn(ctx, options[i]);
            b.setOnClickListener(v -> {
                GameApp.sound.playSnd("click.mp3");
                if (isRunning()) {
                    U.alert(ctx, null, GameApp.T("الاستكشاف يعمل بالفعل! اجمع الغنيمة أولاً.", "Exploration is already running! Collect the loot first."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                if (!canAfford(idx)) {
                    U.alert(ctx, null, GameApp.T("الذهب غير كافٍ لهذه المدة!", "Not enough gold for this duration!"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                payCost(idx);
                p.autoStart = System.currentTimeMillis();
                p.autoDuration = (idx == 0 ? 5 : idx == 1 ? 15 : idx == 2 ? 30 : 60);
                SaveSystem.saveAndRefresh();
                GameApp.sound.playSnd("auto_start.mp3");
                GameApp.speakTts(GameApp.T("بدأ الاستكشاف التلقائي في", "Auto-exploration started in") + zoneName() + GameApp.T("لمدة", "for") + p.autoDuration + GameApp.T("دقيقة.", "minutes."));
                U.alert(ctx, GameApp.T("بدأ الاستكشاف!", "Exploration Started!"),
                        GameApp.T("البطل سيجمع الغنائم تلقائياً. ارجع بعد", "The hero will collect loot automatically. Come back after") + p.autoDuration + GameApp.T("دقيقة لجمعها.", "minutes to collect."),
                        GameApp.T("حسنا", "OK"), null);
                paint[0].run();
            });
            lay.addView(b);
        }

        Button collect = U.btn(ctx, GameApp.T("جمع الغنيمة", "Collect the loot"));
        collect.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            if (!isRunning()) {
                U.alert(ctx, null, GameApp.T("لا يوجد استكشاف جارٍ!", "No exploration is running!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            if (!isReady()) {
                long rem = remainingMs();
                U.alert(ctx, null, GameApp.T("لم يكتمل الاستكشاف بعد. المتبقي:", "Exploration isn't finished yet. Remaining:") + (rem / 60000) + GameApp.T("دقيقة", "minutes"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            long elapsedMin = Math.max(0, (System.currentTimeMillis() - p.autoStart) / 60000L);
            if (elapsedMin <= 0) elapsedMin = p.autoDuration;
            if (elapsedMin > p.autoDuration) elapsedMin = p.autoDuration;
            long gold = mult * (100 + (long) p.level * 50) * elapsedMin;
            long exp = mult * (60 + (long) p.level * 30) * elapsedMin;
            long crystals = 0;
            for (int t = 5; t <= elapsedMin; t += 5) {
                if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 25) crystals++;
            }
            long diamonds = 0;
            for (int t = 1; t <= elapsedMin; t++) {
                if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 1) diamonds++;
            }
            p.gold += gold;
            p.crystals += crystals;
            p.diamonds += diamonds;
            com.lli.com.core.PlayerSystem.addExp(exp);
            p.autoStart = 0;
            collected[0] = true;
            SaveSystem.saveAndRefresh();
            String msg = GameApp.T("جمع البطل:\n", "The hero collected:\n")
                    + "+" + NumberUtil.formatNumber(gold) + GameApp.T("ذهب\n", "gold\n")
                    + "+" + NumberUtil.formatNumber(exp) + GameApp.T("خبرة\n", "exp\n");
            if (crystals > 0) msg += "+" + crystals + GameApp.T("كريستال\n", "crystals\n");
            if (diamonds > 0) msg += "+" + diamonds + GameApp.T("ألماس\n", "diamonds\n");
            GameApp.sound.playSnd("auto_done.mp3");
            GameApp.speakTts(msg);
            GameApp.sound.playSnd("reward.mp3");
            U.alert(ctx, GameApp.T("غنيمة الاستكشاف!", "Exploration Loot!"), msg, GameApp.T("رائع", "Great"), null);
            paint[0].run();
        });
        lay.addView(collect);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الاستكشاف التلقائي (AFK)", "Auto-Exploration (AFK)"));
        b.setView(lay);
        b.setCancelable(false);
        final AlertDialog dialog = b.create();

        Button close = U.btn(ctx, GameApp.T("إغلاق", "Close"));
        close.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            if (tickHandler != null) {
                tickHandler.removeCallbacksAndMessages(null);
                tickHandler = null;
            }
            dialog.dismiss();
        });
        lay.addView(close);

        dialog.show();
        GameApp.speakTts(GameApp.T("الاستكشاف التلقائي. اختر المدة وسيجمع البطل الذهب والخبرة تلقائياً أثناء غيابك.", "Auto-exploration. Choose a duration and the hero will collect gold and exp automatically while you are away."));
        SaveSystem.saveAndRefresh();
    }
}