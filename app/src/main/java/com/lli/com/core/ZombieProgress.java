package com.lli.com.core;

import org.json.JSONException;
import org.json.JSONObject;

public class ZombieProgress {
    public int x = 0;
    public int y = 0;
    public int kills = 0;
    public double hp = 300;

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            JSONObject pos = new JSONObject();
            pos.put("x", x);
            pos.put("y", y);
            o.put("pos", pos);
            o.put("kills", kills);
            o.put("hp", hp);
        } catch (JSONException ignored) {}
        return o;
    }

    public static ZombieProgress fromJSON(JSONObject o) {
        ZombieProgress z = new ZombieProgress();
        if (o == null) return z;
        JSONObject pos = o.optJSONObject("pos");
        if (pos != null) {
            z.x = pos.optInt("x", 0);
            z.y = pos.optInt("y", 0);
        }
        z.kills = o.optInt("kills", 0);
        z.hp = o.optDouble("hp", 300);
        return z;
    }
}
