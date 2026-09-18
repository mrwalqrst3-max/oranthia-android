package com.lli.com.core;

import com.lli.com.GameApp;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Db {
    // رابط خادمك الأساسي (Render) — عالم أترايثيا
    public static final String DB_URL = "https://oranthia-backend.onrender.com";
    private static final String CLANS_PATH = "tribes_system/";
    private static final String SHOPS_PATH = "shops/";
    private static final String PVP_PATH = "pvp_requests/";
    private static final String BOSS_PATH = "world_boss/";
    private static final String WELCOME_PATH = "welcome_messages/";
    private static final String ANNOUNCEMENTS_PATH = "announcements/";
    private static final String ADMIN_PATH = "admin_data/";
    private static final String USER_PATH = "players/";

    public static String playerPath() {
        return USER_PATH + encode(GameApp.player.username) + "/";
    }

    public static String encode(String s) {
        if (s == null) return "";
        return s.replace(".", "%2E").replace("#", "%23").replace("$", "%24")
                .replace("[", "%5B").replace("]", "%5D").replace("/", "%2F");
    }

    private static String buildUrl(String path, String query) {
        String u = DB_URL + "/" + path;
        if (query != null && !query.isEmpty()) {
            try {
                u += ".json?" + URLEncoder.encode(query, "UTF-8").replace("%3D", "=").replace("%26", "&");
            } catch (Exception e) {
                u += ".json?" + query;
            }
        } else {
            u += ".json";
        }
        return u;
    }

    private static String http(String method, String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String out = "";
        if (is != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            out = sb.toString();
        }
        conn.disconnect();
        return out;
    }

    public static JSONObject get(String path) {
        try {
            String body = http("GET", buildUrl(path, null), null);
            return parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject get(String path, String query) {
        try {
            String body = http("GET", buildUrl(path, query), null);
            return parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject put(String path, JSONObject data) {
        try {
            String body = http("PUT", buildUrl(path, null), data == null ? "{}" : data.toString());
            return parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject patch(String path, JSONObject data) {
        try {
            String body = http("PATCH", buildUrl(path, null), data == null ? "{}" : data.toString());
            return parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject delete(String path) {
        try {
            String body = http("DELETE", buildUrl(path, null), null);
            return parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    public static String myIp() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(DB_URL + "/myip").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            conn.disconnect();
            JSONObject o = new JSONObject(sb.toString());
            return o.optString("ip", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean deviceBanned() {
        try {
            String ip = myIp();
            if (ip.isEmpty()) return false;
            return get("device_bans/" + encode(ip)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // Returns the full device-ban record (with reason) for the current device,
    // or null if not banned.
    public static JSONObject deviceBanRecord() {
        try {
            String ip = myIp();
            if (ip.isEmpty()) return null;
            return get("device_bans/" + encode(ip));
        } catch (Exception e) {
            return null;
        }
    }

    // Keep-alive: prevents Render free-tier from sleeping.
    public static boolean ping() {
        try {
            String body = http("GET", DB_URL + "/ping", null);
            return body != null && body.contains("\"ok\"");
        } catch (Exception e) {
            return false;
        }
    }

    // Admin command queue (client side).

    public static JSONObject getCommands(String playerId) {
        return get("admin_commands/" + encode(playerId));
    }

    public static JSONObject getCommandsShallow(String playerId) {
        return get("admin_commands/" + encode(playerId), "shallow=true");
    }

    public static void acknowledgeCommand(String playerId, String seq) {
        delete("admin_commands/" + encode(playerId) + "/" + encode(seq));
    }

    public static boolean enqueueCommand(JSONObject cmd) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(DB_URL + "/admin_commands").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("x-admin-token", com.lli.com.GameApp.ADMIN_TOKEN);
            try (OutputStream os = c.getOutputStream()) {
                os.write(cmd.toString().getBytes("UTF-8"));
            }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (is != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                while (r.readLine() != null) { /* drain */ }
            }
            c.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) {
                try { c.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    public static org.json.JSONArray getServerLogs() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(DB_URL + "/server_logs.json").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            c.disconnect();
            String body = sb.toString();
            if (body == null || body.isEmpty() || body.equals("null")) return null;
            return new org.json.JSONArray(body);
        } catch (Exception e) {
            return null;
        }
    }

    // Logs a named game event to the server (visible to admins in the server logs).
    public static void serverLog(final String event) {
        try {
            final JSONObject o = new JSONObject();
            o.put("user", (GameApp.player != null && GameApp.player.username != null) ? GameApp.player.username : "");
            o.put("event", event == null ? "" : event);
            new Thread(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(DB_URL + "/server_logs/event").openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(o.toString().getBytes("UTF-8"));
                    }
                    c.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (c != null) {
                        try { c.disconnect(); } catch (Exception ignored) {}
                    }
                }
            }).start();
        } catch (Exception ignored) {}
    }

    private static JSONObject parse(String body) {
        if (body == null || body.isEmpty() || body.equals("null")) return null;
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
    }
}
