package com.lli.com.core;

import org.json.JSONException;
import org.json.JSONObject;

public class ChestData {
    public String type = "عادي";
    public int req = 10;
    public int done = 0;
    public int minLvl = 1;

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type);
            o.put("req", req);
            o.put("done", done);
            o.put("min_lvl", minLvl);
        } catch (JSONException ignored) {}
        return o;
    }

    public static ChestData fromJSON(JSONObject o) {
        ChestData c = new ChestData();
        if (o == null) return c;
        c.type = o.optString("type", "عادي");
        c.req = o.optInt("req", 10);
        c.done = o.optInt("done", 0);
        c.minLvl = o.optInt("min_lvl", 1);
        return c;
    }
}
