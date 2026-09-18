package com.lli.com.core;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.ui.U;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BattleLog {

    public static final String SEP = "\u0001";

    public static void record(PlayerData p, String line) {
        if (p == null) p = GameApp.player;
        if (p.battleLog == null) p.battleLog = new java.util.ArrayList<>();
        String ts = new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date());
        p.battleLog.add(0, "[" + ts + "]" + line);
        while (p.battleLog.size() > 60) p.battleLog.remove(p.battleLog.size() - 1);
        SaveSystem.saveAndRefresh();
    }

    public static void record(PlayerData p, String ar, String en) {
        if (ar == null) ar = "";
        if (en == null) en = "";
        record(p, ar + SEP + en);
    }

    public static String display(String rawLine) {
        if (rawLine == null) return "";
        if (rawLine.contains(SEP)) {
            String ts = "";
            String body = rawLine;
            if (body.startsWith("[")) {
                int e = body.indexOf("]");
                if (e >= 0) {
                    ts = body.substring(0, e + 1);
                    body = body.substring(e + 1);
                }
            }
            String[] parts = body.split(SEP, -1);
            String chosen = parts.length > (GameApp.isEn ? 1 : 0) ? parts[GameApp.isEn ? 1 : 0] : parts[0];
            if (chosen == null || chosen.trim().isEmpty() && parts.length > 1) chosen = parts[GameApp.isEn ? 0 : 1];
            return ts + chosen;
        }
        return rawLine;
    }

    public static void openUI() {
        final Context ctx = GameApp.uiCtx();
        GameApp.sound.playSnd("log_open.mp3");
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        TextView head = U.text(ctx, GameApp.T("سجل المعارك", "Battle Log"), 18, U.GOLD, true);
        lay.addView(head);
        LinearLayout rows = U.linear(ctx, true);
        final ScrollView sc = U.scroll(ctx, rows);
        lay.addView(sc);
        final AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(GameApp.T("سجل آخر المعارك (الآخر 60)", "Latest Battles (last 60)"));
        builder.setView(lay);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.show();

        Button close = U.btn(ctx, GameApp.T("إغلاق", "Close"));
        close.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            dialog.dismiss();
        });
        lay.addView(close);

        PlayerData p = GameApp.player;
        if (p.battleLog == null || p.battleLog.isEmpty()) {
            TextView tv = U.text(ctx, GameApp.T("لا توجد معارك محفوظة بعد.\nاخض خوض المعارك وسيتم تسجيلها هنا!", "No battles recorded yet.\nFight battles and they will be recorded here!"), 14, U.WHITE, false);
            tv.setPadding(20, 20, 20, 20);
            rows.addView(tv);
        } else {
            int n = Math.min(p.battleLog.size(), 60);
            int shown = 0;
            String spoken = "";
            for (int i = 0; i < n; i++) {
                final String line = BattleLog.display(p.battleLog.get(i));
                if (spoken.isEmpty()) spoken = line;
                TextView tv = U.text(ctx, line, 13, U.WHITE, false);
                tv.setPadding(10, 8, 10, 8);
                rows.addView(tv);
                shown++;
            }
            GameApp.speakTts(GameApp.T("لديك", "You have") + shown + GameApp.T("معركة مسجلة. أحدثها:", "recorded battles. The latest:") + spoken.replace("[", "").replace("]", ""));
        }
    }
}