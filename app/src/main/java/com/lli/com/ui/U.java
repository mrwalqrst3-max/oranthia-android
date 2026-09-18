package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;

public class U {
    public static final int BLACK = 0xFF000000;
    public static final int GOLD = Color.YELLOW;
    public static final int WHITE = Color.WHITE;
    public static final int GRAY = Color.GRAY;
    public static final int LIGHT_GREEN = 0xFF90EE90;
    public static final int LIGHT_BLUE = 0xFFADD8E6;
    public static final int ORANGE = 0xFFFFA500;
    public static final int RED = 0xFFFF4444;

    /** True only when the main activity is alive and its window has a valid token
     *  (safe to show windowed dialogs). Prevents BadTokenException crashes. */
    public static boolean uiReady() {
        try {
            com.lli.com.MainActivity a = com.lli.com.MainActivity.inst;
            if (a == null || a.isFinishing()) return false;
            android.view.Window w = a.getWindow();
            if (w == null) return false;
            android.view.View decor = w.getDecorView();
            return decor != null && decor.getWindowToken() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static LinearLayout linear(Context ctx, boolean vertical) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        l.setBackgroundColor(BLACK);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp1(int h) {
        return new LinearLayout.LayoutParams(0, h, 1);
    }

    public static LinearLayout.LayoutParams lpMargins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    public static TextView text(Context ctx, String s, float size, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s == null ? "" : s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static TextView label(Context ctx, String s, float size, int color) {
        return text(ctx, s, size, color, false);
    }

    public static Button btn(Context ctx, String s) {
        Button b = new Button(ctx);
        b.setText(s == null ? "" : s);
        b.setTextSize(16);
        b.setTextColor(WHITE);
        b.setAllCaps(false);
        return b;
    }

    public static Button btn(Context ctx, String s, View.OnClickListener onClick) {
        Button b = btn(ctx, s);
        b.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            if (onClick != null) onClick.onClick(v);
        });
        return b;
    }

    public static EditText edit(Context ctx, String hint) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setHintTextColor(GRAY);
        e.setTextColor(WHITE);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > before) GameApp.sound.playSnd("type.mp3");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        return e;
    }

    public static ScrollView scroll(Context ctx, View content) {
        ScrollView sv = new ScrollView(ctx);
        sv.setBackgroundColor(BLACK);
        sv.addView(content);
        return sv;
    }

    /** Dialog message view with comfortable line spacing and padding so long
     *  feature texts are no longer glued together. Very long texts scroll. */
    public static View msg(String s) {
        TextView t = new TextView(GameApp.ctx);
        t.setText(s == null ? "" : s);
        t.setTextSize(13);
        t.setTextColor(WHITE);
        t.setLineSpacing(2, 1.3f);
        t.setPadding(18, 14, 18, 14);
        t.setGravity(Gravity.START);
        if (s != null && s.length() > 600) {
            ScrollView sc = new ScrollView(GameApp.ctx);
            sc.addView(t);
            sc.setFillViewport(true);
            return sc;
        }
        return t;
    }

    public static void alert(Context ctx, String title, String msg, String okText, View.OnClickListener ok) {
        if (!uiReady()) return;
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            if (title != null) b.setTitle(title);
            if (msg != null) b.setView(msg(msg));
            b.setPositiveButton(okText == null ? GameApp.T("حسناً", "OK") : okText, (d, w) -> {
                GameApp.sound.playSnd("click.mp3");
                if (ok != null) ok.onClick(null);
            });
            b.setNegativeButton(GameApp.T("إغلاق", "Close"), (d, w) -> d.dismiss());
            b.show();
        } catch (Exception ignored) {}
    }

    public static void toast(String s) {
        String msg = s == null ? "" : s;
        try {
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                try {
                    android.widget.Toast.makeText(GameApp.ctx, msg, android.widget.Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    /** Thread-safe ProgressDialog.show(). ProgressDialog/create handlers must run on the
     *  main looper; off-main-thread callers post to the main thread and wait for it, so
     *  dismiss() works afterwards without "Can't create handler inside thread" crashes. */
    public static ProgressDialog progress(Context ctx, CharSequence title, CharSequence msg, boolean indeterminate) {
        final Context c = (ctx == null) ? GameApp.ctx : ctx;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return ProgressDialog.show(c, title, msg, indeterminate);
            } catch (Exception e) {
                return null;
            }
        }
        final ProgressDialog[] holder = new ProgressDialog[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        try {
            new android.os.Handler(Looper.getMainLooper()).post(() -> {
                try {
                    holder[0] = ProgressDialog.show(c, title, msg, indeterminate);
                } catch (Exception ignored) {}
                latch.countDown();
            });
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return holder[0];
    }
}
