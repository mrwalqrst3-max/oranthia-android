package com.lli.com.core;

import org.json.JSONException;
import org.json.JSONObject;

public class ItemData {
    public String name = "";
    public String rarity = "";
    public String category = "أخرى";
    public String subType = "";
    public String desc = "";
    public String effect = "";
    public int level = 1;
    public long price = 0;
    public Boosts boosts = new Boosts();
    public Stats stats = null;
    public int upgradeLvl = 0;
    public boolean isScroll = false;
    public boolean isRoyal = false;
    public boolean isPotion = false;
    public boolean isRaceItem = false;
    public double speedBonus = 0;
    public double atkMultiplier = 0;
    public double appliedAtkBonus = 0;
    public int count = 1;
    public String potionType = "";
    public double potionVal = 0;
    public long potionDurMin = 0;
    public int potionDurBattles = 0;

    public double power() {
        double p = 0;
        if (boosts != null) {
            p += boosts.str + boosts.end + boosts.hp / 2 + boosts.agi + boosts.lck;
        }
        if (stats != null) {
            p += stats.strength + stats.endurance + stats.maxHp / 2;
        }
        p += upgradeLvl * 50;
        if (atkMultiplier > 0) p += atkMultiplier * 100;
        if (speedBonus > 0) p += speedBonus * 100;
        return p;
    }

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            if (!rarity.isEmpty()) o.put("rarity", rarity);
            o.put("category", category);
            if (!subType.isEmpty()) o.put("sub_type", subType);
            if (!desc.isEmpty()) o.put("desc", desc);
            if (!effect.isEmpty()) o.put("effect", effect);
            o.put("level", level);
            o.put("price", price);
            o.put("boosts", boosts.toJSON());
            if (stats != null) o.put("stats", stats.toJSON());
            if (upgradeLvl != 0) o.put("upgrade_lvl", upgradeLvl);
            if (isScroll) o.put("is_scroll", true);
            if (isRoyal) o.put("is_royal", true);
            if (isPotion) o.put("is_potion", true);
            if (isRaceItem) o.put("is_race_item", true);
            if (speedBonus != 0) o.put("speed_bonus", speedBonus);
            if (atkMultiplier != 0) o.put("atk_multiplier", atkMultiplier);
            if (appliedAtkBonus != 0) o.put("applied_atk_bonus", appliedAtkBonus);
            if (count != 1) o.put("count", count);
            if (isPotion && !potionType.isEmpty()) o.put("type", potionType);
            if (isPotion && potionVal != 0) o.put("val", potionVal);
            if (isPotion && potionDurMin != 0) o.put("duration_min", potionDurMin);
            if (isPotion && potionDurBattles != 0) o.put("duration_battles", potionDurBattles);
        } catch (JSONException ignored) {}
        return o;
    }

    public static ItemData fromJSON(JSONObject o) {
        ItemData it = new ItemData();
        if (o == null) return it;
        it.name = o.optString("name", "");
        it.rarity = o.optString("rarity", "");
        it.category = o.optString("category", "أخرى");
        it.subType = o.optString("sub_type", "");
        it.desc = o.optString("desc", "");
        if (it.desc != null) it.desc = it.desc.replaceAll("(\\d+)\\.0(?=\\D|$)", "$1");
        it.effect = o.optString("effect", "");
        it.level = o.optInt("level", 1);
        it.price = o.optLong("price", 0);
        it.boosts = Boosts.fromJSON(o.optJSONObject("boosts"));
        JSONObject st = o.optJSONObject("stats");
        if (st != null) it.stats = Stats.fromJSON(st);
        it.upgradeLvl = o.optInt("upgrade_lvl", 0);
        it.isScroll = o.optBoolean("is_scroll", false);
        it.isRoyal = o.optBoolean("is_royal", false);
        it.isPotion = o.optBoolean("is_potion", false);
        it.isRaceItem = o.optBoolean("is_race_item", false);
        it.speedBonus = o.optDouble("speed_bonus", 0);
        it.atkMultiplier = o.optDouble("atk_multiplier", 0);
        it.appliedAtkBonus = o.optDouble("applied_atk_bonus", 0);
        it.count = o.optInt("count", 1);
        it.potionType = o.optString("type", "");
        it.potionVal = o.optDouble("val", 0);
        it.potionDurMin = o.optLong("duration_min", 0);
        it.potionDurBattles = o.optInt("duration_battles", 0);
        return it;
    }
}
