package com.lli.com.core;

import org.json.JSONException;
import org.json.JSONObject;

public class Quest {
    public boolean active = false;
    public String type = "";
    public long target = 0;
    public long current = 0;
    public String rewardType = "";
    public long rewardVal = 0;
    public String title = "";
    public org.json.JSONObject rewardObj = null;

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("active", active);
            o.put("type", type);
            o.put("target", target);
            o.put("current", current);
            o.put("reward_type", rewardType);
            o.put("reward_val", rewardVal);
            o.put("title", title);
            if (rewardObj != null) o.put("reward_obj", rewardObj);
        } catch (JSONException ignored) {}
        return o;
    }

    public static Quest fromJSON(JSONObject o) {
        Quest q = new Quest();
        if (o == null) return q;
        q.active = o.optBoolean("active", false);
        q.type = o.optString("type", "");
        q.target = o.optLong("target", 0);
        q.current = o.optLong("current", 0);
        q.rewardType = o.optString("reward_type", "");
        q.rewardVal = o.optLong("reward_val", 0);
        q.title = o.optString("title", "");
        q.rewardObj = o.optJSONObject("reward_obj");
        return q;
    }
}
