package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.MainActivity;
import com.lli.com.core.Achievements;
import com.lli.com.core.BattleLog;
import com.lli.com.core.ItemData;
import com.lli.com.core.ItemGenerator;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.PlayerSystem;
import com.lli.com.core.SaveSystem;
import com.lli.com.core.TempStats;

import java.util.function.IntConsumer;

public class ExplorationSystem {
    private static Dialog currentDialog;
    private static Handler walkHandler;

    private static final String[] EFRIT = {"زافر النار", "لاهب الجمر", "سارب الدخان", "راقد الجمر", "نافث اللهب"};
    private static final String[] LION = {"أسد الصحراء", "ليث الجبال", "نمر البراري", "ضيغم الغابات", "فهد السهول"};
    private static final String[] DEVIL = {"شيطان الوسواس", "مارد الأرواح", "فتان العقول", "سالب النفوس", "قاطع الصراط"};
    private static final String[] DRAGON = {"تنين الجحيم", "عملاق البراكين", "نيروب اللهب", "قاصف السماوات", "سيد الجمر"};
    private static final String[] DARK = {"ظالم الأعماق", "ناشر الظلام", "ساحق النور", "مطفئ الأمل", "قاتل الأرواح"};
    private static final String[] VOID = {"سيد الفراغ", "محطم الوجود", "بالع الكون", "قاهر الأبدية", "ماحق الزمن"};
    private static final String[] GALAXY = {"كائن النجوم", "سيد المجرة", "راعي الثقوب السوداء", "محطم النجوم", "سارب المجرة"};
    private static final String[] SOUL = {"روح الضياع", "طيف الماضي", "سارب الأحياء", "ناشر الأرواح", "راعد القبور"};
    private static final String[] SKY = {"حارس السماوات", "صاعق السحاب", "ملاك العذاب", "سيد الرياح", "قاصف السماء"};
    private static final String[] ABYSS = {"شيطان الهاوية", "مالك الأعماق", "ساحق الهاوية", "بالع الأرواح", "ظالم الأبدية"};

    private static final java.util.Map<String, String> MONSTER_EN = new java.util.HashMap<>();
    static {
        String[][] pairs = {
                EFRIT, new String[]{"Fire Emitter", "Ember Blazer", "Smoke Drifter", "Ember Dweller", "Flame Breather"},
                LION, new String[]{"Desert Lion", "Mountain Lion", "Wilderness Tiger", "Forest Lion", "Plains Cheetah"},
                DEVIL, new String[]{"Obsession Demon", "Spirit Djinn", "Mind Charmer", "Soul Stealer", "Road Blocker"},
                DRAGON, new String[]{"Hell Dragon", "Volcano Giant", "Blazing Flame", "Sky Crusher", "Ember Lord"},
                DARK, new String[]{"Depths Tyrant", "Darkness Spreader", "Light Crusher", "Hope Extinguisher", "Soul Killer"},
                VOID, new String[]{"Void Lord", "Existence Shatterer", "Universe Devourer", "Eternity Conqueror", "Time Destroyer"},
                GALAXY, new String[]{"Star Being", "Galaxy Lord", "Black Hole Herder", "Star Breaker", "Galaxy Drifter"},
                SOUL, new String[]{"Soul of Loss", "Ghost of the Past", "Drifter of the Living", "Spirit Spreader", "Grave Thunderer"},
                SKY, new String[]{"Guardian of the Skies", "Cloud Striker", "Angel of Torment", "Wind Lord", "Sky Smasher"},
                ABYSS, new String[]{"Abyss Demon", "Master of the Depths", "Abyss Crusher", "Soul Devourer", "Tyrant of Eternity"}
        };
        for (int i = 0; i < pairs.length; i += 2) {
            for (int j = 0; j < pairs[i].length; j++) {
                MONSTER_EN.put(pairs[i][j], pairs[i + 1][j]);
            }
        }
    }

    // Translate a monster name at display time so it always matches the current language.
    public static String mtr(String arName) {
        String en = MONSTER_EN.get(arName);
        return GameApp.T(arName, en == null ? arName : en);
    }

    public static void returnToMain() {
        dismissCurrent();
        if (walkHandler != null) walkHandler.removeCallbacksAndMessages(null);
        MainActivity.inst.showScreen(new DashboardScreen(GameApp.uiCtx()));
    }

    private static void dismissCurrent() {
        if (currentDialog != null) {
            try { currentDialog.dismiss(); } catch (Exception ignored) {}
            currentDialog = null;
        }
    }

