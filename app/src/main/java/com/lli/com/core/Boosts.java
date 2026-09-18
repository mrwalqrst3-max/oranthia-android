package com.lli.com.core;

import org.json.JSONException;
import org.json.JSONObject;

public class Boosts {
    public double str = 0;
    public double end = 0;
    public double hp = 0;
    public double agi = 0;
    public double lck = 0;

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            if (str != 0) o.put("str", str);
            if (end != 0) o.put("end", end);
            if (hp != 0) o.put("hp", hp);
            if (agi != 0) o.put("agi", agi);
            if (lck != 0) o.put("lck", lck);
        } catch (JSONException ignored) {}
        return o;
    }

    public static Boosts fromJSON(JSONObject o) {
        Boosts b = new Boosts();
        if (o == null) return b;
        b.str = o.optDouble("str", 0);
        b.end = o.optDouble("end", 0);
        b.hp = o.optDouble("hp", 0);
        b.agi = o.optDouble("agi", 0);
        b.lck = o.optDouble("lck", 0);
        return b;
    }
}
