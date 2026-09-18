package com.lli.com.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.lli.com.GameApp;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * VoiceChatController: the glue between P2P voice and the arena.
 *
 * One singleton per client. On {@link #start(String)} it connects to the
 * signaling server, discovers every player in the same arena that also has
 * voice enabled, and dials each of them -> a full mesh of WebRTC
 * PeerConnections. Audio streams peer-to-peer (STUN only, no media server).
 *
 * Semantics: once the mesh is up the client can HEAR all connected peers.
 * Pressing "Talk" toggles whether our microphone is actually sent (microphone
 * permission is requested by the arena UI before calling start()). Staying in
 * the arena keeps the mesh alive between talk sessions.
 *
 * Glare avoidance: for each peer pair the lexicographically SMALLER peer id is
 * the offerer, the larger one answers — deterministically, no double offers.
 */
public final class VoiceChatController {
    private static final String TAG = "VoiceCtrl";
    private static volatile VoiceChatController sInstance;

    private final Map<String, VoiceChatManager> managers = new ConcurrentHashMap<>();
    private final Object meshLock = new Object();
    private volatile PeerSignalingClient signal;
    private volatile String arenaId;
    private volatile String userId;
    private volatile boolean started = false;
    private volatile boolean talking = false;
    private volatile boolean dialing = false;

    // Keeps re-discovering peers while active so late joiners are dialed from
    // both sides (a single discovery pass at connect time misses peers that
    // join the arena afterwards — the main cause of "no sound").
    private final ScheduledExecutorService redialPool =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "voice-redial");
                t.setDaemon(true);
                return t;
            });
    private volatile ScheduledFuture<?> redialFuture;

    private VoiceChatController() {}

    public static VoiceChatController instance() {
        if (sInstance == null) {
            synchronized (VoiceChatController.class) {
                if (sInstance == null) sInstance = new VoiceChatController();
            }
        }
        return sInstance;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Connects to the signaling network and dials every peer in the arena. */
    public synchronized boolean start(String arenaId) {
        if (started) return true;
        if (arenaId == null || arenaId.isEmpty()) return false;
        Context ctx = GameApp.uiCtx();
        if (ctx == null || GameApp.player == null) return false;
        this.arenaId = arenaId;
        this.userId = GameApp.player.username == null ? "player" : GameApp.player.username;
        talking = false;
        managers.clear();

        PeerSignalingClient c = new PeerSignalingClient(peerIdOf(userId, arenaId), signalListener());
        signal = c;
        started = c.connect();
        if (started) {
            applyAudioRoute(true);
            discoverAndDial();
            startRedialLoop();
        } else {
            Log.w(TAG, "failed to connect signaling");
        }
        return started;
    }

    /** Tears down signaling + every peer connection. */
    public synchronized void stop() {
        started = false;
        talking = false;
        stopRedialLoop();
        PeerSignalingClient c = signal;
        signal = null;
        if (c != null) c.close();
        for (VoiceChatManager m : managers.values()) {
            try { m.dispose(); } catch (Throwable ignored) {}
        }
        managers.clear();
        arenaId = null;
        dialing = false;
        applyAudioRoute(false);
    }

    public boolean isActive() {
        return started;
    }

    // ------------------------------------------------------------------ talk

    /** Flips broadcasting on/off. Returns the new talking state. */
    public boolean toggleTalk() {
        talking = !talking;
        applyTalkState();
        return talking;
    }

    public boolean isTalking() {
        return talking;
    }

    private void applyTalkState() {
        for (VoiceChatManager m : managers.values()) {
            try { m.setMute(!talking); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ listening filters

    /** Stops/restores hearing a single remote player (host local mute). */
    public void setPeerListenEnabled(String remoteUsername, boolean enabled) {
        String prefix = remoteUsername + "~";
        for (Map.Entry<String, VoiceChatManager> e : managers.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                try { e.getValue().setListenEnabled(enabled); } catch (Throwable ignored) {}
            }
        }
    }

    /** Stops/restores hearing a single remote player by its encoded arena key. */
    public void setPeerListenEnabledByKey(String encodedMemberKey, boolean enabled) {
        setPeerListenEnabled(decodeKey(encodedMemberKey), enabled);
    }

    /** Inverse of Db.encode(): restores the raw username from an arena member key. */
    private static String decodeKey(String s) {
        if (s == null) return "";
        return s.replace("%2F", "/").replace("%5D", "]").replace("%5B", "[")
                .replace("%24", "$").replace("%23", "#").replace("%2E", ".");
    }

    /** Stops/restores hearing all remote players (host mute-all). */
    public void setAllPeersListenEnabled(boolean enabled) {
        for (VoiceChatManager m : managers.values()) {
            try { m.setListenEnabled(enabled); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ mesh

    private void discoverAndDial() {
        if (dialing) return;
        synchronized (meshLock) {
            if (dialing) return;
            dialing = true;
        }
        new Thread(() -> {
            try {
                // Give the socket time to register before asking the server.
                Thread.sleep(1200);
                PeerSignalingClient c = signal;
                if (c == null) { synchronized (meshLock) { dialing = false; } return; }
                java.util.List<String> peers = c.fetchPeers();
                if (peers == null) { synchronized (meshLock) { dialing = false; } return; }
                for (String p : peers) {
                    if (isInMyArena(p) && !p.equals(c.myPeerId())) {
                        ensureManager(p);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "discoverAndDial: " + t.getMessage());
            } finally {
                synchronized (meshLock) { dialing = false; }
            }
        }).start();
    }

    /** Creates a manager (and starts negotiation) for a remote peer. Single-flight. */
    private VoiceChatManager ensureManager(final String remotePeerId) {
        if (!started || remotePeerId == null) return null;
        synchronized (meshLock) {
            if (!started) return null;
            VoiceChatManager existing = managers.get(remotePeerId);
            if (existing != null) return existing;

            Context ctx = GameApp.uiCtx();
            if (ctx == null) return null;

            VoiceChatManager m = new VoiceChatManager(ctx, managerListener(remotePeerId));
            if (m.createPeerConnection(null) == null) {
                try { m.dispose(); } catch (Throwable ignored) {}
                return null;
            }
            // Re-check under lock: another thread may have created this peer while we were building.
            VoiceChatManager raced = managers.get(remotePeerId);
            if (raced != null) {
                try { m.dispose(); } catch (Throwable ignored) {}
                return raced;
            }
            managers.put(remotePeerId, m);
            // Deterministic glare avoidance: the smaller peer id always offers.
            String myId = signal == null ? "" : signal.myPeerId();
            if (myId.compareTo(remotePeerId) < 0) {
                try { m.startCall(); } catch (Throwable ignored) {}
            }
            try { m.setMute(!talking); } catch (Throwable ignored) {}
            return m;
        }
    }

    private VoiceChatManager getOrCreateRemote(String remotePeerId) {
        VoiceChatManager m = managers.get(remotePeerId);
        return m != null ? m : ensureManager(remotePeerId);
    }

    private void dropRemote(final String remotePeerId) {
        VoiceChatManager m = managers.remove(remotePeerId);
        if (m != null) {
            try { m.dispose(); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ keep-alive

    private void startRedialLoop() {
        stopRedialLoop();
        try {
            redialFuture = redialPool.scheduleWithFixedDelay(() -> {
                if (!started) return;
                try { discoverAndDial(); } catch (Throwable ignored) {}
            }, 6, 6, TimeUnit.SECONDS);
        } catch (Throwable ignored) {}
    }

    private void stopRedialLoop() {
        ScheduledFuture<?> f = redialFuture;
        redialFuture = null;
        if (f != null) {
            try { f.cancel(false); } catch (Throwable ignored) {}
        }
    }

    /**
     * Reopens the signaling socket after an unexpected close (server restart,
     * network blip, or an old duplicate-id kick). Without this the mesh silently
     * dies and voice goes quiet for the rest of the session.
     */
    private void scheduleReconnect() {
        if (!started) return;
        redialPool.schedule(() -> {
            PeerSignalingClient current = signal;
            if (!started || (current != null && !current.isClosed())) return;
            synchronized (VoiceChatController.this) {
                if (!started) return;
                PeerSignalingClient old = signal;
                if (old != null && !old.isClosed()) return;
                try { if (old != null) old.close(); } catch (Throwable ignored) {}
                PeerSignalingClient c = new PeerSignalingClient(peerIdOf(userId, arenaId), signalListener());
                signal = c;
                if (!c.connect()) {
                    scheduleReconnect();
                    return;
                }
            }
            discoverAndDial();
        }, 3, TimeUnit.SECONDS);
    }

    /** Routes voice to the loudspeaker while the mesh is active (else earpiece). */
    private void applyAudioRoute(boolean on) {
        try {
            Context ctx = GameApp.uiCtx();
            if (ctx == null) return;
            android.media.AudioManager am =
                    (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            if (on) {
                am.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                am.setSpeakerphoneOn(true);
            } else {
                am.setSpeakerphoneOn(false);
                am.setMode(android.media.AudioManager.MODE_NORMAL);
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ signaling callbacks

    private PeerSignalingClient.Listener signalListener() {
        return new PeerSignalingClient.Listener() {
            @Override public void onOpen(String myPeerId) {
                Log.d(TAG, "signaling open @ " + myPeerId);
                discoverAndDial();
            }

            @Override public void onRemoteOffer(String fromPeerId, SessionDescription offer) {
                if (!started || !isInMyArena(fromPeerId)) return;
                VoiceChatManager m = getOrCreateRemote(fromPeerId);
                if (m != null) {
                    try { m.acceptIncomingOffer(offer); } catch (Throwable ignored) {}
                }
            }

            @Override public void onRemoteAnswer(String fromPeerId, SessionDescription answer) {
                if (!started || !isInMyArena(fromPeerId)) return;
                VoiceChatManager m = managers.get(fromPeerId);
                if (m != null) {
                    try { m.setRemoteAnswer(answer); } catch (Throwable ignored) {}
                }
            }

            @Override public void onRemoteIceCandidate(String fromPeerId, IceCandidate candidate) {
                if (!started || !isInMyArena(fromPeerId)) return;
                VoiceChatManager m = getOrCreateRemote(fromPeerId);
                if (m != null) {
                    try { m.addRemoteIceCandidate(candidate); } catch (Throwable ignored) {}
                }
            }

            @Override public void onRemoteLeave(String peerId) {
                if (isInMyArena(peerId)) dropRemote(peerId);
            }

            @Override public void onError(String message) {
                Log.w(TAG, "signaling error: " + message);
            }

            @Override public void onClose(int code, String reason) {
                if (started) {
                    Log.w(TAG, "signaling closed (" + code + "): " + reason + " — reconnecting");
                    scheduleReconnect();
                }
            }
        };
    }

    private VoiceChatManager.Listener managerListener(final String remotePeerId) {
        return new VoiceChatManager.Listener() {
            @Override public void onLocalSdp(SessionDescription sdp) {
                PeerSignalingClient c = signal;
                if (c == null) return;
                boolean offer = "offer".equals(sdp.type.canonicalForm());
                if (offer) c.sendOffer(remotePeerId, sdp);
                else c.sendAnswer(remotePeerId, sdp);
            }

            @Override public void onLocalIceCandidate(IceCandidate candidate) {
                PeerSignalingClient c = signal;
                if (c != null) c.sendIceCandidate(remotePeerId, candidate);
            }

            @Override public void onIceConnectionState(PeerConnection.IceConnectionState state) {
                Log.d(TAG, "ice[" + remotePeerId + "] = " + state);
            }

            @Override public void onRemoteStreamAdded() {
                // Remote audio starts playing automatically (WebRTC AudioTrack).
            }

            @Override public void onError(String message) {
                Log.w(TAG, "voice[" + remotePeerId + "] error: " + message);
            }
        };
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Deterministic, globally-unique peer id: "<user>~<arenaId>".
     *
     * Only our own '~' delimiter is stripped from the parts. Everything else —
     * including non-Latin names (Arabic, etc.) — is preserved verbatim, because
     * the signaling server treats ids as unique: collapsing every Arabic letter
     * to '_' made two same-length Arabic usernames share one id, so the server
     * disconnected the first player the moment the second joined (the "kick").
     */
    private static String peerIdOf(String username, String arenaId) {
        String u = sanitizePeerPart(username == null ? "player" : username);
        String a = sanitizePeerPart(arenaId == null ? "" : arenaId);
        // Keep the id a sane length without losing uniqueness.
        if (u.length() > 48) u = u.substring(0, 48) + "-" + Integer.toHexString(u.hashCode());
        if (a.length() > 40) a = a.substring(0, 40) + "-" + Integer.toHexString(a.hashCode());
        return u + "~" + a;
    }

    private static String sanitizePeerPart(String s) {
        if (s == null) return "";
        return s.replace("~", "_").replace("%", "_").replace("/", "_").replace("?", "_")
                .replace("#", "_").replace("&", "_").replace("=", "_").replace(" ", "_");
    }

    private boolean isInMyArena(String peerId) {
        String a = arenaId;
        if (a == null || peerId == null) return false;
        int idx = peerId.lastIndexOf('~');
        if (idx < 0) return false;
        return peerId.substring(idx + 1).equals(sanitizePeerPart(a));
    }

    /** Convenience: is the mic permission granted (used by the arena UI). */
    public static boolean micPermissionGranted() {
        Context ctx = GameApp.uiCtx();
        if (ctx == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}