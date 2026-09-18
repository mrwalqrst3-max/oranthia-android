package com.lli.com.core;

import org.json.JSONException;
import org.json.JSONObject;

public class TempStats {
    public double str = 0;
    public double endurance = 0;
    public double agi = 0;
    public double luck = 0;
    public double hp = 0; // temporary max-HP bonus (healing potions)
    public int battlesLeft = 0;
    public long until = 0; // epoch millis expiry for time-based potions; 0 = none

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("str", str);
            o.put("endurance", endurance);
            o.put("agi", agi);
            o.put("luck", luck);
            o.put("hp", hp);
            o.put("battles_left", battlesLeft);
            if (until != 0) o.put("until", until);
        } catch (JSONException ignored) {}
        return o;
    }

    public static TempStats fromJSON(JSONObject o) {
        TempStats t = new TempStats();
        if (o == null) return t;
        t.str = o.optDouble("str", 0);
        t.endurance = o.optDouble("endurance", 0);
        t.agi = o.optDouble("agi", 0);
        t.luck = o.optDouble("luck", 0);
        t.hp = o.optDouble("hp", 0);
        t.battlesLeft = o.optInt("battles_left", 0);
        t.until = o.optLong("until", 0);
        return t;
    }

    public boolean isBattlesType() {
        return until <= 0 && battlesLeft > 0;
    }

    public boolean isTimeType() {
        return until > 0;
    }

    /** True if any bonus is currently in effect. */
    public boolean active() {
        if (battlesLeft > 0) return true;
        if (until > 0) return System.currentTimeMillis() < until;
        return false;
    }

    /** Reset bonuses once a time-based potion expires. */
    public void expireIfPast() {
        if (until > 0 && System.currentTimeMillis() >= until) {
            resetBonuses();
        }
    }

    public void resetBonuses() {
        str = 0;
        endurance = 0;
        agi = 0;
        luck = 0;
        hp = 0;
        battlesLeft = 0;
        until = 0;
    }

    /** Remaining seconds for time-based potions, 0 if not active. */
    public long remainingSeconds() {
        if (until <= 0) return 0;
        long r = (until - System.currentTimeMillis()) / 1000;
        return r > 0 ? r : 0;
    }
}
