package com.lli.com.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.ItemData;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;
import com.lli.com.core.TempStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InventoryUI {
    private static final String[] CATEGORIES = {"أسلحة", "دروع", "أدوات مساعدة", "جرع", "مخطوطات", "المتجر الملكي"};
    private static final java.util.Map<String, Integer> RARITY_ORDER = new java.util.HashMap<>();

    static {
        String[] order = {"عادي", "غير عادي", "نادر", "ملحمي", "أسطوري", "خرافي", "خرافي مطلق"};
        for (int i = 0; i < order.length; i++) RARITY_ORDER.put(order[i], i + 1);
    }

    private static String[] categoryLabels() {
        return new String[]{
                GameApp.T("أسلحة", "Weapons"),
                GameApp.T("دروع", "Armor"),
                GameApp.T("أدوات مساعدة", "Support Tools"),
                GameApp.T("جرع", "Potions"),
                GameApp.T("مخطوطات", "Scrolls"),
                GameApp.T("المتجر الملكي", "Royal Shop")
        };
    }

    private static String categoryLabel(String key) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(key)) return categoryLabels()[i];
        }
        return key;
    }

    private static class ItemEntry {
        ItemData item;
        int originalIndex;
        boolean isEq;
        double power;
    }

    public static double getItemPower(ItemData v) {
        double p = 0;
        if (v.boosts != null) p += v.boosts.str + v.boosts.end + v.boosts.hp / 2.0 + v.boosts.agi + v.boosts.lck;
        if (v.stats != null) p += v.stats.strength + v.stats.endurance + v.stats.maxHp / 2.0;
        p += v.upgradeLvl * 50;
        if (v.atkMultiplier > 0) p += v.atkMultiplier * 100;
        if (v.speedBonus > 0) p += v.speedBonus * 100;
        return p;
    }

    public static void openInventoryUI() {
        Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        int cur = p != null ? p.bagLimitPerSection() : 6;
        int cost = p != null ? p.getBagExpansionCost() : 75;
        List<String> opts = new ArrayList<>();
        for (String c : categoryLabels()) opts.add(c);
        opts.add(GameApp.T("توسيع الحقيبة (+3 لكل قسم)\nالتكلفة: " + cost + " كريستال | الحد الحالي: " + cur + "/قسم",
                "Expand Bag (+3 per section)\nCost: " + cost + " crystals | Current limit: " + cur + "/section"));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الحقيبة - اختر الفئة (الحد: " + cur + "/قسم)", "Bag - Choose Category (limit: " + cur + "/section)"));
        b.setItems(opts.toArray(new String[0]), (d, catIdx) -> {
            if (catIdx < CATEGORIES.length) openInventoryCategoryUI(CATEGORIES[catIdx]);
            else openBagExpandUI();
        });
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), (d, w) -> {
            if (GameNav.currentTab >= 1) GameNav.currentTab = 1;
        });
        b.show();
    }

    private static void openBagExpandUI() {
        Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        if (p == null) return;
        final int cur = p.bagLimitPerSection();
        final int cost = p.getBagExpansionCost();
        final int next = cur + 3;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("توسيع الحقيبة 🎒", "Expand Bag 🎒"));
        b.setView(U.msg(GameApp.T("الحد الحالي: ", "Current limit: ") + cur + GameApp.T(" عنصر/قسم\nالحد بعد التوسيع: ", " items/section\nLimit after expansion: ") + next + GameApp.T(" عنصر/قسم\nالتكلفة: ", " items/section\nCost: ") + cost + GameApp.T(" كريستال\n\nكل توسعة تضيف 3 أماكن لكل قسم، وسعر كل توسعة يزيد بمقدار كريستال واحد.", " crystals\n\nEach expansion adds 3 slots to every section, and each subsequent expansion costs 1 more crystal.")));
        b.setPositiveButton(GameApp.T("تأكيد (" + cost + " كريستال)", "Confirm (" + cost + " crystals)"), (d, w) -> {
            if (p.crystals < cost) {
                U.alert(ctx, null, GameApp.T("كريستال غير كافٍ! تحتاج ", "Not enough crystals! You need ") + cost + GameApp.T(" كريستال.", " crystals."), GameApp.T("حسنا", "OK"), null);
                return;
            }
            p.crystals -= cost;
            p.bagExpansions += 1;
            SaveSystem.saveAndRefresh();
            U.alert(ctx, null, GameApp.T("تم توسيع الحقيبة! الحد أصبح ", "Bag expanded! The limit is now ") + p.bagLimitPerSection() + GameApp.T(" عنصر لكل قسم.", " items per section."), GameApp.T("رائع", "Great"), null);
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    public static void openInventoryCategoryUI(String selectedCat) {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        List<ItemEntry> itemsData = new ArrayList<>();
        int secCount = 0;
        if (p.inventory != null) {
            for (int i = 0; i < p.inventory.size(); i++) {
                ItemData v = p.inventory.get(i);
                if (v == null) continue;
                boolean match = false;
                if (selectedCat.equals("أسلحة")) match = v.category.equals("سلاح");
                else if (selectedCat.equals("دروع")) match = v.category.equals("دروع");
                else if (selectedCat.equals("أدوات مساعدة")) match = v.category.equals("أدوات مساعدة") && !v.isScroll && !(v.name != null && v.name.contains("مخطوطة"));
                else if (selectedCat.equals("جرع")) match = v.name.contains("جرعة") || v.isPotion;
                else if (selectedCat.equals("مخطوطات")) match = v.name.contains("مخطوطة") || v.isScroll;
                else if (selectedCat.equals("المتجر الملكي")) match = v.category.equals("المتجر الملكي") || v.category.equals("ملكي");
                if (match) {
                    secCount++;
                    ItemEntry e = new ItemEntry();
                    e.item = v;
                    e.originalIndex = i;
                    e.isEq = false;
                    if (p.equipped != null) {
                        for (String eq : p.equipped) {
                            if (eq.equals(v.name)) { e.isEq = true; break; }
                        }
                    }
                    e.power = getItemPower(v);
                    itemsData.add(e);
                }
            }
            itemsData.sort(new Comparator<ItemEntry>() {
                @Override
                public int compare(ItemEntry a, ItemEntry b) {
                    if (a.isEq != b.isEq) return a.isEq ? -1 : 1;
                    if (a.power != b.power) return Double.compare(b.power, a.power);
                    int ra = RARITY_ORDER.getOrDefault(a.item.rarity, 0);
                    int rb = RARITY_ORDER.getOrDefault(b.item.rarity, 0);
                    return Integer.compare(rb, ra);
                }
            });
        }

        List<String> itemsNames = new ArrayList<>();
        for (ItemEntry d : itemsData) {
            ItemData v = d.item;
            String rarLabel = (v.rarity != null && !v.rarity.isEmpty()) ? " [" + GameApp.rarity(v.rarity) + "]" : "";
            String lvlLabel = v.level > 0 ? " " + GameApp.T("مستوى", "Level") + " " + v.level : "";
            String powerLabel = " " + GameApp.T("القوة:", "Power:") + (long) Math.floor(d.power);
            String subLabel = (v.category.equals("سلاح") && v.subType != null && !v.subType.isEmpty()) ? " (" + v.subType + ")" : "";
            String displayName = v.name + subLabel + rarLabel + lvlLabel + powerLabel;
            itemsNames.add(displayName + (d.isEq ? " " + GameApp.T("[مجهز]", "[Equipped]") : ""));
        }

        if (itemsNames.isEmpty()) {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(categoryLabel(selectedCat) + " (" + secCount + "/" + GameApp.player.bagLimitPerSection() + ")");
            b.setView(U.msg(GameApp.T("لا توجد عناصر في هذه الفئة!", "No items in this category!")));
            b.setPositiveButton(GameApp.T("رجوع", "Back"), (d, w) -> openInventoryUI());
            b.show();
            return;
        }

        if (selectedCat.equals("جرع")) {
            openPotionCategoryUI(ctx, itemsData);
            return;
        }

        final List<ItemEntry> data = itemsData;
        AlertDialog.Builder catDialog = new AlertDialog.Builder(ctx);
        catDialog.setTitle(categoryLabel(selectedCat) + " (" + secCount + "/" + GameApp.player.bagLimitPerSection() + ")");
        catDialog.setItems(itemsNames.toArray(new String[0]), (d, pos) -> openItemDetails(ctx, selectedCat, data, pos));
        catDialog.setPositiveButton(GameApp.T("إغلاق", "Close"), (d, w) -> openInventoryUI());
        catDialog.show();
    }

    private static void openPotionCategoryUI(Context ctx, List<ItemEntry> data) {
        PlayerData p = GameApp.player;
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(30, 30, 30, 30);
        TextView timer = U.text(ctx, "", 15, Color.YELLOW, false);
        timer.setGravity(Gravity.CENTER);
        timer.setPadding(0, 0, 0, 20);
        lay.addView(timer);
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<Integer> idxs = new ArrayList<>();
        for (ItemEntry e : data) {
            ItemData it = e.item;
            String durTxt = potionDurationText(it);
            String lbl = it.name + " x" + it.count + (durTxt.isEmpty() ? "" : "\n" + durTxt);
            Button btn = U.btn(ctx, lbl);
            btn.setOnClickListener(v -> openItemDetails(ctx, "جرع", data, data.indexOf(e)));
            lay.addView(btn);
            ids.add(it.name);
            idxs.add(e.originalIndex);
        }
        final AlertDialog[] holder = new AlertDialog[1];
        final TextView fTimer = timer;
        final Runnable updater = new Runnable() {
            @Override
            public void run() {
                if (holder[0] == null || !holder[0].isShowing()) return;
                TempStats ts = p == null ? null : p.tempStats;
                if (ts != null && ts.isTimeType() && ts.remainingSeconds() > 0) {
                    long sec = ts.remainingSeconds();
                    fTimer.setText(GameApp.T("جرعة نشطة — متبقي ", "Active potion — remaining ") + (sec / 60) + ":" + String.format(java.util.Locale.US, "%02d", sec % 60));
                    new Handler(Looper.getMainLooper()).postDelayed(this, 1000);
                } else if (ts != null && ts.isBattlesType()) {
                    fTimer.setText(GameApp.T("جرعة نشطة — معارك متبقية: ", "Active potion — battles left: ") + ts.battlesLeft);
                } else {
                    fTimer.setText(GameApp.T("لا توجد جرعة نشطة حالياً.", "No potion active right now."));
                }
            }
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الجرع", "Potions"));
        b.setView(U.scroll(ctx, lay));
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), (d, w) -> {
            if (holder[0] != null) { holder[0].dismiss(); }
            openInventoryUI();
        });
        holder[0] = b.create();
        holder[0].setOnDismissListener(d -> updater.run());
        holder[0].show();
        updater.run();
    }

    private static String potionDurationText(ItemData it) {
        StringBuilder sb = new StringBuilder();
        if (it.potionDurMin > 0) {
            sb.append(GameApp.T("المدة: ", "Duration: ")).append(it.potionDurMin).append(GameApp.T(" دقيقة", " min"));
        } else if (it.potionDurBattles > 0) {
            sb.append(GameApp.T("المدة: ", "Duration: ")).append(it.potionDurBattles).append(GameApp.T(" معركة", " battles"));
        }
        String val = potionStatLabel(it);
        if (!val.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(val);
        }
        return sb.toString();
    }

    private static String potionStatLabel(ItemData it) {
        String t = it.potionType == null ? "" : it.potionType;
        double v = it.potionVal;
        if (t.equals("str")) return GameApp.T("هجوم +", "Atk +") + (long) v;
        if (t.equals("end")) return GameApp.T("دفاع +", "Def +") + (long) v;
        if (t.equals("agi")) return GameApp.T("رشاقة +", "Agi +") + (long) v;
        if (t.equals("luck")) return GameApp.T("حظ +", "Luck +") + (long) v;
        if (t.equals("hp")) return GameApp.T("صحة +", "HP +") + (long) v;
        return "";
    }

    // Full "performance" block for any item: rarity/type, boosts, multipliers,
    // scroll absorb stats and potion effect — so the player always knows what
    // an item actually does in the bag.
    private static String itemPerfSummary(ItemData item) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        String rar = (item.rarity != null && !item.rarity.isEmpty()) ? GameApp.T("الندرة: ", "Rarity: ") + GameApp.rarity(item.rarity) : "";
        if (!rar.isEmpty()) { sb.append(rar); any = true; }
        if (item.subType != null && !item.subType.isEmpty()) {
            if (any) sb.append(" | ");
            sb.append(GameApp.T("النوع: ", "Type: ")).append(item.subType);
            any = true;
        }
        if (item.level > 1) {
            if (any) sb.append(" | ");
            sb.append(GameApp.T("المستوى المطلوب: ", "Required Level: ")).append(item.level);
            any = true;
        }
        if (item.boosts != null && (item.boosts.str > 0 || item.boosts.end > 0 || item.boosts.hp > 0 || item.boosts.agi > 0 || item.boosts.lck > 0)) {
            StringBuilder b = new StringBuilder();
            boolean f = true;
            if (item.boosts.str > 0) { if (!f) b.append(", "); b.append(GameApp.T("هجوم +", "Atk +")).append((long) item.boosts.str); f = false; }
            if (item.boosts.end > 0) { if (!f) b.append(", "); b.append(GameApp.T("دفاع +", "Def +")).append((long) item.boosts.end); f = false; }
            if (item.boosts.hp > 0) { if (!f) b.append(", "); b.append(GameApp.T("صحة +", "HP +")).append((long) item.boosts.hp); f = false; }
            if (item.boosts.agi > 0) { if (!f) b.append(", "); b.append(GameApp.T("رشاقة +", "Agi +")).append((long) item.boosts.agi); f = false; }
            if (item.boosts.lck > 0) { if (!f) b.append(", "); b.append(GameApp.T("حظ +", "Luck +")).append((long) item.boosts.lck); f = false; }
            if (any) sb.append("\n");
            sb.append(GameApp.T("التعزيزات:", "Boosts:")).append(b);
            any = true;
        }
        if (item.atkMultiplier > 0) {
            if (any) sb.append("\n");
            sb.append(GameApp.T("ضعف الهجوم: ", "Attack Multiplier: ")).append(fmtNum(item.atkMultiplier)).append("x");
            any = true;
        }
        if (item.speedBonus > 0) {
            if (any) sb.append("\n");
            sb.append(GameApp.T("سرعة إضافية: ", "Speed Bonus: ")).append(fmtNum(item.speedBonus));
            any = true;
        }
        if (item.isPotion && item.potionVal > 0) {
            String pv = potionStatLabel(item);
            if (!pv.isEmpty()) {
                if (any) sb.append("\n");
                sb.append(GameApp.T("التأثير:", "Effect:")).append(pv);
                any = true;
            }
        }
        if (item.isScroll && item.stats != null) {
            if (any) sb.append("\n");
            sb.append(GameApp.T("عند القراءة (استيعاب دائم):", "When read (permanent absorb):"));
            boolean f = true;
            if (item.stats.strength != 0) { if (!f) sb.append(", "); sb.append(GameApp.T("هجوم +", "Atk +")).append((long) item.stats.strength); f = false; }
            if (item.stats.endurance != 0) { if (!f) sb.append(", "); sb.append(GameApp.T("دفاع +", "Def +")).append((long) item.stats.endurance); f = false; }
            if (item.stats.maxHp != 0) { if (!f) sb.append(", "); sb.append(GameApp.T("صحة +", "HP +")).append((long) item.stats.maxHp); f = false; }
            if (item.stats.luck != 0) { if (!f) sb.append(", "); sb.append(GameApp.T("حظ +", "Luck +")).append((long) item.stats.luck); f = false; }
            if (f) sb.append(GameApp.T(" لا توجد إحصائيات محددة", " no specific stats"));
            any = true;
        }
        if (!any) {
            sb.append(GameApp.T("لا توجد تفاصيل أداء لهذا العنصر.", "No performance details for this item."));
        }
        return sb.toString();
    }

    private static void openItemDetails(Context ctx, final String selectedCat, final List<ItemEntry> data, final int posInData) {
        PlayerData p = GameApp.player;
        final ItemEntry entry = data.get(posInData);
        final ItemData item = entry.item;
        final String itemName = item.name;
        final int originalPos = entry.originalIndex;

        String displayDesc = item.desc == null ? GameApp.T("بدون وصف", "No description") : item.desc;
        displayDesc = displayDesc == null ? "" : displayDesc.replaceAll("(\\d+)\\.0(?=\\D|$)", "$1");
        if ((item.category.equals("سلاح") || item.category.equals("دروع") || item.category.equals("أدوات مساعدة")) && item.upgradeLvl > 0) {
            displayDesc = displayDesc + "\n\n" + GameApp.T("مستوى الترقية: +", "Upgrade Level: +") + item.upgradeLvl + "(" + GameApp.T("أقصى حد: +30", "Max: +30") + ")";
        } else if (item.category.equals("سلاح") || item.category.equals("دروع") || item.category.equals("أدوات مساعدة")) {
            displayDesc = displayDesc + "\n\n" + GameApp.T("قابل للترقية (مستوى الترقية: +0 / أقصى حد: +30)", "Upgradeable (Upgrade Level: +0 / Max: +30)");
        }
        displayDesc = displayDesc + "\n\n---\n" + itemPerfSummary(item);

        final AlertDialog[] itemDialog = new AlertDialog[1];
        AlertDialog.Builder menu = new AlertDialog.Builder(ctx);
        menu.setTitle(itemName);
        menu.setView(U.msg(displayDesc));
        final int eqIdx = findEquipIndex(p, itemName);
        final Runnable refresh = () -> openInventoryCategoryUI(selectedCat);

        Runnable doSellItem = () -> {
            long sellPrice = (long) Math.floor((item.price) / 2.0) + item.upgradeLvl * (long) Math.floor(item.price * 0.05);
            p.gold += sellPrice;
            if (eqIdx != -1) {
                p.equipped.remove(eqIdx);
                if (item.boosts != null) {
                    p.stats.strength -= item.boosts.str;
                    p.stats.maxHp -= item.boosts.hp;
                    p.stats.agility -= item.boosts.agi;
                    p.stats.luck -= item.boosts.lck;
                    p.stats.endurance -= item.boosts.end;
                }
                if (item.atkMultiplier != 0 && item.appliedAtkBonus != 0) {
                    p.stats.strength -= item.appliedAtkBonus;
                    item.appliedAtkBonus = 0;
                }
            }
            p.inventory.remove(originalPos);
            PlayerSystem_addNews(GameApp.T("قرأت مخطوطة وامتصصت قوتها:", "I read a scroll and absorbed its power:"), "I read a scroll and absorbed its power: " + itemName);
            GameApp.sound.playSnd("sell.mp3");
            SaveSystem.saveAndRefresh();
            if (itemDialog[0] != null) itemDialog[0].dismiss();
            U.alert(ctx, null, GameApp.T("تم بيع", "Sold") + itemName + GameApp.T("بـ", "for") + sellPrice + GameApp.T("ذهب", "gold"), GameApp.T("حسنا", "OK"), null);
        };

        if (item.isScroll) {
            menu.setPositiveButton(GameApp.T("قراءة المخطوطة", "Read the Scroll"), (d, w) -> {
                AlertDialog.Builder rd = new AlertDialog.Builder(ctx);
                rd.setTitle(GameApp.T("قراءة...", "Reading..."));
                rd.setView(U.msg(GameApp.T("تفتح المخطوطة وتبدأ في قراءة الكلمات القديمة... تشعر بطاقة هائلة تتدفق في جسدك!", "The scroll opens and you begin reading the ancient words... You feel an immense power flowing through your body!")));
                rd.setPositiveButton(GameApp.T("استيعاب القوة", "Absorb the Power"), (d2, w2) -> {
                    if (item.stats != null) {
                        p.stats.strength += item.stats.strength;
                        p.stats.endurance += item.stats.endurance;
                        p.stats.maxHp += item.stats.maxHp;
                        p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
                        p.stats.luck += item.stats.luck;
                    }
                    p.inventory.remove(originalPos);
                    PlayerSystem_addNews(GameApp.T("قرأت مخطوطة:", "I read a scroll:"), "I read a scroll: " + itemName);
                    SaveSystem.saveAndRefresh();
                    if (itemDialog[0] != null) itemDialog[0].dismiss();
                    U.alert(ctx, null, GameApp.T("اختفت المخطوطة وأصبحت قوتها جزءاً منك للأبد!", "The scroll vanished and its power became a part of you forever!"), GameApp.T("رائع", "Amazing"), null);
                });
                rd.show();
            });
            menu.setNegativeButton(GameApp.T("إغلاق", "Close"), (d, w) -> {
                if (itemDialog[0] != null) itemDialog[0].dismiss();
                refresh.run();
            });
        } else if (item.isPotion) {
            String durTxt = potionDurationText(item);
            String detail = displayDesc;
            if (!durTxt.isEmpty()) detail = detail + (detail.isEmpty() ? "" : "\n") + durTxt;
            menu.setView(U.msg(detail));
            menu.setPositiveButton(GameApp.T("شرب الجرعة", "Drink Potion"), (d, w) -> drinkPotion(ctx, originalPos, selectedCat, itemDialog));
            menu.setNeutralButton(GameApp.T("بيع", "Sell"), (d, w) -> doSellItem.run());
            menu.setNegativeButton(GameApp.T("إغلاق", "Close"), (d, w) -> {
                if (itemDialog[0] != null) itemDialog[0].dismiss();
                refresh.run();
            });
        } else if (item.category.equals("سلاح") || item.category.equals("دروع") || item.category.equals("أدوات مساعدة") || item.category.equals("ملكي") || item.isRoyal) {
            if (eqIdx == -1) {
                menu.setPositiveButton(GameApp.T("تجهيز", "Equip"), (d, w) -> {
                    if (p.level < item.level) {
                        U.alert(ctx, null, GameApp.T("مستواك غير كافٍ لارتداء هذا العنصر! المطلوب:", "Your level is too low to wear this item! Required:") + item.level, GameApp.T("حسنا", "OK"), v -> openItemDetails(ctx, selectedCat, data, posInData));
                        return;
                    }
                    if (p.equipped == null) p.equipped = new ArrayList<>();
                    int categoryCount = 0;
                    boolean isSameItem = false;
                    for (String eqName : p.equipped) {
                        for (ItemData invItem : p.inventory) {
                            if (invItem != null && invItem.name.equals(eqName)) {
                                if (invItem.category.equals(item.category)) {
                                    categoryCount++;
                                    if (invItem.name.equals(item.name)) isSameItem = true;
                                }
                                break;
                            }
                        }
                    }
                    boolean canEquip = true;
                    String errorMsg = "";
                    if (item.category.equals("دروع") || item.category.equals("أدوات مساعدة")) {
                        if (isSameItem) { canEquip = false; errorMsg = GameApp.T("لا يمكنك تجهيز عنصرين متطابقين من نفس النوع!", "You cannot equip two identical items of the same type!"); }
                        else if (categoryCount >= 2) { canEquip = false; errorMsg = GameApp.T("لا يمكنك تجهيز أكثر من عنصرين من فئة", "You cannot equip more than two items from category") + item.category + "!"; }
                    } else {
                        if (categoryCount >= 1) { canEquip = false; errorMsg = GameApp.T("لا يمكنك تجهيز أكثر من عنصر واحد من فئة", "You cannot equip more than one item from category") + item.category + "!"; }
                    }
                    if (!canEquip) {
                        U.alert(ctx, GameApp.T("تنبيه التجهيز", "Equip Warning"), errorMsg, GameApp.T("حسنا", "OK"), v -> openItemDetails(ctx, selectedCat, data, posInData));
                        return;
                    }
                    int reqLvl = item.level;
                    if (p.level < reqLvl) {
                        U.alert(ctx, GameApp.T("مستوى غير كافٍ", "Level Insufficient"), GameApp.T("لا يمكنك تجهيز [", "You cannot equip [") + itemName + "]!\n" + GameApp.T("مستواك الحالي:", "Your current level:") + p.level + "\n" + GameApp.T("المستوى المطلوب:", "Required level:") + reqLvl + "\n\n" + GameApp.T("استمر في التطور وارجع لاحقاً!", "Keep growing and come back later!"), GameApp.T("حسنا", "OK"), v -> openItemDetails(ctx, selectedCat, data, posInData));
                        return;
                    }
                    p.equipped.add(itemName);
                    if (item.boosts != null) {
                        p.stats.strength += item.boosts.str;
                        p.stats.maxHp += item.boosts.hp;
                        p.stats.agility += item.boosts.agi;
                        p.stats.luck += item.boosts.lck;
                        p.stats.endurance += item.boosts.end;
                    }
                    if (item.atkMultiplier > 0) {
                        item.appliedAtkBonus = (long) Math.floor(p.stats.strength * item.atkMultiplier);
                        p.stats.strength += item.appliedAtkBonus;
                    }
                    SaveSystem.saveAndRefresh();
                    if (itemDialog[0] != null) itemDialog[0].dismiss();
                    U.alert(ctx, null, GameApp.T("تم تجهيز:", "Equipped:") + itemName, GameApp.T("حسنا", "OK"), v -> openItemDetails(ctx, selectedCat, data, posInData));
                });
            } else {
                menu.setPositiveButton(GameApp.T("خلع", "Unequip"), (d, w) -> {
                    p.equipped.remove(eqIdx);
                    if (item.boosts != null) {
                        p.stats.strength -= item.boosts.str;
                        p.stats.maxHp -= item.boosts.hp;
                        p.stats.agility -= item.boosts.agi;
                        p.stats.luck -= item.boosts.lck;
                        p.stats.endurance -= item.boosts.end;
                    }
                    if (item.appliedAtkBonus != 0) {
                        p.stats.strength -= item.appliedAtkBonus;
                        item.appliedAtkBonus = 0;
                    }
                    SaveSystem.saveAndRefresh();
                    if (itemDialog[0] != null) itemDialog[0].dismiss();
                    U.alert(ctx, null, GameApp.T("تم خلع:", "Unequipped:") + itemName, GameApp.T("حسنا", "OK"), v -> openItemDetails(ctx, selectedCat, data, posInData));
                });
            }
            menu.setNeutralButton(GameApp.T("ترقية (+", "Upgrade (+") + item.upgradeLvl + ")", (d, w) -> {
                if (itemDialog[0] != null) itemDialog[0].dismiss();
                upgradeItem(ctx, originalPos, selectedCat);
            });
            menu.setNegativeButton(GameApp.T("بيع", "Sell"), (d, w) -> {
                if (itemDialog[0] != null) itemDialog[0].dismiss();
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("تأكيد البيع", "Confirm Sale"));
                b.setView(U.msg(GameApp.T("هل تريد بيع", "Do you want to sell") + itemName + GameApp.T("بـ", "for") + (long) Math.floor((item.price) / 2.0) + GameApp.T("ذهب؟", "gold?")));
                b.setPositiveButton(GameApp.T("بيع", "Sell"), (d2, w2) -> doSellItem.run());
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), (d2, w2) -> openItemDetails(ctx, selectedCat, data, posInData));
                b.show();
            });
            LinearLayout btnLay = U.linear(ctx, true);
            Button closeBtn = U.btn(ctx, GameApp.T("إغلاق", "Close"));
            closeBtn.setOnClickListener(v -> {
                if (itemDialog[0] != null) itemDialog[0].dismiss();
                refresh.run();
            });
            btnLay.addView(closeBtn);
            LinearLayout detailLay = U.linear(ctx, true);
            detailLay.addView(U.msg(displayDesc));
            detailLay.addView(btnLay);
            menu.setView(detailLay);
        } else {
            menu.setPositiveButton(GameApp.T("بيع", "Sell"), (d, w) -> doSellItem.run());
            menu.setNegativeButton(GameApp.T("إغلاق", "Close"), (d, w) -> {
                if (itemDialog[0] != null) itemDialog[0].dismiss();
                refresh.run();
            });
        }
        itemDialog[0] = menu.show();
    }

    private static void drinkPotion(Context ctx, final int originalPos, final String category, final AlertDialog[] itemDialog) {
        PlayerData p = GameApp.player;
        if (p.inventory == null || originalPos < 0 || originalPos >= p.inventory.size()) return;
        final ItemData item = p.inventory.get(originalPos);
        String type = item.potionType == null ? "" : item.potionType;
        double val = item.potionVal;
        if (type.isEmpty() || val <= 0) {
            U.alert(ctx, null, GameApp.T("هذه الجرعة بدون تأثير محدد!", "This potion has no defined effect!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        if (type.equals("hp")) {
            if (p.tempStats != null && p.tempStats.active()) {
                String activeTxt = p.tempStats.isTimeType()
                        ? (GameApp.T("لديك جرعة نشطة متبقيتها: ", "You have an active potion with ") + (p.tempStats.remainingSeconds() / 60) + GameApp.T(" دقيقة", " min"))
                        : (GameApp.T("لديك جرعة نشطة معاركها المتبقية: ", "You have an active potion with battles left: ") + p.tempStats.battlesLeft);
                U.alert(ctx, null, GameApp.T("لا يمكنك شرب جرعتين بنفس الوقت، انتظر انتهاء التأثير الحالي.", "You cannot drink two potions at the same time. Wait for the current effect to end.") + "\n" + activeTxt, GameApp.T("حسنا", "OK"), null);
                return;
            }
            if (p.tempStats == null) p.tempStats = new TempStats();
            p.tempStats.hp = val;
            if (item.potionDurMin > 0) {
                p.tempStats.until = System.currentTimeMillis() + item.potionDurMin * 60000L;
                p.tempStats.battlesLeft = 0;
            } else {
                p.tempStats.until = 0;
                p.tempStats.battlesLeft = item.potionDurBattles > 0 ? item.potionDurBattles : 10;
            }
            p.stats.hp = Math.min(p.getTotalMaxHp(), p.stats.hp + val);
            if (item.count > 1) {
                item.count -= 1;
            } else {
                p.inventory.remove(originalPos);
            }
            String durTxt = item.potionDurMin > 0
                    ? (item.potionDurMin + GameApp.T(" دقيقة", " min"))
                    : (p.tempStats.battlesLeft + GameApp.T(" معركة", " battles"));
            GameApp.sound.playSnd("potion_drink.mp3");
            SaveSystem.saveAndRefresh();
            if (itemDialog[0] != null) itemDialog[0].dismiss();
            U.alert(ctx, null, GameApp.T("شربت جرعة الشفاء! زادت صحتك القصوى مؤقتاً بمقدار", "You drank the healing potion! Your max HP increased temporarily by") + " " + (long) val + "\n" + GameApp.T("المدة: ", "Duration: ") + durTxt, GameApp.T("رائع", "Awesome"), v -> openInventoryCategoryUI(category));
            return;
        }
        if (p.tempStats != null && p.tempStats.active()) {
            String activeTxt = p.tempStats.isTimeType()
                    ? (GameApp.T("لديك جرعة نشطة متبقيتها: ", "You have an active potion with ") + (p.tempStats.remainingSeconds() / 60) + GameApp.T(" دقيقة", " min"))
                    : (GameApp.T("لديك جرعة نشطة معاركها المتبقية: ", "You have an active potion with battles left: ") + p.tempStats.battlesLeft);
            U.alert(ctx, null, GameApp.T("لا يمكنك شرب جرعتين بنفس الوقت، انتظر انتهاء التأثير الحالي.", "You cannot drink two potions at the same time. Wait for the current effect to end.") + "\n" + activeTxt, GameApp.T("حسنا", "OK"), null);
            return;
        }
        if (p.tempStats == null) p.tempStats = new TempStats();
        if (type.equals("str")) p.tempStats.str = val;
        else if (type.equals("end")) p.tempStats.endurance = val;
        else if (type.equals("agi")) p.tempStats.agi = val;
        else if (type.equals("luck")) p.tempStats.luck = val;
        if (item.potionDurMin > 0) {
            p.tempStats.until = System.currentTimeMillis() + item.potionDurMin * 60000L;
            p.tempStats.battlesLeft = 0;
        } else {
            p.tempStats.until = 0;
            p.tempStats.battlesLeft = item.potionDurBattles > 0 ? item.potionDurBattles : 10;
        }
        if (item.count > 1) {
            item.count -= 1;
        } else {
            p.inventory.remove(originalPos);
        }
        StringBuilder msg = new StringBuilder(GameApp.T("شربت الجرعة بنجاح! تأثير:", "You drank the potion! Effect:") + " " + potionStatLabel(item));
        if (item.potionDurMin > 0) msg.append("\n").append(GameApp.T("المدة: ", "Duration: ")).append(item.potionDurMin).append(GameApp.T(" دقيقة", " min"));
        else msg.append("\n").append(GameApp.T("المدة: ", "Duration: ")).append(p.tempStats.battlesLeft).append(GameApp.T(" معركة", " battles"));
        GameApp.sound.playSnd("potion_drink.mp3");
        SaveSystem.saveAndRefresh();
        if (itemDialog[0] != null) itemDialog[0].dismiss();
        U.alert(ctx, null, msg.toString(), GameApp.T("رائع", "Awesome"), v -> openInventoryCategoryUI(category));
    }

    private static int findEquipIndex(PlayerData p, String itemName) {
        if (p.equipped != null) {
            for (int i = 0; i < p.equipped.size(); i++) {
                if (p.equipped.get(i).equals(itemName)) return i;
            }
        }
        return -1;
    }

    private static void PlayerSystem_addNews(String arMsg, String enMsg) {
        com.lli.com.core.PlayerSystem.addPlayerNews(arMsg, enMsg);
    }

    private static double upgradedStat(double v) {
        double nv = Math.floor(v * 1.1);
        return nv > v ? nv : (v > 0 ? v + 1 : 0);
    }

    // Upgrade gain per item type: weapons +12%, armor +10%, support tools +8%.
    private static double upgradeMult(ItemData item) {
        if (item == null || item.category == null) return 1.10;
        if (item.category.equals("سلاح")) return 1.12;
        if (item.category.equals("أدوات مساعدة")) return 1.08;
        return 1.10;
    }

    private static double upgradedStat(double v, double mult) {
        double nv = Math.floor(v * mult);
        return nv > v ? nv : (v > 0 ? v + 1 : 0);
    }

    // Compact stat row for a weapon/armor/tool upgraded by `levels` more levels.
    private static String boostedLine(ItemData item, double mult, int levels) {
        if (item.boosts == null) return "";
        double s = item.boosts.str, e = item.boosts.end, h = item.boosts.hp, a = item.boosts.agi, l = item.boosts.lck;
        for (int i = 0; i < levels; i++) {
            s = upgradedStat(s, mult);
            e = upgradedStat(e, mult);
            h = upgradedStat(h, mult);
            a = upgradedStat(a, mult);
            l = upgradedStat(l, mult);
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (s > 0) { if (!first) sb.append(", "); sb.append(GameApp.T("هجوم +", "Atk +")).append((long) s); first = false; }
        if (e > 0) { if (!first) sb.append(", "); sb.append(GameApp.T("دفاع +", "Def +")).append((long) e); first = false; }
        if (h > 0) { if (!first) sb.append(", "); sb.append(GameApp.T("صحة +", "HP +")).append((long) h); first = false; }
        if (a > 0) { if (!first) sb.append(", "); sb.append(GameApp.T("رشاقة +", "Agi +")).append((long) a); first = false; }
        if (l > 0) { if (!first) sb.append(", "); sb.append(GameApp.T("حظ +", "Luck +")).append((long) l); }
        return sb.toString();
    }

    // Show whole numbers without a trailing ".0"; keep real decimals like "1.5".
    private static String fmtNum(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    public static void upgradeItem(Context ctx, final int posInData, final String category) {
        PlayerData p = GameApp.player;
        if (p.inventory == null || posInData < 0 || posInData >= p.inventory.size()) {
            U.alert(ctx, null, GameApp.T("العنصر غير موجود!", "Item not found!"), GameApp.T("حسنا", "OK"), v -> openInventoryCategoryUI(category));
            return;
        }
        final ItemData item = p.inventory.get(posInData);
        if (item.upgradeLvl >= 30) {
            U.alert(ctx, GameApp.T("أقصى ترقية", "Max Upgrade"), GameApp.T("لقد وصل هذا العنصر إلى أقصى مستوى ترقية (+30)!", "This item has reached its maximum upgrade level (+30)!"), GameApp.T("حسنا", "OK"), v -> openInventoryCategoryUI(category));
            return;
        }
        long costGold = (long) Math.floor((item.price) * Math.pow(1.2, item.upgradeLvl));
        long costCrystal = (long) Math.floor(2 * Math.pow(1.1, item.upgradeLvl));
        int successRate = Math.max(10, 100 - (item.upgradeLvl * 3));
        final double mult = upgradeMult(item);
        String statPreview = "";
        if (item.boosts != null && (item.boosts.str > 0 || item.boosts.end > 0 || item.boosts.hp > 0 || item.boosts.agi > 0 || item.boosts.lck > 0)) {
            statPreview += GameApp.T("\n\nالمواصفات الآن:", "\n\nCurrent stats:")
                    + GameApp.T("هجوم +", "Atk +") + (long) item.boosts.str
                    + GameApp.T(", دفاع +", ", Def +") + (long) item.boosts.end
                    + GameApp.T(", صحة +", ", HP +") + (long) item.boosts.hp
                    + GameApp.T(", رشاقة +", ", Agi +") + (long) item.boosts.agi
                    + GameApp.T(", حظ +", ", Luck +") + (long) item.boosts.lck;
            statPreview += "\n\n" + GameApp.T("نسبة الزيادة لكل مستوى:", "Increase per level:") + (long) Math.round((mult - 1) * 100) + "%";
            statPreview += "\n" + GameApp.T("جدول الترقيات:", "Upgrade table:") + GameApp.T("(+1)", "(+1)") + boostedLine(item, mult, 1);
            statPreview += "\n" + GameApp.T("(+2)", "(+2)") + boostedLine(item, mult, 2);
            statPreview += "\n" + GameApp.T("(+3)", "(+3)") + boostedLine(item, mult, 3);
        }
        if (item.atkMultiplier > 0) {
            statPreview += GameApp.T("\nضعف الهجوم الآن:", "\nAttack multiplier now:") + fmtNum(item.atkMultiplier) + "x"
                    + GameApp.T("→ بعد الترقية:", "→ After upgrade:") + fmtNum(Math.round(item.atkMultiplier * mult * 100.0) / 100.0) + "x";
        }
        String msg = GameApp.T("هل تريد ترقية", "Do you want to upgrade") + item.name + GameApp.T("؟\n", "?\n") +
                GameApp.T("المستوى الحالي: +", "Current Level: +") + item.upgradeLvl + "\n" +
                GameApp.T("المستوى القادم: +", "Next Level: +") + (item.upgradeLvl + 1) + statPreview + "\n\n" +
                GameApp.T("التكلفة:\n- ذهب:", "Cost:\n- Gold:") + NumberUtil.formatNumber(costGold) + GameApp.T("\n- كريستال:", "\n- Crystal:") + costCrystal + "\n\n" +
                GameApp.T("نسبة النجاح:", "Success Rate:") + successRate + "%";
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("ترقية المعدات", "Upgrade Equipment"));
        b.setView(U.msg(msg));
        b.setPositiveButton(GameApp.T("ترقية", "Upgrade"), (d, w) -> {
            if (p.gold >= costGold && p.crystals >= costCrystal) {
                p.gold -= costGold;
                p.crystals -= costCrystal;
                int chance = NumberUtil.rand(1, 100);
                if (chance <= successRate) {
                    boolean isEquipped = false;
                    int eqIdx = findEquipIndex(p, item.name);
                    if (eqIdx != -1) isEquipped = true;
                    if (isEquipped) {
                        if (item.boosts != null) {
                            p.stats.strength -= item.boosts.str;
                            p.stats.maxHp -= item.boosts.hp;
                            p.stats.agility -= item.boosts.agi;
                            p.stats.luck -= item.boosts.lck;
                            p.stats.endurance -= item.boosts.end;
                        }
                        if (item.appliedAtkBonus != 0) {
                            p.stats.strength -= item.appliedAtkBonus;
                            item.appliedAtkBonus = 0;
                        }
                    }
                    item.upgradeLvl += 1;
                    if (item.boosts != null) {
                        item.boosts.str = upgradedStat(item.boosts.str, mult);
                        item.boosts.end = upgradedStat(item.boosts.end, mult);
                        item.boosts.hp = upgradedStat(item.boosts.hp, mult);
                        item.boosts.agi = upgradedStat(item.boosts.agi, mult);
                        item.boosts.lck = upgradedStat(item.boosts.lck, mult);
                    }
                    if (item.atkMultiplier > 0) item.atkMultiplier = Math.round(item.atkMultiplier * mult * 100.0) / 100.0;
                    if (item.speedBonus > 0) item.speedBonus = Math.round(item.speedBonus * mult * 100.0) / 100.0;
                    if (isEquipped) {
                        if (item.boosts != null) {
                            p.stats.strength += item.boosts.str;
                            p.stats.maxHp += item.boosts.hp;
                            p.stats.agility += item.boosts.agi;
                            p.stats.luck += item.boosts.lck;
                            p.stats.endurance += item.boosts.end;
                        }
                        if (item.atkMultiplier > 0) {
                            item.appliedAtkBonus = (long) Math.floor(p.stats.strength * item.atkMultiplier);
                            p.stats.strength += item.appliedAtkBonus;
                        }
                    }
                    GameApp.sound.playSnd("reward.mp3");
                    SaveSystem.saveAndRefresh();
                    U.alert(ctx, GameApp.T("نجاح!", "Success!"), GameApp.T("تمت ترقية", "Successfully upgraded") + item.name + GameApp.T("بنجاح إلى +", "to +") + item.upgradeLvl, GameApp.T("رائع", "Amazing"), v -> openInventoryCategoryUI(category));
                } else {
                    GameApp.sound.playSnd("fail.mp3");
                    SaveSystem.saveAndRefresh();
                    U.alert(ctx, GameApp.T("فشل!", "Failed!"), GameApp.T("للأسف، فشلت عملية الترقية وضاعت الموارد.", "Unfortunately, the upgrade failed and the resources were lost."), GameApp.T("حسنا", "OK"), v -> openInventoryCategoryUI(category));
                }
            } else {
                U.alert(ctx, null, GameApp.T("الموارد غير كافية للترقية!", "Not enough resources to upgrade!"), GameApp.T("حسنا", "OK"), v -> openInventoryCategoryUI(category));
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), (d, w) -> openInventoryCategoryUI(category));
        b.show();
    }
}
