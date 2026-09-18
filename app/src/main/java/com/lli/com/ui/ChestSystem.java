package com.lli.com.ui;

import android.app.AlertDialog;
import android.content.Context;

import com.lli.com.GameApp;
import com.lli.com.core.ChestData;
import com.lli.com.core.ItemGenerator;
import com.lli.com.core.ItemData;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.PlayerSystem;
import com.lli.com.core.SaveSystem;

import java.util.ArrayList;
import java.util.List;

public class ChestSystem {
    private static final Object[][] CHEST_TABLE = {
            {"خرافي مطلق", 100, 150, 9999, 10},
            {"خرافي", 70, 100, 9999, 25},
            {"أسطوري", 50, 80, 9999, 60},
            {"ملحمي", 35, 60, 9999, 150},
            {"نادر", 20, 40, 9999, 300},
            {"غير عادي", 15, 20, 9999, 600},
            {"عادي", 10, 1, 20, 1000}
    };

    public static String checkChestFind() {
        PlayerData p = GameApp.player;
        if (NumberUtil.rand(1, 100) > 5) return "";
        int roll = NumberUtil.rand(1, 1000);
        String cType = null;
        int cReq = 10;
        int minLvl = 1;
        for (Object[] ct : CHEST_TABLE) {
            String type = (String) ct[0];
            int req = (Integer) ct[1];
            int mLvl = (Integer) ct[2];
            int xLvl = (Integer) ct[3];
            int chance = (Integer) ct[4];
            if (p.level >= mLvl && p.level <= xLvl && roll <= chance) {
                cType = type;
                cReq = req;
                minLvl = mLvl;
                break;
            }
        }
        if (cType == null) {
            if (p.level <= 20) {
                cType = "عادي";
                cReq = 10;
                minLvl = 1;
            } else {
                return "";
            }
        }
        int maxChests = 5 + p.chestSlotsExtra;
        if (p.lockedChests.size() >= maxChests) {
            return GameApp.T("[صندوق] وجدت صندوقاً [" + GameApp.rarity(cType) + "] لكن حقيبتك ممتلئة! (الحد:" + maxChests + ")", "[Chest] You found a chest [" + GameApp.rarity(cType) + "] but your bag is full! (Limit:" + maxChests + ")");
        }
        ChestData c = new ChestData();
        c.type = cType;
        c.req = cReq;
        c.done = 0;
        c.minLvl = minLvl;
        p.lockedChests.add(c);
        GameApp.sound.playSnd("reward.mp3");
        return GameApp.T("[صندوق] لقد وجدت صندوق [" + GameApp.rarity(cType) + "]! (يفتح في مستوى" + minLvl + ")", "[Chest] You found a chest [" + GameApp.rarity(cType) + "]! (Opens at level" + minLvl + ")");
    }

