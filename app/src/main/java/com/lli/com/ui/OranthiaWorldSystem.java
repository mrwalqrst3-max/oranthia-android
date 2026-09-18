package com.lli.com.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.PlayerData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class OranthiaWorldSystem {
    private static final int GATE_LEVEL = 25;
    private static String currentMap = "main";
    private static String mapDataText = "";
    private static List<Box> platforms = new ArrayList<>();
    private static List<Box> zones = new ArrayList<>();
    private static List<Sign> signs = new ArrayList<>();
    private static float mapMaxX = 200, mapMaxY = 200;
    private static float myX = 2, myY = 2, myZ = 5;
    private static List<Online> online = new ArrayList<>();
    private static List<String[]> chat = new ArrayList<>();
    private static WorldMapView mapView;
    private static TextView onlineText;
    private static TextView chatBox;

    private static class Box {
        float x1, y1, x2, y2;
        String label;
        Box(float x1, float y1, float x2, float y2, String label) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.label = label;
        }
        boolean contains(float px, float py) {
            return px >= x1 - 0.5f && px <= x2 + 0.5f && py >= y1 - 0.5f && py <= y2 + 0.5f;
        }
    }

    private static class Sign {
        float x, y;
        String text;
        Sign(float x, float y, String text) { this.x = x; this.y = y; this.text = text; }
    }

    private static class Online {
        String user; float x, y;
        Online(String user, float x, float y) { this.user = user; this.x = x; this.y = y; }
    }

    private static String httpRaw(String method, String url, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
            }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            c.disconnect();
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) { try { c.disconnect(); } catch (Exception ignored) {} }
        }
    }

    private static JSONObject postWorld(String path, JSONObject body) {
        String out = httpRaw("POST", Db.DB_URL + "/world/" + path, body == null ? "{}" : body.toString());
        try { return out.isEmpty() ? null : new JSONObject(out); } catch (Exception e) { return null; }
    }

    private static JSONObject getWorld(String path) {
        String out = httpRaw("GET", Db.DB_URL + "/world/" + path, null);
        try { return out.isEmpty() ? null : new JSONObject(out); } catch (Exception e) { return null; }
    }

    private static void parseMapData(String text) {
        platforms.clear();
        zones.clear();
        signs.clear();
        mapMaxX = 200; mapMaxY = 200;
        if (text == null) return;
        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(":");
            if (parts.length < 2) continue;
            String key = parts[0].trim();
            String val = parts[1].trim();
            if (key.equals("maxx")) { try { mapMaxX = Float.parseFloat(val); } catch (Exception ignored) {} continue; }
            if (key.equals("maxy")) { try { mapMaxY = Float.parseFloat(val); } catch (Exception ignored) {} continue; }
            if (key.equals("platform") && parts.length >= 8) {
                try {
                    platforms.add(new Box(Float.parseFloat(parts[2]), Float.parseFloat(parts[4]), Float.parseFloat(parts[1]), Float.parseFloat(parts[3]), "pl"));
                } catch (Exception ignored) {}
            } else if (key.equals("staircase") && parts.length >= 8) {
                try {
                    platforms.add(new Box(Float.parseFloat(parts[2]) - 2, Float.parseFloat(parts[4]) - 2, Float.parseFloat(parts[1]) + 2, Float.parseFloat(parts[3]) + 2, "st"));
                } catch (Exception ignored) {}
            } else if (key.equals("zone") && parts.length >= 8) {
                String label = parts[7];
                try {
                    zones.add(new Box(Float.parseFloat(parts[2]), Float.parseFloat(parts[4]), Float.parseFloat(parts[1]), Float.parseFloat(parts[3]), label));
                } catch (Exception ignored) {}
            } else if (key.equals("sign") && parts.length >= 5) {
                try {
                    signs.add(new Sign(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), parts[4]));
                } catch (Exception ignored) {}
            }
        }
    }

    public static void openWorldUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;

        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(20, 20, 20, 20);

        TextView title = U.text(ctx, GameApp.T("بوابة أترايثيا — عالم الأساطير", "Oranthia Gate — Legendary World"), 18, Color.YELLOW, true);
        lay.addView(title);

        TextView gateInfo = U.label(ctx,
                GameApp.T("تفتح البوابة عند المستوى 25 فقط.\nمستواك الحالي:", "The gate opens only at level 25.\nYour current level:") + p.level,
                14, p.level >= GATE_LEVEL ? U.LIGHT_GREEN : U.RED);
        lay.addView(gateInfo);

        Button enterBtn = U.btn(ctx, GameApp.T("دخول عالم أترايثيا ⚔️", "Enter Oranthia World ⚔️"), v -> enterWorld(ctx, p));
        lay.addView(enterBtn, U.lpMargins(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 6, 0, 6));

        mapView = new WorldMapView(ctx);
        mapView.setLayoutParams(U.lp(LinearLayout.LayoutParams.MATCH_PARENT, 440));
        mapView.setBackgroundColor(Color.rgb(8, 10, 16));
        lay.addView(mapView);

        onlineText = U.text(ctx, "", 13, U.LIGHT_BLUE, false);
        lay.addView(onlineText);

        final TextView chatBox = U.text(ctx, GameApp.T("دردشة العالم", "World Chat"), 13, Color.WHITE, false);
        lay.addView(chatBox);
        OranthiaWorldSystem.chatBox = chatBox;

        final EditText msgInput = U.edit(ctx, GameApp.T("اكتب رسالة للعالم...", "Write a message to the world..."));
        lay.addView(msgInput);

        LinearLayout chatRow = U.linear(ctx, false);
        Button sendBtn = U.btn(ctx, GameApp.T("إرسال", "Send"), v -> {
            String msg = msgInput.getText().toString().trim();
            if (msg.isEmpty()) return;
            Thread th = new Thread(() -> {
                JSONObject body = new JSONObject();
                try {
                    body.put("username", p.username);
                    body.put("map", currentMap);
                    body.put("msg", msg);
                } catch (Exception ignored) {}
                JSONObject res = postWorld("message", body);
                UI.post(() -> {
                    msgInput.setText("");
                    refreshChat();
                });
            });
            th.start();
        });
        chatRow.addView(sendBtn, U.lp1(LinearLayout.LayoutParams.WRAP_CONTENT));
        Button refreshBtn = U.btn(ctx, GameApp.T("تحديث 🔄", "Refresh 🔄"), v -> refreshAll(ctx, p));
        chatRow.addView(refreshBtn, U.lp1(LinearLayout.LayoutParams.WRAP_CONTENT));
        lay.addView(chatRow);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("عالم أترايثيا", "Oranthia World"));
        b.setView(U.scroll(ctx, lay));
        b.setPositiveButton(GameApp.T("خروج", "Exit"), null);
        b.show();

        refreshAll(ctx, p);
    }

    private static void enterWorld(final Context ctx, final PlayerData p) {
        final android.app.ProgressDialog pd = U.progress(ctx, null, GameApp.T("جارِ فتح البوابة...", "Opening the gate..."), true);
        Thread th = new Thread(() -> {
            JSONObject body = new JSONObject();
            try {
                body.put("username", p.username);
                body.put("map", currentMap);
            } catch (Exception ignored) {}
            final JSONObject res = postWorld("enter", body);
            UI.post(() -> {
                try { if (pd != null) pd.dismiss(); } catch (Exception ignored) {}
                boolean locked = res != null && res.optBoolean("locked", false);
                if (res == null) {
                    U.alert(ctx, null, GameApp.T("تعذر الاتصال بخادم أترايثيا.", "Cannot reach the Oranthia server."), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                if (!res.optBoolean("success", false)) {
                    U.alert(ctx, null, res.optString("error", GameApp.T("فشل الدخول.", "Enter failed.")), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                mapDataText = res.optString("mapdata", "");
                parseMapData(mapDataText);
                myX = 2; myY = 2; myZ = 5;
                applyOnline(res.optJSONArray("online"));
                U.alert(ctx, null,
                        GameApp.T("فتحت بوابة أترايثيا! أنت الآن داخل العالم الأسطوري.", "You opened the Oranthia gate! You are now inside the legendary world."),
                        GameApp.T("حسناً", "OK"), null);
                mapView.invalidate();
                renderOnline();
            });
        });
        th.start();
    }

    private static void refreshAll(final Context ctx, final PlayerData p) {
        Thread th = new Thread(() -> {
            JSONObject chatRes = getWorld("chat/" + currentMap);
            JSONObject presRes = getWorld("presence/" + currentMap);
            UI.post(() -> {
                refreshChatFrom(chatRes);
                applyPresence(presRes);
                renderOnline();
                mapView.invalidate();
            });
        });
        th.start();
    }

    private static void refreshChat() {
        final JSONObject saved = getWorld("chat/" + currentMap);
        UI.post(() -> refreshChatFrom(saved));
    }

    private static void refreshChatFrom(JSONObject res) {
        chat.clear();
        if (res != null && res.optBoolean("success", false)) {
            JSONArray arr = res.optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    if (m == null) continue;
                    chat.add(new String[]{m.optString("user", ""), m.optString("text", "")});
                }
            }
        }
        String body = "";
        for (String[] m : chat) {
            body += "\n[" + m[0] + "] " + m[1];
        }
        if (chatBox != null) chatBox.setText(GameApp.T("دردشة العالم", "World Chat") + body);
    }

    private static void applyOnline(JSONArray arr) {
        online.clear();
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            online.add(new Online(o.optString("user", ""), (float) o.optDouble("x", 0), (float) o.optDouble("y", 0)));
            if (myX == 2 && myY == 2 && o.optString("user", "").equals(GameApp.player.username)) {
                myX = (float) o.optDouble("x", 2);
                myY = (float) o.optDouble("y", 2);
            }
        }
    }

    private static void applyPresence(JSONObject res) {
        if (res != null && res.optBoolean("success", false)) {
            applyOnline(res.optJSONArray("online"));
        }
    }

    private static void renderOnline() {
        if (onlineText == null) return;
        StringBuilder s = new StringBuilder(GameApp.T("الحاضرون في العالم (", "Online in the world (") + online.size() + "):\n");
        for (Online o : online) {
            s.append("• ").append(o.user).append(" (").append((int) o.x).append(",").append((int) o.y).append(")\n");
        }
        onlineText.setText(s.toString().trim());
    }

    private static void moveTo(final Context ctx, final PlayerData p, final float nx, final float ny) {
        boolean ok = false;
        for (Box b : platforms) {
            if (b.contains(nx, ny)) { ok = true; break; }
        }
        if (!ok) {
            U.toast(GameApp.T("لا يمكنك المشي هنا (خارج الأرض الصلبة)!", "You cannot walk here (outside solid ground)!"));
            return;
        }
        myX = nx; myY = ny;
        mapView.invalidate();
        Thread th = new Thread(() -> {
            JSONObject body = new JSONObject();
            try {
                body.put("username", p.username);
                body.put("map", currentMap);
                body.put("x", nx);
                body.put("y", ny);
                body.put("z", myZ);
            } catch (Exception ignored) {}
            final JSONObject res = postWorld("move", body);
            UI.post(() -> {
                if (res != null && res.optBoolean("success", false)) {
                    applyOnline(res.optJSONArray("online"));
                    renderOnline();
                    mapView.invalidate();
                }
            });
        });
        th.start();
    }

    private static class WorldMapView extends View {
        private final Paint fill = new Paint();
        private final Paint edge = new Paint();
        private final Paint zone = new Paint();
        private final Paint sign = new Paint();
        private final Paint me = new Paint();
        private final Paint other = new Paint();
        private final Paint text = new Paint();

        WorldMapView(Context c) {
            super(c);
            fill.setColor(Color.rgb(30, 34, 48));
            edge.setColor(Color.GRAY);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(2f);
            zone.setColor(0x33FFD700);
            zone.setStyle(Paint.Style.STROKE);
            zone.setStrokeWidth(2f);
            sign.setColor(Color.CYAN);
            me.setColor(Color.YELLOW);
            other.setColor(Color.rgb(80, 200, 255));
            text.setColor(Color.WHITE);
            text.setTextSize(28f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (mapMaxX <= 0 || mapMaxY <= 0 || w <= 0 || h <= 0) {
                canvas.drawText(GameApp.T("ادخل العالم أولاً.", "Enter the world first."), 30, 40, text);
                return;
            }
            float scale = Math.min(w / mapMaxX, h / mapMaxY);
            float ox = (w - mapMaxX * scale) / 2f;
            float oy = (h - mapMaxY * scale) / 2f;

            for (Sign s : signs) {
                canvas.drawCircle(ox + s.x * scale, oy + s.y * scale, 4f, sign);
            }
            for (Box z : zones) {
                RectF r = new RectF(ox + z.x1 * scale, oy + z.y1 * scale, ox + z.x2 * scale, oy + z.y2 * scale);
                canvas.drawRect(r, zone);
            }
            for (Box pl : platforms) {
                RectF r = new RectF(ox + pl.x1 * scale, oy + pl.y1 * scale, ox + pl.x2 * scale, oy + pl.y2 * scale);
                canvas.drawRect(r, fill);
                canvas.drawRect(r, edge);
            }
            canvas.drawText(GameApp.T("أنت", "You"), ox + myX * scale - 20, oy + myY * scale - 10, me);
            canvas.drawCircle(ox + myX * scale, oy + myY * scale, 8f, me);
            for (Online o : online) {
                if (o.user.equals(GameApp.player.username)) continue;
                canvas.drawCircle(ox + o.x * scale, oy + o.y * scale, 6f, other);
            }
            canvas.drawText(currentMap + " | " + (int) mapMaxX + "x" + (int) mapMaxY, 10, 28, text);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                float w = getWidth();
                float h = getHeight();
                float scale = Math.min(w / Math.max(mapMaxX, 1), h / Math.max(mapMaxY, 1));
                float ox = (w - mapMaxX * scale) / 2f;
                float oy = (h - mapMaxY * scale) / 2f;
                float tx = (ev.getX() - ox) / scale;
                float ty = (ev.getY() - oy) / scale;
                if (tx >= 0 && tx <= mapMaxX && ty >= 0 && ty <= mapMaxY) {
                    moveTo(GameApp.uiCtx(), GameApp.player, tx, ty);
                }
                return true;
            }
            return true;
        }
    }
}