    public static void openExplorationUI() {
        if (NumberUtil.rand100() <= 15) {
            triggerRandomEvent();
            return;
        }
        dismissCurrent();
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;

        LinearLayout lay = U.linear(ctx, true);
        lay.setBackgroundColor(U.BLACK);

        TextView info = U.text(ctx, "", 14, U.GOLD, false);
        info.setPadding(40, 40, 40, 40);
        info.setText("" + p.name + "| Lv." + p.level + "|" + NumberUtil.formatNumber(p.gold) + GameApp.T("ذهب\n صحة:", "gold\nHP:") + (long) p.getTotalHp() + "/" + (long) p.getTotalMaxHp());
        lay.addView(info);

        final TextView actionText = U.text(ctx, "", 16, U.WHITE, false);
        actionText.setPadding(40, 10, 40, 40);
        actionText.setText(GameApp.T("اضغط مطولا للتقدم في المنطقة.", "Press and hold to move forward in the area."));
        lay.addView(actionText);

        walkHandler = new Handler();
        final boolean[] isWalking = {false};
        final Button b1 = U.btn(ctx, GameApp.T("اضغط مطولا للتقدم المستمر", "Press and hold to keep advancing"));
        final Runnable[] walkRunnable = new Runnable[1];

        Runnable stopWalking = () -> {
            isWalking[0] = false;
            walkHandler.removeCallbacks(walkRunnable[0]);
            b1.setText(GameApp.T("اضغط مطولا للتقدم المستمر", "Press and hold to keep advancing"));
        };

        walkRunnable[0] = new Runnable() {
            @Override
            public void run() {
                if (!isWalking[0]) return;
                p.steps += 1;
                GameApp.sound.playSnd("steps.mp3");
                String chestMsg = ChestSystem.checkChestFind();
                actionText.setText(GameApp.T("تتقدم في المنطقة بحذر...", "You advance carefully in the area...") + chestMsg);
                if (p.currentQuest != null && p.currentQuest.active && p.currentQuest.type.equals("walk")) {
                    p.currentQuest.current += 1;
                    PlayerSystem.checkQuestCompletion();
                }
                if (p.steps >= p.targetSteps) {
                    p.steps = 0;
                    p.targetSteps = NumberUtil.rand(5, 8);
                    stopWalking.run();
                    dismissCurrent();
                    startBattle();
                } else {
                    SaveSystem.saveAndRefreshQuiet();
                    double speedMod = 0;
                    for (String eq : p.equipped) {
                        for (ItemData inv : p.inventory) {
                            if (inv != null && inv.name.equals(eq) && inv.speedBonus > speedMod) speedMod = inv.speedBonus;
                        }
                    }
                    long finalDelay = (long) Math.floor(1000 * (1 - speedMod));
                    if (finalDelay < 300) finalDelay = 300;
                    walkHandler.postDelayed(walkRunnable[0], finalDelay);
                }
            }
        };

        b1.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (!isWalking[0]) {
                    isWalking[0] = true;
                    b1.setText(GameApp.T("جاري التقدم...", "Advancing..."));
                    walkHandler.post(walkRunnable[0]);
                }
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                stopWalking.run();
            }
            return true;
        });
        lay.addView(b1);

        Button b2 = U.btn(ctx, GameApp.T("العودة", "Return"));
        b2.setOnClickListener(v -> {
            stopWalking.run();
            p.location = "المنطقة الآمنة";
            SaveSystem.saveAndRefresh();
            returnToMain();
        });
        lay.addView(b2);

        final Dialog d = new Dialog(ctx);
        currentDialog = d;
        d.setCancelable(false);
        d.setContentView(lay);
        d.show();
        SaveSystem.saveAndRefresh();
    }

    public static void startBattle() {
        dismissCurrent();
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final String loc = p.location;

        String mName = GameApp.T("عفريت", "Imp");
        int minL = 1, maxL = 20;
        long mult = 1;
        final boolean[] isDragon = {false};
        String[] names = EFRIT;
        if (loc.contains("الاسود")) { names = LION; minL = 20; maxL = 70; mult = 5; }
        else if (loc.contains("الشياطين")) { names = DEVIL; minL = 50; maxL = 276; mult = 15; }
        else if (loc.contains("التنانين")) { names = DRAGON; minL = 100; maxL = 2001; mult = 50; isDragon[0] = true; }
        else if (loc.contains("بوابة الظلام")) { names = DARK; minL = 500; maxL = 30000; mult = 5000; }
        else if (loc.contains("عرش الفراغ")) { names = VOID; minL = 600; maxL = 50000; mult = 12000; }
        else if (loc.contains("المجرة")) { names = GALAXY; minL = 700; maxL = 99999; mult = 30000; }
        else if (loc.contains("جزيرة الأرواح")) { names = SOUL; minL = 100; maxL = 300; mult = 60; }
        else if (loc.contains("مملكة السماء")) { names = SKY; minL = 200; maxL = 600; mult = 150; }
        else if (loc.contains("أعماق الهاوية")) { names = ABYSS; minL = 500; maxL = 1500; mult = 1000; }
        mName = names[NumberUtil.rand(0, names.length - 1)];
        final String fName = mtr(mName);
        final long fMult = mult;

        final int mLvl = isDragon[0] ? p.dragonLevel : Math.min(maxL, minL + p.killsInArea);
        final double[] mHp = {Math.floor((60 + (mLvl * 12)) * (fMult * 0.6)) * 4};
        final double[] mPwr = {Math.floor((12 + (mLvl * 3)) * (fMult * 0.5)) * 4};
        if (!loc.contains("العفاريت")) {
            mHp[0] = Math.floor(mHp[0] * 1.5);
            mPwr[0] = Math.floor(mPwr[0] * 1.5);
        }

        AlertDialog.Builder db = new AlertDialog.Builder(ctx);
        db.setCancelable(false);
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        final TextView txt = U.text(ctx, GameApp.T("وحش:", "Monster:") + fName + "[" + mLvl + "]", 15, U.WHITE, false);
        lay.addView(txt);
        final AlertDialog alert = db.setView(lay).create();
        currentDialog = alert;
        final long[] battleStart = {System.currentTimeMillis()};
        final double elapsedF = (System.currentTimeMillis() - battleStart[0]) / 1000.0;

        Runnable turn = () -> {
            GameApp.sound.playSnd("attack.mp3");
            GameApp.sound.playSnd(new String[]{"hit_enemy1.mp3", "hit_enemy2.mp3", "hit_enemy3.mp3", "hit_enemy4.mp3"}[NumberUtil.rand(0, 3)]);
            double myDmg = Math.floor((p.stats.strength + p.tempStats.str) + NumberUtil.rand(10, 30));
            if (NumberUtil.rand100() <= (p.stats.luck + p.tempStats.luck) / 2.0) myDmg = myDmg * 2;
            mHp[0] = mHp[0] - myDmg;

            double totalDef = com.lli.com.core.BattleDecay.effective(p.stats.endurance + p.tempStats.endurance, elapsedF);
            double totalAgi = com.lli.com.core.BattleDecay.effective(p.stats.agility + p.tempStats.agi, elapsedF);
            double defPen = 0;
            if (totalDef > 10000) defPen = (totalDef - 10000) * 0.05;
            double maxReductionPct = 0.85;
            double defReduction = Math.min(mPwr[0] * maxReductionPct, totalDef / 4.0);
            double finalMPwr = mPwr[0] + defPen;
            double mDmg = Math.max(mPwr[0] * 0.1, finalMPwr - defReduction);

            double dodgeChance = totalAgi / 2.0;
            if (totalAgi > 2000) {
                double accuracyBoost = (totalAgi - 2000) * 0.01;
                dodgeChance = Math.max(5, dodgeChance - accuracyBoost);
            }
            if (NumberUtil.rand100() <= dodgeChance) {
                mDmg = 0;
                txt.setText(txt.getText() + GameApp.T("\nلقد تفاديت ضربة الوحش ببراعة!", "\nYou brilliantly dodged the monster's attack!"));
            }
            p.stats.hp = p.stats.hp - mDmg;
            txt.setText(String.format(GameApp.T("صحتك: %d | الوحش: %d", "Your HP: %d | Monster: %d"), (long) Math.floor(p.stats.hp), (long) Math.floor(Math.max(0, mHp[0]))));

            if (p.stats.hp <= 0 || mHp[0] <= 0) {
                alert.dismiss();
                currentDialog = null;
                if (mHp[0] <= 0) {
                    GameApp.sound.playSnd("kill_enemy.mp3");
                    GameApp.sound.playSnd("reward.mp3");
                    double luckBonus = p.stats.luck / 10.0;
                    long winG = (long) Math.floor(mLvl * 8 * fMult * 1.5 * (1 + luckBonus));
                    long winE = (long) Math.floor(mLvl * 15 * fMult * 1.5 * (1 + luckBonus));
                    final boolean dFound = NumberUtil.rand100() <= 3;
                    if (dFound) p.diamonds += 1;
                    String winMsg = GameApp.T("ذهب:", "Gold:") + winG + GameApp.T("\nخبرة:", "\nEXP:") + winE;
                    if (dFound) winMsg = winMsg + GameApp.T("\n[نادر جداً] وجدت 1 ألماس!", "\n[VERY RARE] You found 1 diamond!");
                    String chestNews = ChestSystem.checkChestFind();
                    if (!chestNews.isEmpty()) winMsg = winMsg + "\n" + chestNews;
                    final long fWinG = winG;
                    final long fWinE = winE;
                    AlertDialog.Builder v = new AlertDialog.Builder(ctx);
                    v.setTitle(GameApp.T("انتصار!", "Victory!"));
                    v.setView(U.msg(winMsg));
                    v.setPositiveButton(GameApp.T("استلام والاستمرار", "Claim and Continue"), (d, w) -> {
                        if (isDragon[0]) p.dragonLevel += 1; else p.killsInArea += 1;
                        p.gold += fWinG;
                        PlayerSystem.addExp(fWinE);
                        p.stats.hp = Math.max(1, p.stats.hp);
                        recordExplorerBattle(true, fName, mLvl, loc, fWinG, dFound);
                        PlayerSystem.endBattleProcessing(isDragon[0]);
                        openExplorationUI();
                    });
                    v.setNegativeButton(GameApp.T("العودة للمدينة", "Return to City"), (d, w) -> {
                        if (isDragon[0]) p.dragonLevel += 1; else p.killsInArea += 1;
                        p.gold += fWinG;
                        PlayerSystem.addExp(fWinE);
                        p.stats.hp = Math.max(1, p.stats.hp);
                        recordExplorerBattle(true, fName, mLvl, loc, fWinG, dFound);
                        p.location = "المنطقة الآمنة";
                        PlayerSystem.endBattleProcessing(isDragon[0]);
                        returnToMain();
                    });
                    v.setCancelable(false);
                    v.show();
                } else {
                    GameApp.sound.playSnd("death_defeat.mp3");
                    p.stats.hp = 1;
                    BattleLog.record(p, GameApp.T("هزيمة على يد", "Defeated by") + fName + "[" + mLvl + "]" + GameApp.T("في", "in") + loc, "Defeated by " + fName + "[" + mLvl + "] in " + loc);
                    PlayerSystem.endBattleProcessing(isDragon[0]);
                    openExplorationUI();
                }
            }
        };

        Runnable flee = () -> {
            if (NumberUtil.rand100() <= (30 + (p.stats.agility + p.tempStats.agi) / 5.0)) {
                alert.dismiss();
                currentDialog = null;
                PlayerSystem.endBattleProcessing(isDragon[0]);
                openExplorationUI();
            } else {
                p.stats.hp = p.stats.hp - mPwr[0];
                txt.setText(GameApp.T("فشلت! صحتك:", "Failed! Your HP:") + (long) Math.floor(p.stats.hp));
                if (p.stats.hp <= 0) {
                    alert.dismiss();
                    currentDialog = null;
                    p.stats.hp = 1;
                    PlayerSystem.endBattleProcessing(isDragon[0]);
                    openExplorationUI();
                }
            }
        };

        LinearLayout btnLay = U.linear(ctx, false);
        Button bA = U.btn(ctx, GameApp.T("هجوم", "Attack"));
        bA.setOnClickListener(v -> turn.run());
        btnLay.addView(bA);
        Button bF = U.btn(ctx, GameApp.T("هروب", "Flee"));
        bF.setOnClickListener(v -> flee.run());
        btnLay.addView(bF);
        lay.addView(btnLay);
        alert.show();
    }

    private static void saveAndReopen() {
        SaveSystem.saveAndRefresh();
        openExplorationUI();
    }

    private static void recordExplorerBattle(boolean win, String mName, int mLvl, String loc, long g, boolean dFound) {
        PlayerData p = GameApp.player;
        String locTranslated = Dialogs.zoneName(loc);
        if (win) {
            BattleLog.record(p, GameApp.T("انتصار على", "Victory over") + " " + mName + "[" + mLvl + "] " + GameApp.T("في", "in") + " " + locTranslated + " " + GameApp.T("(+", "(+") + NumberUtil.formatNumber(g) + GameApp.T("ذهب)", "gold)"),
                    "Victory over " + mName + "[" + mLvl + "] in " + locTranslated + " (+" + NumberUtil.formatNumber(g) + " gold)");
            Achievements.progress("kill", 1);
            Achievements.progress("gold", g);
            Achievements.progress("win", 1);
            if (dFound) Achievements.progress("diamond", 1);
        } else {
            BattleLog.record(p, GameApp.T("هزيمة على يد", "Defeated by") + " " + mName + "[" + mLvl + "] " + GameApp.T("في", "in") + " " + locTranslated,
                    "Defeated by " + mName + "[" + mLvl + "] in " + locTranslated);
        }
    }

    public static void triggerRandomEvent() {
        dismissCurrent();
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;

        final Object[][] events = {
                {GameApp.T("التاجر المتجول", "The Wandering Merchant"), GameApp.T("قابلت تاجراً غامضاً يعرض عليك صندوقاً سحرياً مقابل 1000 ذهب. هل تقبل؟", "You met a mysterious merchant offering you a magic box for 1000 gold. Do you accept?"), new String[]{GameApp.T("شراء (1000 ذهب)", "Buy (1000 gold)"), GameApp.T("تجاهله", "Ignore him")}},
                {GameApp.T("بطل المبارزة الشريف", "The Honorable Duelist"), GameApp.T("التقيت بمبارز متجول يبحث عن منافس شريف. هل تقبل تحديه في مبارزة ودية؟", "You met a traveling duelist looking for an honorable rival. Do you accept his friendly duel challenge?"), new String[]{GameApp.T("قبول التحدي", "Accept the challenge"), GameApp.T("الاعتذار والرحيل", "Apologize and leave")}},
                {GameApp.T("تاجر الكتب القديمة", "Ancient Books Merchant"), GameApp.T("وجدت تاجراً يبيع كتباً نادرة عن فنون القتال والدفاع. هل تشتري كتاباً بـ 5000 ذهب؟", "You found a merchant selling rare books about combat and defense arts. Do you buy a book for 5000 gold?"), new String[]{GameApp.T("شراء الكتاب", "Buy the book"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("بئر الحظ", "The Well of Luck"), GameApp.T("وصلت إلى بئر قديمة يقال إنها تجلب الحظ لمن يلقي فيها قطعة ذهبية. هل تجرب؟", "You reached an ancient well said to bring luck to those who throw a gold coin into it. Do you try?"), new String[]{GameApp.T("إلقاء 1000 ذهب", "Throw 1000 gold"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("مدرب الرشاقة", "Agility Trainer"), GameApp.T("قابلت مدرباً قديماً يعرض عليك تدريباً مكثفاً لزيادة رشاقتك مقابل 3000 ذهب.", "You met an old trainer offering you intense training to increase your agility for 3000 gold."), new String[]{GameApp.T("بدء التدريب", "Start training"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("صياد الكنوز", "Treasure Hunter"), GameApp.T("التقيت بصياد كنوز يعرض عليك خريطة لمكان مجهول مقابل 10,000 ذهب. هل تخاطر؟", "You met a treasure hunter offering you a map to an unknown place for 10,000 gold. Do you risk it?"), new String[]{GameApp.T("شراء الخريطة", "Buy the map"), GameApp.T("الرفض", "Refuse")}},
                {GameApp.T("طبيب الأعشاب", "Herbal Healer"), GameApp.T("وجدت طبيب أعشاب يعرض عليك جرعة منشطة تزيد من صحتك القصوى مقابل 7000 ذهب.", "You found an herbal healer offering you a tonic that increases your max health for 7000 gold."), new String[]{GameApp.T("شرب الجرعة", "Drink the potion"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("حداد القلعة", "The Castle Blacksmith"), GameApp.T("قابلت حداداً ماهراً يعرض عليك شحذ سلاحك لزيادة قوته مقابل 4000 ذهب.", "You met a skilled blacksmith offering to sharpen your weapon to increase its power for 4000 gold."), new String[]{GameApp.T("شحذ السلاح", "Sharpen the weapon"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("المنافس الغامض", "The Mysterious Rival"), GameApp.T("ظهر لك شخص غامض يتحداك في سباق سرعة. هل تقبل؟", "A mysterious person appeared and challenged you to a speed race. Do you accept?"), new String[]{GameApp.T("قبول السباق", "Accept the race"), GameApp.T("الرفض", "Refuse")}},
                {GameApp.T("صندوق المفقودات", "The Lost Box"), GameApp.T("وجدت صندوقاً قديماً يبدو أنه سقط من إحدى القوافل. هل تفتحه؟", "You found an old box that seems to have fallen from one of the caravans. Do you open it?"), new String[]{GameApp.T("فتح الصندوق", "Open the box"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("حكيم الغابة", "The Forest Sage"), GameApp.T("قابلت حكيماً يعيش في الغابة، يعرض عليك نصيحة تزيد من حظك مقابل 2000 ذهب.", "You met a wise man living in the forest, offering you advice that increases your luck for 2000 gold."), new String[]{GameApp.T("سماع النصيحة", "Hear the advice"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("تحدي القوة", "The Strength Challenge"), GameApp.T("وجدت صخرة ضخمة مكتوب عليها: 'من يحرك هذه الصخرة ينال القوة'. هل تحاول؟", "You found a huge rock with writing on it: 'Whoever moves this rock gains power'. Do you try?"), new String[]{GameApp.T("محاولة تحريكها", "Try to move it"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("فخ اللصوص", "The Thieves' Trap"), GameApp.T("وقع بطلنا في كمين لمجموعة من اللصوص! ماذا ستفعل؟", "Our hero fell into an ambush by a group of thieves! What will you do?"), new String[]{GameApp.T("قتالهم", "Fight them"), GameApp.T("دفع رشوة (500 ذهب)", "Pay a bribe (500 gold)")}},
                {GameApp.T("قرية محتاجة", "A Village in Need"), GameApp.T("وجدت قرية صغيرة تحتاج للمساعدة. هناك أطفال جوعى وعائلات تعاني. هل تساعدهم؟", "You found a small village in need of help. There are hungry children and suffering families. Do you help them?"), new String[]{GameApp.T("التبرع بـ 1000 ذهب", "Donate 1000 gold"), GameApp.T("المتابعة", "Continue")}},
                {GameApp.T("معسكر عسكري قديم", "An Old Military Camp"), GameApp.T("وجدت معسكراً عسكرياً قديماً مهجوراً. يحتوي على أسلحة وذخيرة قديمة. هل تستكشفه؟", "You found an old abandoned military camp. It contains old weapons and ammunition. Do you explore it?"), new String[]{GameApp.T("الاستكشاف", "Explore"), GameApp.T("الرحيل", "Leave")}},
                {GameApp.T("كنز مخفي", "A Hidden Treasure"), GameApp.T("لاحظت علامات غريبة على الأرض تشير إلى كنز مخفي! هل تحفر للبحث عنه؟", "You noticed strange marks on the ground pointing to a hidden treasure! Do you dig to search for it?"), new String[]{GameApp.T("البحث عن الكنز", "Search for the treasure"), GameApp.T("الاستمرار", "Continue")}},
                {GameApp.T("ساحر عادل", "A Fair Wizard"), GameApp.T("قابلت ساحراً عادلاً يقدم خدمات سحرية مفيدة. هل تريد استشارته؟", "You met a fair wizard offering useful magical services. Do you want to consult him?"), new String[]{GameApp.T("استشارة (500 ذهب)", "Consult (500 gold)"), GameApp.T("الرحيل", "Leave")}},
        };

        Object[] ev = events[NumberUtil.rand(0, events.length - 1)];
        final String title = (String) ev[0];
        final String msg = (String) ev[1];
        final String[] options = (String[]) ev[2];
        GameApp.sound.playSnd("event_alert.mp3");

        IntConsumer action;
        if (title.equals(GameApp.T("التاجر المتجول", "The Wandering Merchant"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 1000) {
                        p.gold -= 1000;
                        int r = NumberUtil.rand(1, 3);
                        if (r == 1) { p.diamonds += 2; U.alert(ctx, GameApp.T("حظ موفق!", "Lucky Break!"), GameApp.T("وجدت 2 ألماس داخل الصندوق!", "You found 2 diamonds inside the box!"), GameApp.T("عظيم", "Great"), v -> saveAndReopen()); }
                        else if (r == 2) {
                            ItemData item = ItemGenerator.generateRandomItem(p.level, 0);
                            p.inventory.add(item);
                            U.alert(ctx, GameApp.T("اكتشاف!", "Discovery!"), GameApp.T("وجدت أداة:", "You found an item:") + item.name, GameApp.T("شكراً", "Thanks"), v -> saveAndReopen());
                        } else { U.alert(ctx, GameApp.T("خيبة أمل", "Disappointment"), GameApp.T("للأسف، الصندوق كان فارغاً! التاجر خدعك.", "Unfortunately, the box was empty! The merchant tricked you."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهب كافٍ!", "You don't have enough gold!"), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك بحذر.", "You continued on your way carefully."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("بطل المبارزة الشريف", "The Honorable Duelist"))) {
            action = idx -> {
                if (idx == 0) {
                    if (NumberUtil.rand100() <= 60) {
                        p.stats.strength += 5;
                        U.alert(ctx, GameApp.T("انتصار شريف", "Honorable Victory"), GameApp.T("لقد هزمت المبارز ببراعة! تعلمت منه تقنيات جديدة زادت قوتك بـ 5 نقاط.", "You defeated the duelist brilliantly! You learned new techniques that increased your strength by 5 points."), GameApp.T("رائع", "Amazing"), v -> saveAndReopen());
                    } else {
                        p.stats.hp = Math.floor(p.stats.hp * 0.8);
                        U.alert(ctx, GameApp.T("خسارة مشرفة", "Honorable Defeat"), GameApp.T("كان المبارز أقوى منك هذه المرة، لكنك تعلمت الكثير. فقدت 20% من صحتك.", "The duelist was stronger than you this time, but you learned a lot. You lost 20% of your health."), GameApp.T("سأتدرب أكثر", "I'll train harder"), v -> saveAndReopen());
                    }
                } else { U.alert(ctx, null, GameApp.T("ودعت المبارز وتابعت طريقك.", "You bid the duelist farewell and continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("تاجر الكتب القديمة", "Ancient Books Merchant"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 5000) {
                        p.gold -= 5000;
                        p.stats.endurance += 10;
                        U.alert(ctx, GameApp.T("معرفة جديدة", "New Knowledge"), GameApp.T("قرأت الكتاب وتعلمت أساليب دفاعية جديدة! زاد دفاعك بـ 10 نقاط.", "You read the book and learned new defensive techniques! Your defense increased by 10 points."), GameApp.T("ممتاز", "Excellent"), v -> saveAndReopen());
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("بئر الحظ", "The Well of Luck"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 1000) {
                        p.gold -= 1000;
                        if (NumberUtil.rand100() <= 40) {
                            p.stats.luck += 2;
                            U.alert(ctx, GameApp.T("حظ سعيد", "Good Luck"), GameApp.T("تشعر بهالة من الحظ تحيط بك! زاد حظك بـ 2.", "You feel an aura of luck surrounding you! Your luck increased by 2."), GameApp.T("رائع", "Amazing"), v -> saveAndReopen());
                        } else { U.alert(ctx, GameApp.T("لا شيء", "Nothing"), GameApp.T("لم يحدث شيء، ربما في المرة القادمة.", "Nothing happened, maybe next time."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("مدرب الرشاقة", "Agility Trainer"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 3000) {
                        p.gold -= 3000;
                        p.stats.agility += 8;
                        U.alert(ctx, GameApp.T("تدريب شاق", "Intense Training"), GameApp.T("بعد ساعات من التدريب، أصبحت حركتك أسرع! زادت رشاقتك بـ 8 نقاط.", "After hours of training, your movement became faster! Your agility increased by 8 points."), GameApp.T("عظيم", "Great"), v -> saveAndReopen());
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("صياد الكنوز", "Treasure Hunter"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 10000) {
                        p.gold -= 10000;
                        int r = NumberUtil.rand(1, 3);
                        if (r == 1) { p.gold += 25000; U.alert(ctx, GameApp.T("كنز حقيقي", "A Real Treasure"), GameApp.T("الخريطة كانت صحيحة! وجدت كنزاً يحتوي على 25,000 ذهب.", "The map was correct! You found a treasure containing 25,000 gold."), GameApp.T("مذهل", "Awesome"), v -> saveAndReopen()); }
                        else if (r == 2) { p.crystals += 10; U.alert(ctx, GameApp.T("جواهر نادرة", "Rare Gems"), GameApp.T("وجدت صندوقاً مليئاً بالكريستال! حصلت على 10 كريستالات.", "You found a box full of crystals! You got 10 crystals."), GameApp.T("رائع", "Amazing"), v -> saveAndReopen()); }
                        else { U.alert(ctx, GameApp.T("خريطة مزيفة", "A Fake Map"), GameApp.T("للأسف، الخريطة كانت مزيفة ولم تجد شيئاً.", "Unfortunately, the map was fake and you found nothing."), GameApp.T("يا للأسف", "How unfortunate"), v -> saveAndReopen()); }
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("طبيب الأعشاب", "Herbal Healer"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 7000) {
                        p.gold -= 7000;
                        p.stats.maxHp += 50;
                        p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
                        U.alert(ctx, GameApp.T("حيوية فائقة", "Super Vitality"), GameApp.T("تشعر بنشاط غير مسبوق! زادت صحتك القصوى بـ 50 نقطة.", "You feel unprecedented energy! Your max health increased by 50 points."), GameApp.T("ممتاز", "Excellent"), v -> saveAndReopen());
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("حداد القلعة", "The Castle Blacksmith"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 4000) {
                        p.gold -= 4000;
                        p.stats.strength += 12;
                        U.alert(ctx, GameApp.T("سلاح حاد", "A Sharp Weapon"), GameApp.T("أصبح سلاحك أكثر فتكاً الآن! زادت قوتك بـ 12 نقطة.", "Your weapon became deadlier now! Your strength increased by 12 points."), GameApp.T("رائع", "Amazing"), v -> saveAndReopen());
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("المنافس الغامض", "The Mysterious Rival"))) {
            action = idx -> {
                if (idx == 0) {
                    if (NumberUtil.rand100() <= (p.stats.agility / 10.0 + 30)) {
                        p.stats.agility += 15;
                        U.alert(ctx, GameApp.T("فوز ساحق", "Crushing Victory"), GameApp.T("لقد كنت أسرع منه بكثير! زادت رشاقتك بـ 15 نقطة.", "You were much faster than him! Your agility increased by 15 points."), GameApp.T("عظيم", "Great"), v -> saveAndReopen());
                    } else {
                        p.stats.hp = Math.floor(p.stats.hp * 0.9);
                        U.alert(ctx, GameApp.T("خسارة", "Defeat"), GameApp.T("كان المنافس أسرع منك، وأصبت بالإرهاق. فقدت 10% من صحتك.", "The rival was faster than you, and you became exhausted. You lost 10% of your health."), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("صندوق المفقودات", "The Lost Box"))) {
            action = idx -> {
                if (idx == 0) {
                    int r = NumberUtil.rand(1, 3);
                    if (r == 1) {
                        ItemData item = ItemGenerator.generateRandomItem(p.level, 0);
                        p.inventory.add(item);
                        U.alert(ctx, GameApp.T("لقطة ثمينة", "A Precious Find"), GameApp.T("وجدت أداة داخل الصندوق:", "You found an item inside the box:") + item.name, GameApp.T("رائع", "Amazing"), v -> saveAndReopen());
                    } else if (r == 2) {
                        p.gold += 5000;
                        U.alert(ctx, GameApp.T("ذهب ضائع", "Lost Gold"), GameApp.T("وجدت 5000 ذهب داخل الصندوق!", "You found 5000 gold inside the box!"), GameApp.T("عظيم", "Great"), v -> saveAndReopen());
                    } else {
                        p.stats.hp = Math.floor(p.stats.hp * 0.85);
                        U.alert(ctx, GameApp.T("فخ!", "A Trap!"), GameApp.T("كان الصندوق مفخخاً! فقدت 15% من صحتك.", "The box was booby-trapped! You lost 15% of your health."), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("حكيم الغابة", "The Forest Sage"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 2000) {
                        p.gold -= 2000;
                        p.stats.luck += 5;
                        U.alert(ctx, GameApp.T("حكمة غالية", "Precious Wisdom"), GameApp.T("كلام الحكيم فتح آفاقاً جديدة لك! زاد حظك بـ 5 نقاط.", "The sage's words opened new horizons for you! Your luck increased by 5 points."), GameApp.T("شكراً", "Thanks"), v -> saveAndReopen());
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("تحدي القوة", "The Strength Challenge"))) {
            action = idx -> {
                if (idx == 0) {
                    if (NumberUtil.rand100() <= (p.stats.strength / 20.0 + 20)) {
                        p.stats.strength += 20;
                        U.alert(ctx, GameApp.T("قوة جبارة", "Mighty Strength"), GameApp.T("لقد حركت الصخرة! تشعر بقوة تتدفق في عضلاتك. زادت قوتك بـ 20 نقطة.", "You moved the rock! You feel strength flowing through your muscles. Your strength increased by 20 points."), GameApp.T("مذهل", "Awesome"), v -> saveAndReopen());
                    } else {
                        p.stats.hp = Math.floor(p.stats.hp * 0.8);
                        U.alert(ctx, GameApp.T("إصابة", "Injury"), GameApp.T("الصخرة ثقيلة جداً، أصبت بتمزق عضلي. فقدت 20% من صحتك.", "The rock was too heavy, you suffered a muscle tear. You lost 20% of your health."), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("فخ اللصوص", "The Thieves' Trap"))) {
            action = idx -> {
                if (idx == 0) {
                    if (NumberUtil.rand100() <= 70) {
                        long reward = 2000;
                        p.gold += reward;
                        U.alert(ctx, GameApp.T("انتصار!", "Victory!"), GameApp.T("هزمت اللصوص ووجدت معهم", "You defeated the thieves and found with them") + reward + GameApp.T("ذهب!", "gold!"), GameApp.T("عظيم", "Great"), v -> saveAndReopen());
                    } else {
                        p.stats.hp = Math.floor(p.stats.hp * 0.5);
                        U.alert(ctx, GameApp.T("هزيمة", "Defeat"), GameApp.T("أصبت في المعركة وفقدت نصف صحتك!", "You were injured in the battle and lost half your health!"), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    }
                } else {
                    if (p.gold >= 500) {
                        p.gold -= 500;
                        U.alert(ctx, GameApp.T("نجاة", "Escape"), GameApp.T("دفعت الرشوة ونجوت بجلدك.", "You paid the bribe and escaped by the skin of your teeth."), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    } else {
                        U.alert(ctx, GameApp.T("فشل", "Failure"), GameApp.T("لا تملك ذهب كافٍ للرشوة! اضطررت للقتال.", "You don't have enough gold for the bribe! You had to fight."), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    }
                }
            };
        } else if (title.equals(GameApp.T("قرية محتاجة", "A Village in Need"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 1000) {
                        p.gold -= 1000;
                        PlayerSystem.addExp(5000);
                        U.alert(ctx, GameApp.T("عمل خير", "A Good Deed"), GameApp.T("شكراً لك! ساعدت القرية وحصلت على 5000 خبرة. الله يجزيك خيراً.", "Thank you! You helped the village and earned 5000 experience. May God reward you."), GameApp.T("بسم الله", "In God's name"), v -> saveAndReopen());
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهب كافٍ للتبرع.", "You don't have enough gold to donate."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("معسكر عسكري قديم", "An Old Military Camp"))) {
            action = idx -> {
                if (idx == 0) {
                    int r = NumberUtil.rand(1, 2);
                    if (r == 1) {
                        ItemData item = ItemGenerator.generateRandomItem(p.level + 2, 0);
                        p.inventory.add(item);
                        U.alert(ctx, GameApp.T("اكتشاف عسكري", "Military Discovery"), GameApp.T("وجدت أداة قيمة:", "You found a valuable item:") + item.name, GameApp.T("عظيم", "Great"), v -> saveAndReopen());
                    } else {
                        p.stats.hp = Math.floor(p.stats.hp * 0.7);
                        U.alert(ctx, GameApp.T("خطر!", "Danger!"), GameApp.T("انفجرت ذخيرة قديمة! فقدت 30% من صحتك.", "Old ammunition exploded! You lost 30% of your health."), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    }
                } else { U.alert(ctx, null, GameApp.T("تجاهلت المعسكر بحذر.", "You ignored the camp cautiously."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("كنز مخفي", "A Hidden Treasure"))) {
            action = idx -> {
                if (idx == 0) {
                    int r = NumberUtil.rand(1, 3);
                    if (r == 1) {
                        long gold = NumberUtil.rand(3000, 8000);
                        p.gold += gold;
                        U.alert(ctx, GameApp.T("كنز!", "Treasure!"), GameApp.T("وجدت", "You found") + gold + GameApp.T("ذهب مخفي!", "hidden gold!"), GameApp.T("الحمد لله", "Praise be to God"), v -> saveAndReopen());
                    } else if (r == 2) {
                        p.crystals += 5;
                        U.alert(ctx, GameApp.T("جوهرة نادرة", "Rare Jewel"), GameApp.T("وجدت 5 كريستال مخفي في الكنز!", "You found 5 hidden crystals in the treasure!"), GameApp.T("عظيم", "Great"), v -> saveAndReopen());
                    } else {
                        p.stats.hp = Math.floor(p.stats.hp * 0.4);
                        U.alert(ctx, GameApp.T("فخ قديم", "An Old Trap"), GameApp.T("الكنز كان فخاً! فقدت 60% من صحتك.", "The treasure was a trap! You lost 60% of your health."), GameApp.T("حسنا", "OK"), v -> saveAndReopen());
                    }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك بحذر.", "You continued on your way carefully."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else if (title.equals(GameApp.T("ساحر عادل", "A Fair Wizard"))) {
            action = idx -> {
                if (idx == 0) {
                    if (p.gold >= 500) {
                        p.gold -= 500;
                        int r = NumberUtil.rand(1, 2);
                        if (r == 1) {
                            p.stats.strength += 10;
                            U.alert(ctx, GameApp.T("بركة سحرية", "A Magical Blessing"), GameApp.T("منحك الساحر بركة! زادت قوتك بـ 10.", "The wizard granted you a blessing! Your strength increased by 10."), GameApp.T("شكراً", "Thanks"), v -> saveAndReopen());
                        } else {
                            p.stats.maxHp += 50;
                            p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
                            U.alert(ctx, GameApp.T("شفاء سحري", "Magical Healing"), GameApp.T("منحك الساحر شفاءً! زادت صحتك بـ 50.", "The wizard healed you! Your health increased by 50."), GameApp.T("شكراً", "Thanks"), v -> saveAndReopen());
                        }
                    } else { U.alert(ctx, null, GameApp.T("لا تملك ذهب كافٍ.", "You don't have enough gold."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
                } else { U.alert(ctx, null, GameApp.T("تابعت طريقك.", "You continued on your way."), GameApp.T("حسنا", "OK"), v -> saveAndReopen()); }
            };
        } else {
            action = idx -> saveAndReopen();
        }

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("حدث مفاجئ:", "Random Event:") + title);
        b.setView(U.msg(msg));
        if (options[0] != null) b.setPositiveButton(options[0], (d, w) -> {
            d.dismiss();
            action.accept(0);
        });
        if (options[1] != null) b.setNegativeButton(options[1], (d, w) -> {
            d.dismiss();
            action.accept(1);
        });
        b.setCancelable(false);
        b.show();
    }

    public static void openTowerExplorationUI() {
        dismissCurrent();
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;

        LinearLayout lay = U.linear(ctx, true);
        lay.setBackgroundColor(U.BLACK);

        TextView info = U.text(ctx, "", 14, U.GOLD, false);
        info.setPadding(40, 40, 40, 40);
        info.setText(GameApp.T("البرج المظلم | Lv.", "Dark Tower | Lv.") + p.level + "|" + NumberUtil.formatNumber(p.gold) + GameApp.T("ذهب", "gold"));
        lay.addView(info);

        final TextView actionText = U.text(ctx, "", 16, U.WHITE, false);
        actionText.setPadding(40, 10, 40, 40);
        actionText.setText(GameApp.T("الطابق", "Floor") + p.towerFloor + GameApp.T(": اضغط للبحث عن وحش الطابق.", ": press to search for the floor's monster."));
        lay.addView(actionText);

        walkHandler = new Handler();
        final boolean[] isWalking = {false};
        final Button b1 = U.btn(ctx, GameApp.T("اضغط مطولا للمشي", "Press and hold to walk"));
        final Runnable[] walkRunnable = new Runnable[1];
        p.targetSteps = NumberUtil.rand(2, 4);

        Runnable stopWalking = () -> {
            isWalking[0] = false;
            walkHandler.removeCallbacks(walkRunnable[0]);
            b1.setText(GameApp.T("اضغط مطولا للمشي", "Press and hold to walk"));
        };

        walkRunnable[0] = new Runnable() {
            @Override
            public void run() {
                if (!isWalking[0]) return;
                p.steps += 1;
                double luckVal = p.stats.luck;
                double luckMult = 1 + (luckVal / 200.0);
                long gAdd = (long) Math.floor(NumberUtil.rand(20, 50) * p.towerFloor * luckMult);
                p.gold += gAdd;
                String msg = GameApp.T("جمعت", "Collected") + gAdd + GameApp.T("ذهب من البرج", "gold from the tower");
                actionText.setText(GameApp.T("تتقدم في البرج...\n", "You advance in the tower...\n") + msg);
                GameApp.speakTts(msg);
                if (p.steps >= p.targetSteps) {
                    p.steps = 0;
                    stopWalking.run();
                    dismissCurrent();
                    startTowerBattle();
                } else {
                    SaveSystem.saveAndRefreshQuiet();
                    walkHandler.postDelayed(walkRunnable[0], 1000);
                }
            }
        };

        b1.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (!isWalking[0]) {
                    isWalking[0] = true;
                    b1.setText(GameApp.T("جاري التقدم...", "Advancing..."));
                    walkHandler.post(walkRunnable[0]);
                }
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                stopWalking.run();
            }
            return true;
        });
        lay.addView(b1);

        Button b2 = U.btn(ctx, GameApp.T("الهروب بالغنيمة للمدينة", "Flee with loot to the city"));
        b2.setOnClickListener(v -> {
            stopWalking.run();
            if (p.tempStats != null) p.tempStats = new TempStats();
            p.location = "المنطقة الآمنة";
            SaveSystem.saveAndRefresh();
            returnToMain();
        });
        lay.addView(b2);

        Button bShop = U.btn(ctx, GameApp.T("متجر البرج", "Tower Shop"));
        bShop.setOnClickListener(v -> {
            String[] items = {
                    GameApp.T("جرعة شفاء صغيرة (500 ذهب) - تستعيد 20% من صحتك", "Small Healing Potion (500 gold) - Restores 20% of your health"),
                    GameApp.T("جرعة شفاء كبيرة (2000 ذهب) - تستعيد 50% من صحتك", "Large Healing Potion (2000 gold) - Restores 50% of your health"),
                    GameApp.T("جرعة قوة (3000 ذهب) - تزيد هجومك مؤقتاً بـ 50%", "Strength Potion (3000 gold) - Temporarily increases your attack by 50%")
            };
            AlertDialog.Builder sb = new AlertDialog.Builder(ctx);
            sb.setTitle(GameApp.T("متجر البرج - الطابق", "Tower Shop - Floor") + p.towerFloor);
            sb.setItems(items, (d, i) -> {
                if (i == 0) {
                    if (p.stats.hp >= p.stats.maxHp) {
                        U.alert(ctx, null, GameApp.T("صحتك ممتلئة بالفعل! لا تحتاج لهذه الجرعة الآن.", "Your health is already full! You don't need this potion right now."), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    if (p.gold >= 500) {
                        p.gold -= 500;
                        double heal = Math.floor(p.stats.maxHp * 0.2);
                        p.stats.hp = Math.min(p.stats.maxHp, p.stats.hp + heal);
                        SaveSystem.saveAndRefresh();
                        U.alert(ctx, null, GameApp.T("استعدت", "You recovered") + (long) heal + GameApp.T("نقطة صحة!", "health points!"), GameApp.T("حسنا", "OK"), null);
                    } else { U.alert(ctx, null, GameApp.T("ذهب غير كافٍ!", "Not enough gold!"), GameApp.T("حسنا", "OK"), null); }
                } else if (i == 1) {
                    if (p.stats.hp >= p.stats.maxHp) {
                        U.alert(ctx, null, GameApp.T("صحتك ممتلئة بالفعل! لا تحتاج لهذه الجرعة الآن.", "Your health is already full! You don't need this potion right now."), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    if (p.gold >= 2000) {
                        p.gold -= 2000;
                        double heal = Math.floor(p.stats.maxHp * 0.5);
                        p.stats.hp = Math.min(p.stats.maxHp, p.stats.hp + heal);
                        SaveSystem.saveAndRefresh();
                        U.alert(ctx, null, GameApp.T("استعدت", "You recovered") + (long) heal + GameApp.T("نقطة صحة!", "health points!"), GameApp.T("حسنا", "OK"), null);
                    } else { U.alert(ctx, null, GameApp.T("ذهب غير كافٍ!", "Not enough gold!"), GameApp.T("حسنا", "OK"), null); }
                } else if (i == 2) {
                    if (p.tempStats != null && p.tempStats.active()) {
                        U.alert(ctx, null, GameApp.T("لا يمكنك شرب جرعتين بنفس الوقت، انتظر انتهاء التأثير الحالي.", "You cannot drink two potions at the same time. Wait for the current effect to end."), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    if (p.gold >= 3000) {
                        p.gold -= 3000;
                        if (p.tempStats == null) p.tempStats = new TempStats();
                        p.tempStats.str += Math.floor(p.stats.strength * 0.5);
                        p.tempStats.battlesLeft = 5;
                        SaveSystem.saveAndRefresh();
                        U.alert(ctx, null, GameApp.T("قوتك الهجومية ازدادت مؤقتاً!", "Your attack power temporarily increased!"), GameApp.T("حسنا", "OK"), null);
                    } else { U.alert(ctx, null, GameApp.T("ذهب غير كافٍ!", "Not enough gold!"), GameApp.T("حسنا", "OK"), null); }
                }
            });
            sb.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
            sb.show();
        });
        lay.addView(bShop);

        if (p.towerFloor > 1 && p.towerFloor % 10 == 0 && !(p.towerMilestoneClaimed != null && Boolean.TRUE.equals(p.towerMilestoneClaimed.get(String.valueOf(p.towerFloor))))) {
            Button bMilestone = U.btn(ctx, GameApp.T("مكافأة الطابق", "Floor reward") + p.towerFloor + GameApp.T("(اضغط للاستلام)", "(press to claim)"));
            bMilestone.setOnClickListener(v -> {
                if (p.towerMilestoneClaimed == null) p.towerMilestoneClaimed = new java.util.HashMap<>();
                if (Boolean.TRUE.equals(p.towerMilestoneClaimed.get(String.valueOf(p.towerFloor)))) {
                    U.alert(ctx, null, GameApp.T("استلمت هذه المكافأة بالفعل!", "You already claimed this reward!"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                long crystalsReward = (long) Math.floor(p.towerFloor / 10.0);
                long diamondReward = p.towerFloor >= 50 ? 1 : 0;
                p.crystals += crystalsReward;
                if (diamondReward > 0) p.diamonds += diamondReward;
                p.towerMilestoneClaimed.put(String.valueOf(p.towerFloor), true);
                SaveSystem.saveAndRefresh();
                String mm = GameApp.T("مكافأة الطابق", "Floor reward") + p.towerFloor + ":\n+" + crystalsReward + GameApp.T("كريستال", "crystals");
                if (diamondReward > 0) mm = mm + "\n+" + diamondReward + GameApp.T("ألماس", "diamonds");
                U.alert(ctx, GameApp.T("مكافأة الطابق!", "Floor Reward!"), mm, GameApp.T("رائع", "Amazing"), null);
            });
            lay.addView(bMilestone);
        }

        Button bStats = U.btn(ctx, GameApp.T("إحصائيات البرج", "Tower Stats"));
        bStats.setOnClickListener(v -> {
            double luckVal = p.stats.luck;
            double luckMult = 1 + (luckVal / 200.0);
            long estGold = (long) Math.floor(NumberUtil.rand(20, 50) * p.towerFloor * luckMult);
            U.alert(ctx, GameApp.T("إحصائيات البرج", "Tower Stats"),
                    GameApp.T("الطابق الحالي:", "Current floor:") + p.towerFloor +
                            GameApp.T("\nالذهب التقديري لكل خطوة:", "\nEstimated gold per step:") + NumberUtil.formatNumber(estGold) +
                            GameApp.T("\nالحظ الحالي:", "\nCurrent luck:") + (long) luckVal +
                            GameApp.T("\nمضاعف الحظ:", "\nLuck multiplier:") + String.format("%.2f", luckMult) + "x" +
                            GameApp.T("\n\nكلما زاد حظك، زاد ذهبك في البرج!", "\n\nThe more luck you have, the more gold you get in the tower!"),
                    GameApp.T("حسنا", "OK"), null);
        });
        lay.addView(bStats);

        final Dialog d = new Dialog(ctx);
        currentDialog = d;
        d.setCancelable(false);
        d.setContentView(lay);
        d.show();
        SaveSystem.saveAndRefresh();
    }

    public static void startTowerBattle() {
        dismissCurrent();
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final double[] mHp = {Math.floor((100 * p.towerFloor) * 1.5)};
        final double[] mPwr = {Math.floor((20 * p.towerFloor) * 1.5)};

        AlertDialog.Builder db = new AlertDialog.Builder(ctx);
        db.setCancelable(false);
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        final TextView txt = U.text(ctx, GameApp.T("وحش البرج [الطابق", "Tower Monster [Floor") + p.towerFloor + "]", 15, U.WHITE, false);
        lay.addView(txt);
        final AlertDialog alert = db.setView(lay).create();
        currentDialog = alert;
        final long[] tbStart = {System.currentTimeMillis()};
        final double tbElapsedF = (System.currentTimeMillis() - tbStart[0]) / 1000.0;

        Runnable turn = () -> {
            GameApp.sound.playSnd("attack.mp3");
            GameApp.sound.playSnd(new String[]{"hit_enemy1.mp3", "hit_enemy2.mp3", "hit_enemy3.mp3", "hit_enemy4.mp3"}[NumberUtil.rand(0, 3)]);
            double myDmg = Math.floor((p.stats.strength + p.tempStats.str) + NumberUtil.rand(10, 30));
            if (NumberUtil.rand100() <= (p.stats.luck + p.tempStats.luck) / 2.0) myDmg = myDmg * 2;
            mHp[0] = mHp[0] - myDmg;

            double totalDef = com.lli.com.core.BattleDecay.effective(p.stats.endurance + p.tempStats.endurance, tbElapsedF);
            double totalAgi = com.lli.com.core.BattleDecay.effective(p.stats.agility + p.tempStats.agi, tbElapsedF);
            double defPen = 0;
            if (totalDef > 10000) defPen = (totalDef - 10000) * 0.05;
            double maxReductionPct = 0.85;
            double defReduction = Math.min(mPwr[0] * maxReductionPct, totalDef / 4.0);
            double finalMPwr = mPwr[0] + defPen;
            double mDmg = Math.max(mPwr[0] * 0.1, finalMPwr - defReduction);

            double dodgeChance = totalAgi / 2.0;
            if (totalAgi > 2000) {
                double accuracyBoost = (totalAgi - 2000) * 0.01;
                dodgeChance = Math.max(5, dodgeChance - accuracyBoost);
            }
            if (NumberUtil.rand100() <= dodgeChance) {
                mDmg = 0;
                txt.setText(txt.getText() + GameApp.T("\nلقد تفاديت ضربة الوحش ببراعة!", "\nYou brilliantly dodged the monster's attack!"));
            }
            p.stats.hp = p.stats.hp - mDmg;
            txt.setText(String.format(GameApp.T("صحتك: %d | وحش البرج: %d", "Your HP: %d | Tower Monster: %d"), (long) Math.floor(p.stats.hp), (long) Math.floor(Math.max(0, mHp[0]))));

            if (p.stats.hp <= 0 || mHp[0] <= 0) {
                alert.dismiss();
                currentDialog = null;
                if (mHp[0] <= 0) {
                    GameApp.sound.playSnd("kill_enemy.mp3");
                    GameApp.sound.playSnd("reward.mp3");
                    long winG = (long) Math.floor(p.towerFloor * 500);
                    long winE = (long) Math.floor(p.towerFloor * 800);
                    p.gold += winG;
                    PlayerSystem.addExp(winE);
                    p.towerFloor += 1;
                    BattleLog.record(p, GameApp.T("انتصار على وحش البرج (الطابق", "Victory over tower monster (floor") + (p.towerFloor - 1) + GameApp.T(") (+", ") (+") + NumberUtil.formatNumber(winG) + GameApp.T("ذهب)", "gold)"),
                    "Victory over tower monster (floor " + (p.towerFloor - 1) + ") (+" + NumberUtil.formatNumber(winG) + " gold)");
                    Achievements.progress("kill", 1);
                    Achievements.progress("gold", winG);
                    Achievements.progress("win", 1);
                    SaveSystem.saveAndRefresh();
                    AlertDialog.Builder v = new AlertDialog.Builder(ctx);
                    v.setTitle(GameApp.T("انتصار!", "Victory!"));
                    v.setView(U.msg(GameApp.T("صعدت للطابق", "You advanced to floor") + p.towerFloor + GameApp.T("\nتذكر: صحتك لا ترجع كاملة!", "\nRemember: your health doesn't fully restore!")));
                    v.setPositiveButton(GameApp.T("إكمال البرج", "Continue the Tower"), (d, w) -> openTowerExplorationUI());
                    v.setCancelable(false);
                    v.show();
                } else {
                    GameApp.sound.playSnd("death_defeat.mp3");
                    p.location = "المنطقة الآمنة";
                    if (p.tempStats != null) p.tempStats = new TempStats();
                    p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
                    BattleLog.record(p, GameApp.T("سقطت في البرج (الطابق", "Fell in the tower (floor") + p.towerFloor + ")", "Fell in the tower (floor " + p.towerFloor + ")");
                    p.towerFloor = 1;
                    SaveSystem.saveAndRefresh();
                    U.alert(ctx, GameApp.T("موت!", "Death!"), GameApp.T("سقطت في البرج وفقدت تقدمك. تم إعادتك للمدينة.", "You fell in the tower and lost your progress. You were returned to the city."), GameApp.T("حسنا", "OK"), v -> returnToMain());
                }
            }
        };

        LinearLayout btnLay = U.linear(ctx, false);
        Button bA = U.btn(ctx, GameApp.T("هجوم", "Attack"));
        bA.setOnClickListener(v -> turn.run());
        btnLay.addView(bA);
        Button bF = U.btn(ctx, GameApp.T("هروب", "Flee"));
        bF.setOnClickListener(v -> {
            alert.dismiss();
            currentDialog = null;
            if (p.tempStats != null) p.tempStats = new TempStats();
            p.location = "المنطقة الآمنة";
            returnToMain();
        });
        btnLay.addView(bF);
        lay.addView(btnLay);
        alert.show();
    }
}
