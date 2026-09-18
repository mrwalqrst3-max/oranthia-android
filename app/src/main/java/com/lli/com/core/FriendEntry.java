package com.lli.com.core;

import org.json.JSONObject;

public class FriendEntry {
    public String username = "";
    public String name = "";

    public FriendEntry() {}

    public FriendEntry(String username, String name) {
        this.username = username;
        this.name = name;
    }

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("username", username);
            o.put("name", name);
        } catch (Exception ignored) {}
        return o;
    }

    public static FriendEntry fromJSON(JSONObject o) {
        FriendEntry f = new FriendEntry();
        if (o == null) return f;
        f.username = o.optString("username", "");
        f.name = o.optString("name", "");
        return f;
    }
}
