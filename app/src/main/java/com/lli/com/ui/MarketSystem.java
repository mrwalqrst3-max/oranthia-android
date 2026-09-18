package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.ItemData;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MarketSystem {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String MARKET_PATH = "global_market/";
    private static final String USER_PATH = "players/";

    public static void openMarketUI() {
        showMarketItems(0, 9999);
    }

    private static String getItemRarity(JSONObject item) {
        if (item == null) return GameApp.T("عادي", "Common");
        if (!item.optString("rarity", "").isEmpty()) return item.optString("rarity");
        if (item.optBoolean("is_royal", false)) return GameApp.T("أسطوري", "Legendary");
        if (item.optBoolean("is_scroll", false)) return GameApp.T("نادر", "Rare");
        if (item.optString("category", "").equals("سلاح")) return GameApp.T("غير عادي", "Uncommon");
        return GameApp.T("عادي", "Common");
    }

    private static int getItemLevel(JSONObject item) {
        if (item == null) return 1;
        return item.optInt("level", item.optInt("min_level", 1));
    }

    private static void showMarketItems(final int minLvl, final int maxLvl) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("السوق", "Market"), GameApp.T("جاري جلب البضائع...", "Fetching goods..."), true);
        new Thread(() -> {
            JSONObject data = Db.get(MARKET_PATH.substring(0, MARKET_PATH.length() - 1));
            final List<String[]> items = new ArrayList<>(); // id, itemName, rarity, price, sellerName
            final List<JSONObject> itemObjs = new ArrayList<>();
            final List<String> itemIds = new ArrayList<>();
            if (data != null) {
                Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    JSONObject item = data.optJSONObject(id);
                    if (item == null) continue;
                    if (item.optString("seller_id", "").equals(player.username)) continue;
                    JSONObject itemData = item.optJSONObject("item_data");
                    int iLvl = getItemLevel(itemData);
                    if (iLvl >= minLvl && iLvl <= maxLvl) {
                        items.add(new String[]{id, item.optString("item_name", "?"), getItemRarity(itemData),
                                String.valueOf(item.optLong("price", 0)), item.optString("seller_name", "?")});
                        itemObjs.add(item);
                        itemIds.add(id);
                    }
                }
            }
            final List<JSONObject> finalObjs = itemObjs;
            final List<String> finalIds = itemIds;
            UI.post(() -> {
                progress.dismiss();
                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                builder.setTitle(GameApp.T("سوق اللاعبين", "Player Market"));
                if (items.isEmpty()) {
                    builder.setView(U.msg(GameApp.T("لا توجد بضائع حالياً.", "No goods available right now.")));
                } else {
                    final List<String> names = new ArrayList<>();
                    for (String[] it : items) {
                        names.add(it[1] + "|" + it[2] + "|" + it[3] + GameApp.T("ذهب |", "gold |") + it[4]);
                    }
                    builder.setItems(names.toArray(new String[0]), (d, idx) -> {
                        JSONObject selected = finalObjs.get(idx);
                        JSONObject itemData = selected.optJSONObject("item_data");
                        final String itemId = finalIds.get(idx);
                        showItemDetail(selected, itemData, itemId);
                    });
                }
                builder.setPositiveButton(GameApp.T("عروضي", "My Listings"), (d, w) -> showMyListings());
                builder.setNeutralButton(GameApp.T("بيع عنصر", "Sell Item"), (d, w) -> sellItemUI());
                builder.setNegativeButton(GameApp.T("الفلاتر", "Filters"), (d, w) -> {
                    final EditText edMin = new EditText(ctx);
                    edMin.setHint(GameApp.T("من مستوى...", "From level..."));
                    edMin.setInputType(InputType.TYPE_CLASS_NUMBER);
                    final EditText edMax = new EditText(ctx);
                    edMax.setHint(GameApp.T("إلى مستوى...", "To level..."));
                    edMax.setInputType(InputType.TYPE_CLASS_NUMBER);
                    LinearLayout lay = U.linear(ctx, true);
                    lay.setPadding(40, 40, 40, 40);
                    lay.addView(edMin);
                    lay.addView(edMax);
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setTitle(GameApp.T("فلتر المستوى", "Level Filter"));
                    b2.setView(lay);
                    b2.setPositiveButton(GameApp.T("تطبيق", "Apply"), (d2, w2) -> {
                        int minV, maxV;
                        try { minV = Integer.parseInt(edMin.getText().toString().trim()); } catch (Exception e) { minV = 0; }
                        try { maxV = Integer.parseInt(edMax.getText().toString().trim()); } catch (Exception e) { maxV = 9999; }
                        showMarketItems(minV, maxV);
                    });
                    b2.show();
                });
                builder.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                GameApp.sound.setAmbient("amb_market.mp3");
                AlertDialog mkt = builder.show();
                mkt.setOnDismissListener(d -> GameApp.sound.setAmbient("amb_city.mp3"));
            });
        }).start();
    }

    private static void showItemDetail(final JSONObject selected, final JSONObject itemData, final String itemId) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final String rarity = getItemRarity(itemData);
        final int itemLevel = getItemLevel(itemData);
        StringBuilder statsMsg = new StringBuilder();
        if (itemData != null) {
            JSONObject st = itemData.optJSONObject("stats");
            JSONObject bst = itemData.optJSONObject("boosts");
            if (st != null) {
                if (st.optDouble("atk", 0) > 0) statsMsg.append("\n+").append(st.optDouble("atk")).append(GameApp.T("هجوم", "Attack"));
                if (st.optDouble("def", 0) > 0) statsMsg.append("\n+").append(st.optDouble("def")).append(GameApp.T("دفاع", "Defense"));
                if (st.optDouble("hp", 0) > 0) statsMsg.append("\n+").append(st.optDouble("hp")).append(GameApp.T("صحة", "HP"));
            }
            if (bst != null) {
                if (bst.optDouble("str", 0) > 0) statsMsg.append("\n+").append(bst.optDouble("str")).append(GameApp.T("هجوم", "Attack"));
                if (bst.optDouble("end", 0) > 0) statsMsg.append("\n+").append(bst.optDouble("end")).append(GameApp.T("دفاع", "Defense"));
                if (bst.optDouble("hp", 0) > 0) statsMsg.append("\n+").append(bst.optDouble("hp")).append(GameApp.T("صحة", "HP"));
            }
        }
        if (statsMsg.length() == 0) statsMsg.append(GameApp.T("\nلا توجد إحصائيات محددة", "\nNo specific stats"));
        final long price = selected.optLong("price", 0);
        String detailMsg = String.format(Locale.US,
                GameApp.T("الاسم: %s\nالندرة: %s\nالمستوى المطلوب: %d\nالفئة: %s\nالبائع: %s\n\nالإحصائيات:%s\n\nالوصف: %s\n\nالسعر: %d ذهب",
                        "Name: %s\nRarity: %s\nRequired Level: %d\nCategory: %s\nSeller: %s\n\nStats:%s\n\nDescription: %s\n\nPrice: %d gold"),
                selected.optString("item_name", "?"), rarity, itemLevel,
                itemData != null ? itemData.optString("category", "عام") : "عام",
                selected.optString("seller_name", "?"), statsMsg,
                itemData != null ? itemData.optString("desc", GameApp.T("لا يوجد وصف", "No description")) : GameApp.T("لا يوجد وصف", "No description"), price);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(selected.optString("item_name", "?"));
        b.setView(U.msg(detailMsg));
        b.setPositiveButton(GameApp.T("شراء بـ", "Buy for") + price + GameApp.T("ذهب", "gold"), (d, w) -> {
            int reqLvl = itemData != null ? itemData.optInt("level", itemData.optInt("min_level", 1)) : 1;
            if (player.level < reqLvl) {
                U.alert(ctx, GameApp.T("مستوى غير كافٍ", "Insufficient Level"), String.format(Locale.US,
                        GameApp.T("لا يمكنك شراء [%s]!\nمستواك: %d\nالمستوى المطلوب: %d\n\nطور شخصيتك أولاً!",
                                "You cannot buy [%s]!\nYour level: %d\nRequired level: %d\n\nLevel up your character first!"),
                        selected.optString("item_name", "?"), player.level, reqLvl), GameApp.T("حسنا", "OK"), null);
                return;
            }
            if (player.gold >= price) {
                player.gold -= price;
                if (itemData != null) player.inventory.add(ItemData.fromJSON(itemData));
                final String sellerId = selected.optString("seller_id", "");
                new Thread(() -> {
                    Db.delete(MARKET_PATH + Db.encode(itemId));
                    JSONObject sData = Db.get(USER_PATH + Db.encode(sellerId));
                    if (sData != null) {
                        try {
                            long netPay = (long) Math.floor(price * 0.70);
                            sData.put("gold", sData.optLong("gold", 0) + netPay);
                            Db.put(USER_PATH + Db.encode(sellerId), sData);
                        } catch (Exception ignored) {}
                    }
                }).start();
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("تم الشراء بنجاح! تم إضافة العنصر لحقيبتك.", "Purchase successful! The item has been added to your bag."), GameApp.T("رائع", "Awesome"), null);
            } else {
                U.alert(ctx, null, GameApp.T("ذهبك غير كافٍ!", "Not enough gold!"), GameApp.T("حسنا", "OK"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void showMyListings() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("عروضي", "My Listings"), GameApp.T("جاري جلب عروضك...", "Fetching your listings..."), true);
        new Thread(() -> {
            JSONObject data = Db.get(MARKET_PATH.substring(0, MARKET_PATH.length() - 1));
            final List<String> ids = new ArrayList<>();
            final List<String> names = new ArrayList<>();
            final List<JSONObject> objs = new ArrayList<>();
            if (data != null) {
                Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    JSONObject item = data.optJSONObject(id);
                    if (item == null) continue;
                    if (item.optString("seller_id", "").equals(player.username)) {
                        ids.add(id);
                        objs.add(item);
                        names.add(item.optString("item_name", "?") + "|" + getItemRarity(item.optJSONObject("item_data")) + "|" + item.optLong("price", 0) + GameApp.T("ذهب", "gold"));
                    }
                }
            }
            final List<String> finalIds = ids;
            final List<JSONObject> finalObjs = objs;
            UI.post(() -> {
                progress.dismiss();
                if (finalIds.isEmpty()) {
                    U.alert(ctx, GameApp.T("عروضي", "My Listings"), GameApp.T("لا توجد عناصر معروضة منك حالياً.", "You currently have no items listed."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("عروضي في السوق", "My Market Listings"));
                b.setItems(names.toArray(new String[0]), (d, idx) -> {
                    JSONObject sel = finalObjs.get(idx);
                    JSONObject itemData = sel.optJSONObject("item_data");
                    String detail = String.format(Locale.US,
                            GameApp.T("الاسم: %s\nالندرة: %s\nالمستوى المطلوب: %d\nالفئة: %s\nالسعر: %d ذهب\n\nالوصف: %s",
                                    "Name: %s\nRarity: %s\nRequired Level: %d\nCategory: %s\nPrice: %d gold\n\nDescription: %s"),
                            sel.optString("item_name", "?"), getItemRarity(itemData), getItemLevel(itemData),
                            itemData != null ? itemData.optString("category", "عام") : "عام",
                            sel.optLong("price", 0),
                            itemData != null ? itemData.optString("desc", GameApp.T("لا يوجد وصف", "No description")) : GameApp.T("لا يوجد وصف", "No description"));
                    final JSONObject fd = itemData;
                    final String fId = finalIds.get(idx);
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setTitle(sel.optString("item_name", "?"));
                    b2.setView(U.msg(detail));
                    b2.setPositiveButton(GameApp.T("سحب العنصر", "Withdraw Item"), (d2, w2) -> {
                        new Thread(() -> {
                            Db.delete(MARKET_PATH + Db.encode(fId));
                            UI.post(() -> {
                                if (fd != null) player.inventory.add(ItemData.fromJSON(fd));
                                SaveSystem.saveAndRefresh();
                                U.alert(ctx, null, GameApp.T("تم سحب العنصر وإعادته لحقيبتك. لن يتم استرداد رسوم العرض.", "Item withdrawn and returned to your bag. The listing fee will not be refunded."), GameApp.T("حسنا", "OK"), null);
                            });
                        }).start();
                    });
                    b2.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                    b2.show();
                });
                b.setNegativeButton(GameApp.T("رجوع", "Back"), (d, w) -> showMarketItems(0, 9999));
                b.show();
            });
        }).start();
    }

    private static void sellItemUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final List<String> invNames = new ArrayList<>();
        final List<ItemData> invItems = new ArrayList<>();
        for (ItemData v : player.inventory) {
            if (!player.equipped.contains(v.name)) {
                invNames.add(v.name + "|" + v.category + "|" + v.rarity + GameApp.T("| مستوى", "| Level") + v.level);
                invItems.add(v);
            }
        }
        if (invNames.isEmpty()) {
            U.alert(ctx, null, GameApp.T("لا تملك عناصر غير مجهزة للبيع!", "You have no unequipped items to sell!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("اختر عنصراً للبيع", "Choose an item to sell"));
        b.setItems(invNames.toArray(new String[0]), (d, idx2) -> {
            final ItemData sel = invItems.get(idx2);
            final EditText ed = new EditText(ctx);
            ed.setInputType(InputType.TYPE_CLASS_NUMBER);
            ed.setHint(GameApp.T("حدد السعر بالذهب...", "Set the price in gold..."));
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(sel.name + "(" + sel.rarity + GameApp.T("| مستوى", "| Level") + sel.level + ")");
            LinearLayout lay = U.linear(ctx, true);
            lay.addView(U.msg(GameApp.T("الفئة:", "Category:") + sel.category + "\n" + GameApp.T("الندرة:", "Rarity:") + sel.rarity + "\n" + GameApp.T("المستوى:", "Level:") + sel.level
                    + GameApp.T("\n\nسيتم خصم 25% من السعر كرسوم عرض عند النشر.\nستحصل على 70% من السعر عند البيع.", "\n\n25% of the price will be deducted as a listing fee when posted.\nYou will receive 70% of the price when sold.")));
            lay.addView(ed);
            b2.setView(lay);
            b2.setPositiveButton(GameApp.T("عرض في السوق", "List in Market"), (d3, w3) -> {
                long price;
                try {
                    price = Long.parseLong(ed.getText().toString().trim());
                } catch (Exception e) {
                    price = 0;
                }
                if (price <= 0) {
                    U.alert(ctx, null, GameApp.T("سعر غير صالح!", "Invalid price!"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final long fee = (long) Math.floor(price * 0.25);
                final long fPrice = price;
                if (player.gold < fee) {
                    U.alert(ctx, null, GameApp.T("لا تملك ذهباً كافياً لدفع رسوم العرض (", "You do not have enough gold to pay the listing fee (") + fee + GameApp.T("ذهب)!", "gold)!"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                AlertDialog.Builder b3 = new AlertDialog.Builder(ctx);
                b3.setTitle(GameApp.T("تأكيد العرض", "Confirm Listing"));
                b3.setView(U.msg(GameApp.T("سعر البيع:", "Sale Price:") + fPrice + GameApp.T("ذهب\nرسوم العرض (25%):", "gold\nListing Fee (25%):") + fee + GameApp.T("ذهب\nستحصل عند البيع على:", "gold\nYou will receive when sold:") + (long) Math.floor(fPrice * 0.70) + GameApp.T("ذهب (70%)", "gold (70%)")));
                b3.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (d4, w4) -> {
                    player.gold -= fee;
                    JSONObject marketItem = new JSONObject();
                    try {
                        marketItem.put("seller_id", player.username);
                        marketItem.put("seller_name", player.name);
                        marketItem.put("item_name", sel.name);
                        marketItem.put("item_data", sel.toJSON());
                        marketItem.put("price", fPrice);
                        marketItem.put("time", System.currentTimeMillis() / 1000);
                    } catch (Exception ignored) {}
                    final String key = "item_" + System.currentTimeMillis() + "_" + NumberUtil.rand(1000, 9999);
                    final JSONObject mi = marketItem;
                    com.lli.com.core.Db.serverLog(GameApp.T("عرض للبيع في السوق", "Listed in market") + ": " + sel.name + " (" + fPrice + ")");
                    new Thread(() -> {
                        Db.put(MARKET_PATH + key, mi);
                        UI.post(() -> {
                            player.inventory.remove(sel);
                            SaveSystem.saveAndRefresh();
                            U.alert(ctx, null, GameApp.T("تم عرض العنصر في السوق بنجاح!\nتم خصم", "Item listed in the market successfully!\nA fee of") + fee + GameApp.T("ذهب كرسوم عرض.\nستحصل على 70% من السعر عند البيع.", "gold was charged as the listing fee.\nYou will receive 70% of the price when sold."), GameApp.T("حسنا", "OK"), null);
                        });
                    }).start();
                });
                b3.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b3.show();
            });
            b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b2.show();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }
}
