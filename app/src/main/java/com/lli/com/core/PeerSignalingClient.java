package com.lli.com.core;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.WebSocket;
import okio.ByteString;

/**
 * WebSocket signaling client that speaks the PeerJS wire protocol to the
 * Render-hosted signaling server (voice-signaling/server.js).
 *
 * The server only coordinates the handshake: after an OFFER/ANSWER/ICE
 * exchange the WebRTC audio itself streams peer-to-peer (STUN only).
 *
 * Thread-safety: all public methods are safe to call from any thread
 * (OkHttp dispatches incoming messages on a background executor, which we
 * forward synchronously to the {@link Listener}; the listener is expected to
 * hand SDP/ICE straight to a VoiceChatManager — WebRTC calls are thread-safe).
 */
public final class PeerSignalingClient {
    private static final String TAG = "PeerSignal";

    /**
     * Signaling server base URL. Replace with your deployed Render service:
     * e.g. "https://your-service.onrender.com"
     */
    public static volatile String SIGNALING_BASE = "https://voice-signaling.onrender.com";

    private static final String API_KEY = "peerjs";
    private static final String CONN_ID = "voice";

    /** Events delivered to the caller (typically the VoiceChatController). */
    public interface Listener {
        /** Socket is open and registered with the given peer id. */
        void onOpen(String myPeerId);
        /** A remote peer sent an SDP offer (it wants to talk). */
        void onRemoteOffer(String fromPeerId, SessionDescription offer);
        /** A remote peer answered our offer. */
        void onRemoteAnswer(String fromPeerId, SessionDescription answer);
        /** Trickle ICE candidate from the remote peer. */
        void onRemoteIceCandidate(String fromPeerId, IceCandidate candidate);
        /** The remote peer is leaving the signaling network. */
        void onRemoteLeave(String peerId);
        /** Signaling error (network / protocol). */
        void onError(String message);
        /** Socket closed (server side or network). */
        void onClose(int code, String reason);
    }

    private final String myPeerId;
    /** Per-socket auth token; peer@1.x requires it on the query string. */
    private final String token = java.util.UUID.randomUUID().toString().replace("-", "");
    private final Listener listener;
    private final OkHttpClient httpClient;
    private final Object lock = new Object();
    private WebSocket webSocket;
    private volatile boolean closed = false;

    // PeerServer drops clients that stay silent for alive_timeout (90s), so we
    // send an application-level heartbeat. WebSocket ping frames do NOT count.
    private static final long HEARTBEAT_MS = 30000L;
    private final ScheduledExecutorService heartbeatPool =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "peer-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> heartbeatFuture;