    public static void openFoundChestsUI() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        if (p.lockedChests.isEmpty()) {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setView(U.msg(GameApp.T("لا يوجد لديك صناديق مغلقة!", "You have no closed chests!")));
            b.setNeutralButton(GameApp.T("توسعة المساحة", "Expand Storage"), (d, w) -> expandChests());
            b.setPositiveButton(GameApp.T("إغلاق", "Close"), null);
            b.show();
            return;
        }
        List<String> list = new ArrayList<>();
        for (ChestData c : p.lockedChests) {
            String status = (c.done >= c.req) ? GameApp.T("جاهز", "Ready") : (c.done + "/" + c.req);
            list.add(GameApp.T(String.format("صندوق %s (%s) [مستوى %d]", GameApp.rarity(c.type), status, c.minLvl), String.format("Chest %s (%s) [Level %d]", GameApp.rarity(c.type), status, c.minLvl)));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("غنائمك المغلقة", "Your Locked Loot"));
        b.setItems(list.toArray(new String[0]), (d, idx) -> handleChestClick(idx));
        b.setNeutralButton(GameApp.T("توسعة المساحة", "Expand Storage"), (d, w) -> expandChests());
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void handleChestClick(int idx) {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        int chestIdx = idx;
        ChestData chest = p.lockedChests.get(chestIdx);
        if (p.level < chest.minLvl) {
            U.alert(ctx, GameApp.T("مستوى منخفض", "Level Too Low"), GameApp.T("لا يمكنك فتح هذا الصندوق حتى تصل للمستوى" + chest.minLvl, "You cannot open this chest until you reach level" + chest.minLvl), GameApp.T("حسنا", "OK"), null);
            return;
        }
        if (chest.done >= chest.req) {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("صندوق جاهز", "Chest Ready"));
            b.setView(U.msg(GameApp.T("الرقم السري مكسور! افتح الصندوق الآن.", "The code is cracked! Open the chest now.")));
            b.setPositiveButton(GameApp.T("فتح", "Open"), (d, w) -> grantChestReward(chestIdx));
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        } else {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("صندوق مغلق", "Chest Locked"));
            b.setView(U.msg(GameApp.T("تحتاج لقتل" + (chest.req - chest.done) + "وحوش إضافية.\n\nأو فتحه فوراً باستخدام 2 كريستال.", "You need to kill" + (chest.req - chest.done) + "more monsters.\n\nOr open it now for 2 crystals.")));
            b.setPositiveButton(GameApp.T("فتح فوري (2 كريستال)", "Open Now (2 Crystals)"), (d, w) -> {
                if (p.crystals >= 2) {
                    p.crystals -= 2;
                    grantChestReward(chestIdx);
                } else {
                    U.alert(ctx, null, GameApp.T("الكريستال غير كافٍ!", "Not enough crystals!"), GameApp.T("حسنا", "OK"), null);
                }
            });
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        }
    }

    private static void grantChestReward(int chestIdx) {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        ChestData chest = p.lockedChests.get(chestIdx);
        int rarityIdx = 1;
        if (chest.type.equals("غير عادي")) rarityIdx = 2;
        else if (chest.type.equals("نادر")) rarityIdx = 3;
        else if (chest.type.equals("ملحمي")) rarityIdx = 4;
        else if (chest.type.equals("أسطوري")) rarityIdx = 5;
        else if (chest.type.equals("خرافي") || chest.type.equals("خرافي مطلق")) rarityIdx = 6;

        ItemData newItem = ItemGenerator.generateRandomItem(p.level, rarityIdx);
        newItem.level = Math.min(newItem.level, p.level + 5);

        long gVal = 5000, eVal = 2000;
        if (chest.type.equals("غير عادي")) { gVal = 7000; eVal = 3000; }
        else if (chest.type.equals("نادر")) { gVal = 10000; eVal = 4000; }
        else if (chest.type.equals("ملحمي")) { gVal = 20000; eVal = 8000; }
        else if (chest.type.equals("أسطوري")) { gVal = 50000; eVal = 15000; }
        else if (chest.type.equals("خرافي")) { gVal = 100000; eVal = 30000; }
        else if (chest.type.equals("خرافي مطلق")) { gVal = 250000; eVal = 70000; }

        p.inventory.add(newItem);
        p.gold += gVal;
        PlayerSystem.addExp(eVal);
        p.lockedChests.remove(chestIdx);

        GameApp.sound.playSnd("chest_loot.mp3");
        GameApp.sound.playSnd("reward.mp3");
        SaveSystem.saveAndRefresh();

        final String itemName = newItem.name;
        final String itemRarity = newItem.rarity == null || newItem.rarity.isEmpty() ? "عادي" : newItem.rarity;
        final String itemDesc = newItem.desc == null ? "" : newItem.desc.replaceAll("(\\d+)\\.0(?=\\D|$)", "$1");
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("فتح الصندوق", "Opening the Chest"));
        b.setView(U.msg(GameApp.T("لقد حصلت على:\n-" + itemName + "(" + GameApp.rarity(itemRarity) + ")\n- ذهب:" + gVal + "\n- خبرة:" + eVal + "\n\nالوصف:" + itemDesc, "You got:\n-" + itemName + "(" + GameApp.rarity(itemRarity) + ")\n- Gold:" + gVal + "\n- Experience:" + eVal + "\n\nDescription:" + itemDesc)));
        b.setPositiveButton(GameApp.T("استلام الجائزة", "Claim the Reward"), (d, w) -> openFoundChestsUI());
        b.setCancelable(false);
        b.show();
    }

    private static void expandChests() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        int currentExtra = p.chestSlotsExtra;
        int nextSlot = 6 + currentExtra;
        long costGold = 100000L * (currentExtra + 1);
        long costCrystal = 5L * (currentExtra + 1);
        long costDiamond = 1L * (currentExtra + 1);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("توسعة مساحة الصناديق", "Expand Chest Storage"));
        b.setView(U.msg(GameApp.T("هل تريد فتح المساحة رقم" + nextSlot + "؟\n\nالتكلفة:\n- ذهب:" + NumberUtil.formatNumber(costGold) + "\n- كريستال:" + costCrystal + "\n- ألماس:" + costDiamond, "Do you want to open slot number" + nextSlot + "?\n\nCost:\n- Gold:" + NumberUtil.formatNumber(costGold) + "\n- Crystal:" + costCrystal + "\n- Diamond:" + costDiamond)));
        b.setPositiveButton(GameApp.T("توسعة", "Expand"), (d, w) -> {
            if (p.gold >= costGold && p.crystals >= costCrystal && p.diamonds >= costDiamond) {
                p.gold -= costGold;
                p.crystals -= costCrystal;
                p.diamonds -= costDiamond;
                p.chestSlotsExtra += 1;
                PlayerSystem.addPlayerNews(GameApp.T("قمت بتوسعة مساحة الصناديق إلى" + (5 + p.chestSlotsExtra) + "مساحات.", "You expanded your chest storage to" + (5 + p.chestSlotsExtra) + "slots."),
                        "You expanded your chest storage to " + (5 + p.chestSlotsExtra) + " slots.");
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("تمت التوسعة بنجاح!", "Expansion completed successfully!"), GameApp.T("حسنا", "OK"), null);
                openFoundChestsUI();
            } else {
                U.alert(ctx, null, GameApp.T("الموارد غير كافية!", "Not enough resources!"), GameApp.T("حسنا", "OK"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }
}
