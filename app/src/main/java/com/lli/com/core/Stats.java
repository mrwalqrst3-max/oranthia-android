package com.lli.com.core;

import org.json.JSONException;
import org.json.JSONObject;

public class Stats {
    public double strength = 20;
    public double endurance = 10;
    public double agility = 5;
    public double luck = 5;
    public double hp = 300;
    public double maxHp = 300;

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("strength", strength);
            o.put("endurance", endurance);
            o.put("agility", agility);
            o.put("luck", luck);
            o.put("hp", hp);
            o.put("max_hp", maxHp);
        } catch (JSONException ignored) {}
        return o;
    }

    public static Stats fromJSON(JSONObject o) {
        Stats s = new Stats();
        if (o == null) return s;
        s.strength = o.optDouble("strength", 20);
        s.endurance = o.optDouble("endurance", 10);
        s.agility = o.optDouble("agility", 5);
        s.luck = o.optDouble("luck", 5);
        s.hp = o.optDouble("hp", 300);
        s.maxHp = o.optDouble("max_hp", 300);
        return s;
    }
}
