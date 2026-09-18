package com.lli.com.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.ItemData;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UnifiedShop {
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static void openUnifiedShop() {
        final Context ctx = GameApp.uiCtx();
        U.toast(GameApp.T("جاري تحميل المتجر...", "Loading shop..."));
        new Thread(() -> {
            JSONObject allShops = Db.get("shops.json");
            UI.post(() -> buildShopUI(allShops));
        }).start();
    }

    private static void buildShopUI(JSONObject allShops) {
        final Context ctx = GameApp.uiCtx();
        if (allShops == null) {
            U.alert(ctx, null, GameApp.T("قاعدة بيانات المتجر فارغة أو لا يوجد اتصال!", "Shop database is empty or no connection!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final String[] cats = {GameApp.T("الأسلحة والدروع", "Weapons & Armor"), GameApp.T("الجرعات", "Potions"), GameApp.T("سوق المرافقين", "Companion Market")};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("المتجر الملكي الشامل", "Grand Royal Shop"));
        b.setItems(cats, (d, i) -> buildCategoryUI(allShops, i));
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void buildCategoryUI(JSONObject allShops, int i) {
        final Context ctx = GameApp.uiCtx();
        List<JSONObject> shopData = new ArrayList<>();
        if (i == 0) {
            JSONArray arr = allShops.optJSONArray("equip");
            if (arr != null) {
                for (int k = 0; k < arr.length(); k++) {
                    JSONObject o = arr.optJSONObject(k);
                    if (o != null) shopData.add(o);
                }
            }
            addRoyalItem(shopData, GameApp.T("حذاء الرياح السريع", "Swift Wind Boots"), 50000000L, GameApp.T("يزيد سرعة الاستكشاف بنسبة 15%", "Increases exploration speed by 15%"), "ملكي", "أحذية", 0.15, 0);
            addRoyalItem(shopData, GameApp.T("خف البرق الخاطف", "Lightning Flash Slippers"), 250000000L, GameApp.T("يزيد سرعة الاستكشاف بنسبة 30%", "Increases exploration speed by 30%"), "ملكي", "أحذية", 0.30, 0);
            addRoyalItem(shopData, GameApp.T("أجنحة الأثير الأسطورية", "Legendary Aether Wings"), 1250000000L, GameApp.T("تزيد سرعة الاستكشاف بنسبة 45%", "Increases exploration speed by 45%"), "ملكي", "أجنحة", 0.45, 0);
            addRoyalItem(shopData, GameApp.T("تاج الضوء السرمدي", "Crown of Eternal Light"), 6250000000L, GameApp.T("يزيد الهجوم بنسبة 60% وسرعة الاستكشاف بنسبة 60%", "Increases attack by 60% and exploration speed by 60%"), "ملكي", "خوذ", 0.60, 0.60);
            addRoyalItem(shopData, GameApp.T("وشاح البرق الخارق", "Super Lightning Scarf"), 37500000000L, GameApp.T("يزيد سرعة الاستكشاف بنسبة 80%", "Increases exploration speed by 80%"), "ملكي", "أدوات مساعدة", 0.80, 0);
            addRoyalItem(shopData, GameApp.T("محرك الزمن الأسطوري", "Legendary Time Engine"), 375000000000L, GameApp.T("يزيد سرعة الاستكشاف بنسبة 100%", "Increases exploration speed by 100%"), "ملكي", "أدوات مساعدة", 1.00, 0);
        } else if (i == 1) {
            JSONArray arr = allShops.optJSONArray("crystals");
            if (arr != null) {
                for (int k = 0; k < arr.length(); k++) {
                    JSONObject o = arr.optJSONObject(k);
                    if (o != null) shopData.add(o);
                }
            }
        } else if (i == 2) {
            JSONArray arr = allShops.optJSONArray("pets");
            if (arr != null) {
                for (int k = 0; k < arr.length(); k++) {
                    JSONObject o = arr.optJSONObject(k);
                    if (o != null) shopData.add(o);
                }
            }
        }

        List<String> opts = new ArrayList<>();
        for (JSONObject v : shopData) {
            String priceTxt = (i == 1) ? (v.optLong("price", 0) + GameApp.T("كريستال", "crystals")) : (v.optLong("price", 0) + GameApp.T("ذهب", "gold"));
            opts.add(v.optString("name", "") + "-" + priceTxt + "\n(" + v.optString("desc", "") + ")");
        }
        if (opts.isEmpty()) {
            U.alert(ctx, null, GameApp.T("فارغ حالياً!", "Currently empty!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final List<JSONObject> items = shopData;
        final int cat = i;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(cats(cat));
        b.setItems(opts.toArray(new String[0]), (d2, pos) -> buyItem(ctx, items.get(pos), cat));
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), (d2, w) -> openUnifiedShop());
        b.show();
    }

    private static String cats(int i) {
        return new String[]{GameApp.T("الأسلحة والدروع", "Weapons & Armor"), GameApp.T("الجرعات", "Potions"), GameApp.T("سوق المرافقين", "Companion Market")}[i];
    }

    private static void addRoyalItem(List<JSONObject> list, String name, long price, String desc, String category, String subType, double speed, double atkMult) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("price", price);
            o.put("desc", desc);
            o.put("category", category);
            o.put("sub_type", subType);
            o.put("speed_bonus", speed);
            o.put("atk_multiplier", atkMult);
            o.put("is_royal", true);
            o.put("boosts", boostsForRoyalItem(price));
        } catch (Exception ignored) {}
        list.add(o);
    }

    private static JSONObject boostsForRoyalItem(long price) {
        JSONObject b = new JSONObject();
        double k = Math.max(1, Math.pow(price / 50000000.0, 0.45));
        try {
            b.put("str", (long) Math.floor(20 * k));
            b.put("end", (long) Math.floor(20 * k));
            b.put("hp", (long) Math.floor(200 * k));
            b.put("agi", (long) Math.floor(10 * k));
            b.put("lck", (long) Math.floor(10 * k));
        } catch (Exception ignored) {}
        return b;
    }

    private static void buyItem(final Context ctx, final JSONObject sel, final int i) {
        final PlayerData p = GameApp.player;
        if (i == 0) {
            boolean owned = false;
            for (ItemData inv : p.inventory) {
                if (inv != null && inv.name.equals(sel.optString("name", ""))) { owned = true; break; }
            }
            if (owned) {
                U.alert(ctx, null, GameApp.T("تملك هذا مسبقاً!", "You already own this!"), GameApp.T("حسنا", "OK"), v -> openUnifiedShop());
            } else if (p.gold >= sel.optLong("price", 0)) {
                p.gold -= sel.optLong("price", 0);
                ItemData it = ItemData.fromJSON(sel);
                if (it.name == null || it.name.isEmpty()) it.name = sel.optString("name", "");
                if (it.level < 1) it.level = 1;
                if (it.price <= 0) it.price = sel.optLong("price", 0);
                if (sel.has("is_royal")) it.isRoyal = sel.optBoolean("is_royal", false);
                p.inventory.add(it);
                GameApp.sound.playSnd("buy.mp3");
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("تم الشراء بنجاح!", "Purchase successful!"), GameApp.T("حسنا", "OK"), v -> openUnifiedShop());
            } else {
                U.alert(ctx, null, GameApp.T("ذهب غير كاف!", "Not enough gold!"), GameApp.T("حسنا", "OK"), v -> openUnifiedShop());
            }
        } else if (i == 1) {
            if (p.crystals >= sel.optLong("price", 0)) {
                p.crystals -= sel.optLong("price", 0);
                ItemData it = ItemData.fromJSON(sel);
                if (it.name == null || it.name.isEmpty()) it.name = sel.optString("name", "");
                if (it.level < 1) it.level = 1;
                it.isPotion = true;
                it.count = 1;
                p.inventory.add(it);
                GameApp.sound.playSnd("buy.mp3");
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("تم شراء الجرعة وأضيفت إلى حقيبتك! افتح الحقيبة ثم الجرع لشربها.", "Potion purchased and added to your bag! Open the bag then Potions to drink it."), GameApp.T("حسنا", "OK"), v -> openUnifiedShop());
            } else {
                U.alert(ctx, null, GameApp.T("كريستال غير كاف!", "Not enough crystals!"), GameApp.T("حسنا", "OK"), v -> openUnifiedShop());
            }
        } else if (i == 2) {
            if (p.gold >= sel.optLong("price", 0)) {
                p.gold -= sel.optLong("price", 0);
                p.pet = sel.optString("name", "");
                String type = sel.optString("type", "");
                double val = sel.optDouble("val", 0);
                if (type.equals("luck")) p.stats.luck += val;
                else if (type.equals("str")) p.stats.strength += val;
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("تم الشراء!", "Purchased!"), GameApp.T("حسنا", "OK"), v -> openUnifiedShop());
            } else {
                U.alert(ctx, null, GameApp.T("ذهب غير كاف!", "Not enough gold!"), GameApp.T("حسنا", "OK"), v -> openUnifiedShop());
            }
        }
    }
}