    public PeerSignalingClient(String myPeerId, Listener listener) {
        this.myPeerId = myPeerId == null ? "player" : myPeerId;
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)   // streaming socket: never time out reads
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)      // keep WS alive through NATs/proxies
                .build();
    }

    /** Replaces the signaling base (call before {@link #connect()}). */
    public static void setSignalingBase(String base) {
        if (base != null && !base.trim().isEmpty()) {
            SIGNALING_BASE = base.trim();
        }
    }

    // ------------------------------------------------------------------ connect

    /** Opens the WebSocket to the signaling server. Returns false immediately on bad config. */
    public boolean connect() {
        String url = buildSocketUrl();
        if (url == null) {
            notifyError("Invalid signaling URL: " + SIGNALING_BASE);
            return false;
        }
        synchronized (lock) {
            if (closed) return false;
            if (webSocket != null) return true; // already connected/connecting
        }
        try {
            Request req = new Request.Builder().url(url).build();
            synchronized (lock) {
                if (closed) return false;
                webSocket = httpClient.newWebSocket(req, wsListener());
            }
            return true;
        } catch (Throwable t) {
            notifyError("Failed to open socket: " + t.getMessage());
            return false;
        }
    }

    private String buildSocketUrl() {
        try {
            String base = SIGNALING_BASE.trim();
            if (base.isEmpty()) return null;
            String wsBase = base.replaceFirst("^https://", "wss://").replaceFirst("^http://", "ws://");
            String idUrl = URLEncoder.encode(myPeerId, "UTF-8");
            String tokenUrl = URLEncoder.encode(token, "UTF-8");
            return wsBase + "/peerjs/peerjs?key=" + API_KEY + "&id=" + idUrl + "&token=" + tokenUrl;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ send

    /** Wraps and sends an SDP offer to the remote peer. */
    public boolean sendOffer(String toPeerId, SessionDescription offer) {
        if (offer == null) return false;
        try {
            JSONObject sdpObj = new JSONObject();
            sdpObj.put("type", offer.type.canonicalForm());
            sdpObj.put("sdp", offer.description);
            JSONObject payload = new JSONObject();
            payload.put("sdp", sdpObj);
            return sendMessage("OFFER", toPeerId, payload);
        } catch (JSONException e) {
            notifyError("sendOffer: " + e.getMessage());
            return false;
        }
    }

    /** Wraps and sends an SDP answer to the remote peer. */
    public boolean sendAnswer(String toPeerId, SessionDescription answer) {
        if (answer == null) return false;
        try {
            JSONObject sdpObj = new JSONObject();
            sdpObj.put("type", answer.type.canonicalForm());
            sdpObj.put("sdp", answer.description);
            JSONObject payload = new JSONObject();
            payload.put("sdp", sdpObj);
            return sendMessage("ANSWER", toPeerId, payload);
        } catch (JSONException e) {
            notifyError("sendAnswer: " + e.getMessage());
            return false;
        }
    }

    /** Wraps and sends a trickle ICE candidate to the remote peer. */
    public boolean sendIceCandidate(String toPeerId, IceCandidate candidate) {
        if (candidate == null) return false;
        try {
            JSONObject cand = new JSONObject();
            cand.put("candidate", candidate.sdp);
            cand.put("sdpMid", candidate.sdpMid == null ? "0" : candidate.sdpMid);
            cand.put("sdpMLineIndex", candidate.sdpMLineIndex);
            JSONObject payload = new JSONObject();
            payload.put("candidate", cand);
            return sendMessage("CANDIDATE", toPeerId, payload);
        } catch (JSONException e) {
            notifyError("sendIceCandidate: " + e.getMessage());
            return false;
        }
    }

    private boolean sendMessage(String type, String dst, JSONObject payload) {
        WebSocket ws;
        synchronized (lock) {
            if (closed) return false;
            ws = webSocket;
        }
        if (ws == null) return false;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", type);
            msg.put("src", myPeerId);
            msg.put("dst", dst);
            msg.put("connectionId", CONN_ID);
            msg.put("payload", payload == null ? JSONObject.NULL : payload);
            Log.d(TAG, "SEND " + type + " -> " + dst);
            return ws.send(msg.toString());
        } catch (JSONException e) {
            notifyError("sendMessage: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ discovery

    /**
     * Queries the signaling server for all currently connected peer ids
     * (requires allow_discovery: true, which the deployed server has).
     *
     * peer@1.x returns a JSON ARRAY of ids, e.g. ["a~room","b~room"];
     * older peerjs builds return an object {id: true}. Both are handled.
     * Call on a background thread. Returns an array-like normalized result or null.
     */
    public java.util.List<String> fetchPeers() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        try {
            // The PeerServer discovery route includes the key: /peerjs/peerjs/peers
            HttpURLConnection c = (HttpURLConnection) new URL(SIGNALING_BASE + "/peerjs/peerjs/peers").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (is == null) { c.disconnect(); return null; }
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            c.disconnect();
            String body = sb.toString().trim();
            if (body.isEmpty() || "null".equals(body)) return null;
            if (body.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i);
                    if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
                }
            } else {
                org.json.JSONObject obj = new org.json.JSONObject(body);
                java.util.Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    if (!ids.contains(key)) ids.add(key);
                }
            }
            return ids;
        } catch (Exception e) {
            Log.w(TAG, "fetchPeers failed: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ close

    public void close() {
        synchronized (lock) {
            closed = true;
            stopHeartbeat();
            if (webSocket != null) {
                try { webSocket.close(1000, "client leaving"); } catch (Throwable ignored) {}
                webSocket = null;
            }
        }
        try { heartbeatPool.shutdownNow(); } catch (Throwable ignored) {}
    }

    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    // ------------------------------------------------------------------ heartbeat

    private synchronized void startHeartbeat() {
        stopHeartbeat();
        if (closed) return;
        try {
            heartbeatFuture = heartbeatPool.scheduleWithFixedDelay(this::sendHeartbeat,
                    HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {}
    }

    private synchronized void stopHeartbeat() {
        if (heartbeatFuture != null) {
            try { heartbeatFuture.cancel(false); } catch (Throwable ignored) {}
            heartbeatFuture = null;
        }
    }

    private void sendHeartbeat() {
        WebSocket ws;
        synchronized (lock) {
            if (closed) return;
            ws = webSocket;
        }
        if (ws == null) return;
        try {
            JSONObject hb = new JSONObject();
            hb.put("type", "HEARTBEAT");
            ws.send(hb.toString());
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ receive

    private WebSocketListener wsListener() {
        return new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "socket open, peer=" + myPeerId);
                startHeartbeat();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                try {
                    handleMessage(bytes.utf8());
                } catch (Throwable ignored) {}
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                // Server initiated close; ack politely.
                try { ws.close(code, reason == null ? "" : reason); } catch (Throwable ignored) {}
                notifyClose(code, reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                notifyClose(code, reason);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.w(TAG, "socket failure: " + (t == null ? "unknown" : t.getMessage()));
                notifyClose(-1, t == null ? "network error" : t.getMessage());
            }
        };
    }

    private void handleMessage(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            JSONObject msg = new JSONObject(text);
            String type = msg.optString("type", "");
            String src = msg.optString("src", "");
            JSONObject payload = msg.optJSONObject("payload");

            if ("OPEN".equals(type)) {
                if (listener != null) {
                    try { listener.onOpen(myPeerId); } catch (Throwable ignored) {}
                }
                return;
            }

            // Heartbeat from the server (some PeerServer builds send PING).
            if ("PING".equals(type)) {
                sendPong();
                return;
            }
            if ("ERROR".equals(type)) {
                notifyError("server: " + msg.optString("message", msg.optString("error", type)));
                return;
            }
            if ("OFFER".equals(type)) {
                SessionDescription offer = parseSdp(payload);
                if (offer != null && listener != null) {
                    try { listener.onRemoteOffer(src, offer); } catch (Throwable ignored) {}
                }
                return;
            }
            if ("ANSWER".equals(type)) {
                SessionDescription answer = parseSdp(payload);
                if (answer != null && listener != null) {
                    try { listener.onRemoteAnswer(src, answer); } catch (Throwable ignored) {}
                }
                return;
            }
            if ("CANDIDATE".equals(type)) {
                IceCandidate candidate = parseCandidate(payload);
                if (candidate != null && listener != null) {
                    try { listener.onRemoteIceCandidate(src, candidate); } catch (Throwable ignored) {}
                }
                return;
            }
            if ("LEAVE".equals(type) || "EXPIRE".equals(type)) {
                if (listener != null) {
                    try { listener.onRemoteLeave(src.isEmpty() ? src : msg.optString("peer", "")); } catch (Throwable ignored) {}
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "bad signaling message: " + e.getMessage());
        } catch (Throwable t) {
            notifyError("handleMessage: " + t.getMessage());
        }
    }

    private SessionDescription parseSdp(JSONObject payload) {
        try {
            if (payload == null) return null;
            JSONObject sdpObj = payload.optJSONObject("sdp");
            // Tolerate flattened payloads: {type, sdp} directly.
            if (sdpObj == null && payload.has("sdp")) {
                sdpObj = payload;
            }
            if (sdpObj == null) return null;
            String type = sdpObj.optString("type", "offer");
            String sdp = sdpObj.optString("sdp", "");
            if (sdp.isEmpty()) return null;
            SessionDescription.Type t = SessionDescription.Type.fromCanonicalForm(type);
            return new SessionDescription(t, sdp);
        } catch (Throwable t) {
            Log.w(TAG, "parseSdp failed: " + t.getMessage());
            return null;
        }
    }

    private IceCandidate parseCandidate(JSONObject payload) {
        try {
            if (payload == null) return null;
            JSONObject cand = payload.optJSONObject("candidate");
            if (cand == null && payload.has("candidate")) cand = payload;
            if (cand == null) return null;
            String sdp = cand.optString("candidate", "");
            if (sdp.isEmpty()) return null;
            String mid = cand.optString("sdpMid", "0");
            int mline = cand.optInt("sdpMLineIndex", 0);
            return new IceCandidate(mid, mline, sdp);
        } catch (Throwable t) {
            Log.w(TAG, "parseCandidate failed: " + t.getMessage());
            return null;
        }
    }

    private void sendPong() {
        WebSocket ws;
        synchronized (lock) {
            if (closed) return;
            ws = webSocket;
        }
        if (ws == null) return;
        try {
            JSONObject pong = new JSONObject();
            pong.put("type", "PONG");
            ws.send(pong.toString());
        } catch (Throwable ignored) {}
    }

    private void notifyError(String message) {
        if (listener != null) {
            try { listener.onError(message); } catch (Throwable ignored) {}
        }
    }

    private void notifyClose(int code, String reason) {
        synchronized (lock) {
            webSocket = null;
        }
        stopHeartbeat();
        if (listener != null) {
            try { listener.onClose(code, reason); } catch (Throwable ignored) {}
        }
    }

    public String myPeerId() {
        return myPeerId;
    }
}