package com.lli.com.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;

import com.lli.com.GameApp;

public class ArtView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean dark;
    private int phase = 0;
    private float[] sx, sy, sp;
    private int w, h;
    private String spokenHint;

    public ArtView(Context ctx, boolean dark, String label, String spokenHint, int heightDp) {
        super(ctx);
        this.dark = dark;
        this.spokenHint = spokenHint == null ? label : spokenHint;
        setContentDescription(label);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
        setWillNotDraw(false);
        final int hpx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp, getResources().getDisplayMetrics());
        setMinimumHeight(hpx);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wm = MeasureSpec.getSize(widthMeasureSpec);
        int hm = MeasureSpec.getSize(heightMeasureSpec);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int h = getSuggestedMinimumHeight();
        if (mode == MeasureSpec.EXACTLY) h = hm;
        else if (mode == MeasureSpec.AT_MOST) h = Math.min(h, hm);
        setMeasuredDimension(wm, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        w = getWidth();
        h = getHeight();
        if (w <= 0 || h <= 0) return;
        phase++;
        if (sx == null) initParticles();
        int top, bottom;
        if (dark) {
            top = 0xFF0B0B2E;
            bottom = 0xFF2C1E4A;
        } else {
            top = 0xFF1E3C72;
            bottom = 0xFF2A5298;
            if ((phase / 60) % 2 == 0) {
                top = 0xFF4FA3E3;
                bottom = 0xFF7FC7F7;
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        if (dark) {
            paint.setColor(0xFFFFFFFF);
            for (int i = 0; i < sx.length; i++) {
                float a = (float) (0.3 + 0.7 * Math.abs(Math.sin(sp[i])));
                paint.setAlpha((int) (a * 255));
                c.drawCircle(sx[i], sy[i], 2.2f, paint);
                sp[i] += 0.09f;
            }
            paint.setAlpha(255);
            paint.setColor(0xFFE8E8C0);
            c.drawCircle((int) (w * 0.82f), (int) (h * 0.16f), Math.min(w, h) / 9f, paint);
            paint.setColor(0xFF20203A);
            c.drawCircle((int) (w * 0.82f), (int) (h * 0.16f), Math.min(w, h) / 9f - 4, paint);
        } else {
            int sunX = (int) (w * (0.78f + 0.02f * (float) Math.sin(phase / 40.0)));
            paint.setColor(0xFFFFE28A);
            c.drawCircle(sunX, (int) (h * 0.16f), Math.min(w, h) / 9f, paint);
            paint.setColor(0xFFFFFFFF);
            for (int i = 0; i < sx.length; i++) {
                float a = (float) (0.2 + 0.5 * Math.abs(Math.sin(sp[i])));
                paint.setAlpha((int) (a * 255));
                c.drawCircle(sx[i], sy[i], 1.6f, paint);
                sp[i] += 0.05f;
            }
            paint.setAlpha(255);
        }

        float groundY = h * 0.78f;
        paint.setColor(dark ? 0xFF1A1A2E : 0xFF3E7C3E);
        paint.setStyle(Paint.Style.FILL);
        Path gp = new Path();
        gp.moveTo(0, groundY);
        gp.lineTo(w, groundY);
        gp.lineTo(w, h);
        gp.lineTo(0, h);
        gp.close();
        c.drawPath(gp, paint);

        paint.setColor(dark ? 0xFF15152A : 0xFF2E5F2E);
        Path mp1 = new Path();
        mp1.moveTo(0, groundY);
        mp1.lineTo(w * 0.2f, groundY * 0.62f);
        mp1.lineTo(w * 0.42f, groundY);
        mp1.close();
        c.drawPath(mp1, paint);
        paint.setColor(dark ? 0xFF181836 : 0xFF356B35);
        Path mp2 = new Path();
        mp2.moveTo(w * 0.32f, groundY);
        mp2.lineTo(w * 0.55f, groundY * 0.5f);
        mp2.lineTo(w * 0.8f, groundY);
        mp2.close();
        c.drawPath(mp2, paint);

        paint.setColor(dark ? 0xFF09091C : 0xFF5A3A3A);
        float cw = w * 0.1f;
        float ch = groundY * 0.72f;
        float cx = w * 0.7f;
        c.drawRect(cx - cw / 2, groundY - ch, cx + cw / 2, groundY, paint);
        c.drawRect(cx - cw / 2 - 6, groundY - ch - 4, cx + cw / 2 + 6, groundY - ch - 22, paint);
        paint.setColor(0xFF8A8A4A);
        for (int i = 0; i < 3; i++) {
            c.drawRect(cx - cw / 2 + 4 + i * (cw - 10) / 3, groundY - ch + 8, cx - cw / 2 + 4 + i * (cw - 10) / 3 + (cw - 10) / 4, groundY - ch + 20, paint);
        }
        paint.setColor(0xFFB0B0B0);
        c.drawRect(cx - cw / 2 + (cw / 4), groundY - ch + 30, cx - cw / 2 + (cw / 4) + 10, groundY - ch + 62, paint);

        float heroX = w * (0.15f + 0.7f * (float) (0.5 + 0.5 * Math.sin(phase / 35.0)));
        float bounce = (float) (Math.abs(Math.sin(phase / 8.0)) * 4.0);
        float hy = groundY - 14 - bounce;
        paint.setColor(dark ? 0xFFE0A030 : 0xFFFFD54F);
        c.drawCircle(heroX, hy - 8, 7, paint);
        paint.setColor(dark ? 0xFFC0C0FF : 0xFF4A90D9);
        c.drawCircle(heroX - 12, hy + 6, 4, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(heroX - 13, hy + 5, 1.6f, paint);
        paint.setColor(dark ? 0xFFA03030 : 0xFFB04150);
        c.drawRect(heroX - 6, hy - 1, heroX + 7, hy + 3, paint);
        c.drawRect(heroX - 2, hy - 3, heroX, hy - 1, paint);
        paint.setColor(0xFF90EE90);
        float ex = heroX;
        c.drawCircle(ex + 2, hy - 16, 2 + (float) (Math.sin(phase / 15.0) + 1), paint);
        paint.setAlpha(255);

        if (sx == null) announceWhenShown(spokenHint);
        postInvalidateDelayed(60);
    }

    private void announceWhenShown(final String text) {
        postDelayed(() -> {
            try {
                announceForAccessibility(text);
            } catch (Exception ignored) {}
        }, 1200);
    }

    private void initParticles() {
        int n = dark ? 26 : 14;
        sx = new float[n];
        sy = new float[n];
        sp = new float[n];
        for (int i = 0; i < n; i++) {
            sx[i] = (float) Math.random() * w;
            sy[i] = (float) Math.random() * h * 0.6f;
            sp[i] = (float) Math.random() * 6.28f;
        }
    }
}