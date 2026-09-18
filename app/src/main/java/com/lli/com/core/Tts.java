package com.lli.com.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Tts {
    private Context appContext;
    private TextToSpeech tts;
    private boolean ready = false;

    public Tts(Context ctx) {
        appContext = ctx.getApplicationContext();
        init();
    }

    private void init() {
        if (appContext == null) return;
        try {
            String engine = com.lli.com.GameApp.ttsEngine;
            android.speech.tts.TextToSpeech.OnInitListener listener = status -> {
                if (status == TextToSpeech.SUCCESS) {
                    setupLanguage();
                    ready = true;
                } else {
                    ready = false;
                }
            };
            if (engine != null && !engine.isEmpty()) {
                tts = new TextToSpeech(appContext, listener, engine);
            } else {
                tts = new TextToSpeech(appContext, listener);
            }
        } catch (Exception ignored) {
            ready = false;
        }
    }

    public List<TextToSpeech.EngineInfo> getEngines() {
        List<TextToSpeech.EngineInfo> out = new ArrayList<>();
        try {
            if (tts != null) {
                List<TextToSpeech.EngineInfo> listed = tts.getEngines();
                if (listed != null) out.addAll(listed);
            }
        } catch (Exception ignored) {}
        Set<String> seen = new HashSet<>();
        for (TextToSpeech.EngineInfo ei : out) {
            if (ei.name != null) seen.add(ei.name);
        }
        try {
            Intent intent = new Intent("android.intent.action.TTS_SERVICE");
            List<ResolveInfo> services = appContext.getPackageManager().queryIntentServices(intent, 0);
            if (services != null) {
                for (ResolveInfo ri : services) {
                    if (ri.serviceInfo == null) continue;
                    String pkg = ri.serviceInfo.packageName;
                    if (!seen.add(pkg)) continue;
                    TextToSpeech.EngineInfo ei = new TextToSpeech.EngineInfo();
                    ei.name = pkg;
                    ei.label = ri.serviceInfo.loadLabel(appContext.getPackageManager()).toString();
                    ei.icon = ri.serviceInfo.applicationInfo != null ? ri.serviceInfo.applicationInfo.icon : 0;
                    out.add(ei);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public synchronized void setEngine(final String engine) {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {}
        tts = null;
        ready = false;
        com.lli.com.GameApp.ttsEngine = engine == null ? "" : engine;
        com.lli.com.GameApp.prefs.edit().putString("tts_engine", com.lli.com.GameApp.ttsEngine).apply();
        init();
    }

    private void setupLanguage() {
        try {
            int r = tts.setLanguage(new Locale("ar"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(Locale.getDefault());
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }
            }
        } catch (Exception ignored) {}
    }

    public synchronized void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!com.lli.com.GameApp.ttsEnabled) return;
        try {
            if (tts == null) {
                init();
                if (tts == null) return;
            }
            if (!ready) return;
            float rate = com.lli.com.GameApp.ttsRate;
            if (rate < 0.5f) rate = 0.5f;
            if (rate > 2.0f) rate = 2.0f;
            tts.setSpeechRate(rate);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1");
        } catch (Exception ignored) {}
    }

    public synchronized void stop() {
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
    }

    public synchronized void shutdown() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
            ready = false;
        }
    }
}
