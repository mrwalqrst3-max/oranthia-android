package com.lli.com.core;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe WebRTC voice manager.
 *
 * Responsibilities:
 *  - One-time initialization of the global {@link PeerConnectionFactory}.
 *  - Capturing the microphone into a local {@link AudioTrack} with noise
 *    suppression, echo cancellation and automatic gain control enabled.
 *  - Creating one {@link PeerConnection} per remote peer (STUN only, so the
 *    audio stream is direct P2P — nothing passes through a media server).
 *  - Producing SDP offers/answers and ICE candidates that the caller forwards
 *    over the signaling channel, and consuming the remote ones.
 *  - Mute/unmute and leak-free {@link #dispose()}.
 */
public final class VoiceChatManager {
    private static final String TAG = "VoiceChat";

    // Single shared factory for the whole app (lazy, thread-safe).
    private static final Object FACTORY_LOCK = new Object();
    private static volatile PeerConnectionFactory sSharedFactory = null;
    private static volatile boolean sFactoryInitializing = false;

    /**
     * One shared microphone track for the whole app. In a voice mesh every
     * remote peer gets a PeerConnection, but the same AudioTrack may be added
     * to all of them — so there is exactly ONE mic capture session.
     */
    private static final Object MIC_LOCK = new Object();
    // ONE shared AudioSource (single mic capture) but a SEPARATE AudioTrack per
    // PeerConnection. Adding the same AudioTrack to two peer connections is not
    // supported by libwebrtc and crashes the app the moment a second peer starts
    // talking — the "two players open the mic -> kicked" bug.
    private static volatile AudioSource sSharedSource = null;
    private static volatile boolean sTalkEnabled = false;
    private static int sTrackCounter = 0;
    private static final java.util.List<AudioTrack> sTracks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final AtomicInteger ACTIVE_MANAGERS = new AtomicInteger(0);

    /** Events the signaling/controller layer needs. */
    public interface Listener {
        /** A freshly created local SDP (type = offer or answer). Send it to the remote peer. */
        void onLocalSdp(SessionDescription sdp);
        /** A local ICE candidate to forward to the remote peer. */
        void onLocalIceCandidate(IceCandidate candidate);
        /** ICE connection state changed (e.g. CONNECTED / FAILED). */
        void onIceConnectionState(PeerConnection.IceConnectionState state);
        /** Remote audio track is available and will be played by WebRTC automatically. */
        void onRemoteStreamAdded();
        /** Non-fatal error; the caller may surface it in the UI. */
        void onError(String message);
    }

    private final Context mContext;
    private final Listener mListener;

    private final Object mLock = new Object();
    private volatile boolean mDisposed = false;

    private MediaConstraints mAudioConstraints;
    private AudioSource mAudioSource;
    private AudioTrack mLocalTrack;
    private MediaStream mLocalStream;
    private PeerConnection mPeerConnection;
    private volatile boolean mMuted = false;

    public VoiceChatManager(Context context, Listener listener) {
        mContext = context != null ? context.getApplicationContext() : null;
        mListener = listener;
        ensureFactory(mContext);
        mAudioConstraints = buildAudioConstraints();
        ACTIVE_MANAGERS.incrementAndGet();
        refreshMicPower();
    }

    /** One-time global WebRTC factory init (safe to call many times, waits for pending init). */
    private static void ensureFactory(Context context) {
        if (sSharedFactory != null) return;
        synchronized (FACTORY_LOCK) {
            if (sSharedFactory != null) return;
            if (sFactoryInitializing) {
                // Another thread is already initializing; wait for it to finish.
                try {
                    FACTORY_LOCK.wait(10000);
                } catch (InterruptedException ignored) {}
                return;
            }
            sFactoryInitializing = true;
            try {
                PeerConnectionFactory.InitializationOptions initOptions =
                        PeerConnectionFactory.InitializationOptions.builder(context)
                                .setEnableInternalTracer(false)
                                .createInitializationOptions();
                PeerConnectionFactory.initialize(initOptions);
                sSharedFactory = PeerConnectionFactory.builder()
                        .createPeerConnectionFactory();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to init PeerConnectionFactory", t);
            } finally {
                sFactoryInitializing = false;
                FACTORY_LOCK.notifyAll();
            }
        }
    }

    private static PeerConnectionFactory factory() {
        return sSharedFactory;
    }

    /**
     * Creates a fresh per-connection mic track from the single shared
     * AudioSource (safe from any thread). Each PeerConnection gets its own
     * AudioTrack while the mic is captured only once.
     */
    private static AudioTrack createMicrophoneTrack() {
        synchronized (MIC_LOCK) {
            if (sSharedFactory == null) return null;
            try {
                if (sSharedSource == null) {
                    sSharedSource = sSharedFactory.createAudioSource(buildAudioConstraints());
                }
                AudioTrack trk = sSharedFactory.createAudioTrack("audiolocal" + (sTrackCounter++), sSharedSource);
                if (trk == null) return null;
                try { trk.setEnabled(sTalkEnabled && ACTIVE_MANAGERS.get() > 0); } catch (Throwable ignored) {}
                sTracks.add(trk);
                return trk;
            } catch (Throwable t) {
                Log.e(TAG, "createMicrophoneTrack failed", t);
                return null;
            }
        }
    }

    /** Powers every live mic track up/down based on talk state and live managers. */
    private static void refreshMicPower() {
        boolean on = sTalkEnabled && ACTIVE_MANAGERS.get() > 0;
        for (AudioTrack t : sTracks) {
            try { t.setEnabled(on); } catch (Throwable th) {
                Log.w(TAG, "refreshMicPower failed: " + th.getMessage());
            }
        }
    }

    /** Noise suppression + echo cancellation + AGC forced ON for clear voice. */
    private static MediaConstraints buildAudioConstraints() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("googAudioMirroring", "false"));
        // Keep the mic sample rate sensible for speech.
        c.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        return c;
    }

    private static MediaConstraints buildSdpConstraints() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        return c;
    }

    private static List<PeerConnection.IceServer> iceServers() {
        // STUN only — after NAT traversal the audio flows peer-to-peer directly.
        return Collections.singletonList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
    }

    /**
     * Creates the microphone source + track and a new PeerConnection for one
     * remote peer. Returns the created connection (or null on failure).
     * The provided observer is used for remote-stream/state events.
     */
    public PeerConnection createPeerConnection(final PeerConnection.Observer observer) {
        synchronized (mLock) {
            if (mDisposed || factory() == null) { notifyError("WebRTC not initialized"); return null; }
            if (mPeerConnection != null) { notifyError("PeerConnection already created"); return mPeerConnection; }

            // One mic track per connection, all fed by the single shared source.
            AudioTrack track = createMicrophoneTrack();
            if (track == null) { notifyError("Failed to open microphone"); return null; }
            mLocalTrack = track; // instance ref for mute/disable helpers

            try {
                MediaStream localStream = factory().createLocalMediaStream("localstream" + hashCode());
                localStream.addTrack(mLocalTrack);
                mLocalStream = localStream;
            } catch (Throwable t) {
                Log.e(TAG, "createLocalMediaStream failed", t);
                notifyError("Failed to prepare audio stream: " + t.getMessage());
                return null;
            }

            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers());
            rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

            // Wrap the caller's observer: forward everything, but chill ICE
            // events through the listener too (the controller sends them).
            PeerConnection.Observer wiring = new PeerConnection.Observer() {
                @Override public void onSignalingChange(PeerConnection.SignalingState newState) {
                    if (observer != null) observer.onSignalingChange(newState);
                }
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                    if (mListener != null) mListener.onIceConnectionState(newState);
                    if (observer != null) observer.onIceConnectionChange(newState);
                }
                @Override public void onIceConnectionReceivingChange(boolean receiving) {
                    if (observer != null) observer.onIceConnectionReceivingChange(receiving);
                }
                @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
                    if (observer != null) observer.onIceGatheringChange(newState);
                }
                @Override public void onIceCandidate(IceCandidate candidate) {
                    if (mListener != null) mListener.onLocalIceCandidate(candidate);
                    if (observer != null) observer.onIceCandidate(candidate);
                }
                @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {
                    if (observer != null) observer.onIceCandidatesRemoved(candidates);
                }
                @Override public void onAddStream(MediaStream stream) {
                    if (mListener != null) mListener.onRemoteStreamAdded();
                    if (observer != null) observer.onAddStream(stream);
                }
                @Override public void onRemoveStream(MediaStream stream) {
                    if (observer != null) observer.onRemoveStream(stream);
                }
                @Override public void onDataChannel(org.webrtc.DataChannel dataChannel) {
                    if (observer != null) observer.onDataChannel(dataChannel);
                }
                @Override public void onRenegotiationNeeded() {
                    if (observer != null) observer.onRenegotiationNeeded();
                }
                @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                    if (mListener != null) mListener.onRemoteStreamAdded();
                    if (observer != null) observer.onAddTrack(receiver, mediaStreams);
                }
            };

            mPeerConnection = factory().createPeerConnection(rtcConfig, wiring);
            if (mPeerConnection == null) {
                notifyError("Failed to create peer connection");
                return null;
            }

            // Attach the local mic track to the connection (modern WebRTC takes stream ids).
            try {
                mPeerConnection.addTrack(mLocalTrack, Collections.singletonList(mLocalStream.getId()));
            } catch (Throwable t) {
                Log.e(TAG, "addTrack failed", t);
                // Not fatal: the SDP constraints still carry the audio; try addStream fallback.
                try {
                    mPeerConnection.addStream(mLocalStream);
                } catch (Throwable t2) {
                    Log.e(TAG, "addStream fallback failed", t2);
                }
            }
            return mPeerConnection;
        }
    }

    // ------------------------------------------------------------------ SDP

    /** Initiator: create and locally set the offer, then report it via the listener. */
    public void startCall() {
        PeerConnection pc;
        synchronized (mLock) {
            if (mDisposed) return;
            pc = mPeerConnection;
        }
        if (pc == null) { notifyError("No peer connection"); return; }
        pc.createOffer(new org.webrtc.SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                applyLocalSdp(sdp);
            }
            @Override public void onCreateFailure(String error) {
                notifyError("createOffer failed: " + error);
            }
            @Override public void onSetSuccess() {}
            @Override public void onSetFailure(String error) {
                notifyError("setLocalDescription(offer) failed: " + error);
            }
        }, buildSdpConstraints());
    }

    /** Receiver: set the remote offer, then locally create + set the answer. */
    public void acceptIncomingOffer(final SessionDescription offer) {
        PeerConnection pc;
        synchronized (mLock) {
            if (mDisposed) return;
            pc = mPeerConnection;
        }
        if (pc == null) { notifyError("No peer connection"); return; }
        pc.setRemoteDescription(new org.webrtc.SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {}
            @Override public void onCreateFailure(String error) {}
            @Override public void onSetSuccess() {
                // The manager may have been disposed while we were setting the
                // remote description (peer left / arena closed). Using the old
                // connection here would touch freed native memory.
                PeerConnection live;
                synchronized (mLock) {
                    if (mDisposed) return;
                    live = mPeerConnection;
                }
                if (live == null) return;
                live.createAnswer(new org.webrtc.SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription answer) {
                        applyLocalSdp(answer);
                    }
                    @Override public void onCreateFailure(String error) {
                        notifyError("createAnswer failed: " + error);
                    }
                    @Override public void onSetSuccess() {}
                    @Override public void onSetFailure(String error) {
                        notifyError("setLocalDescription(answer) failed: " + error);
                    }
                }, buildSdpConstraints());
            }
            @Override public void onSetFailure(String error) {
                notifyError("setRemote(offer) failed: " + error);
            }
        }, offer);
    }

    /** Initiator: apply the remote answer. */
    public void setRemoteAnswer(final SessionDescription answer) {
        PeerConnection pc;
        synchronized (mLock) {
            if (mDisposed) return;
            pc = mPeerConnection;
        }
        if (pc == null) { notifyError("No peer connection"); return; }
        pc.setRemoteDescription(new org.webrtc.SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {}
            @Override public void onCreateFailure(String error) {}
            @Override public void onSetSuccess() { /* connected path */ }
            @Override public void onSetFailure(String error) {
                notifyError("setRemote(answer) failed: " + error);
            }
        }, answer);
    }

    private void applyLocalSdp(final SessionDescription sdp) {
        PeerConnection pc;
        synchronized (mLock) {
            if (mDisposed) return;
            pc = mPeerConnection;
        }
        if (pc == null) { notifyError("No peer connection"); return; }
        pc.setLocalDescription(new org.webrtc.SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {}
            @Override public void onCreateFailure(String error) {}
            @Override public void onSetSuccess() {
                if (mListener != null) mListener.onLocalSdp(sdp);
            }
            @Override public void onSetFailure(String error) {
                notifyError("setLocalDescription failed: " + error);
            }
        }, sdp);
    }

    /** Add a remote ICE candidate (from the signaling channel). */
    public void addRemoteIceCandidate(IceCandidate candidate) {
        PeerConnection pc;
        synchronized (mLock) {
            if (mDisposed) return;
            pc = mPeerConnection;
        }
        if (pc == null || candidate == null) return;
        try {
            boolean ok = pc.addIceCandidate(candidate);
            if (!ok) {
                Log.w(TAG, "addIceCandidate rejected (possibly stale): " + candidate.sdpMid);
            }
        } catch (Throwable t) {
            Log.w(TAG, "addIceCandidate failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ mute

    public boolean isMuted() {
        return mMuted;
    }

    /**
     * Mutes/unmutes the shared microphone track. The mic is captured once for
     * the whole mesh, so toggling the track (rather than swapping each sender's
     * track) keeps every PeerConnection in sync and avoids the native
     * renegotiation that {@code setTrack(null)} could trigger.
     */
    public void setMute(boolean muted) {
        mMuted = muted;
        sTalkEnabled = !muted;
        refreshMicPower();
    }

    public void unmute() {
        setMute(false);
    }

    public void mute() {
        setMute(true);
    }

    /**
     * Enables/disables the REMOTE audio of this manager (muting what we hear
     * from that peer without stopping our own microphone toward it).
     */
    public void setListenEnabled(boolean enabled) {
        PeerConnection pc;
        synchronized (mLock) {
            if (mDisposed) return;
            pc = mPeerConnection;
        }
        if (pc == null) return;
        try {
            List<org.webrtc.RtpReceiver> receivers = pc.getReceivers();
            if (receivers == null) return;
            for (org.webrtc.RtpReceiver r : receivers) {
                if (r.track() != null) {
                    try { r.track().setEnabled(enabled); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "setListenEnabled failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Frees every native resource this manager owns. Safe to call twice. */
    public void dispose() {
        synchronized (mLock) {
            if (mDisposed) return;
            mDisposed = true;

            if (mPeerConnection != null) {
                try {
                    mPeerConnection.close();
                } catch (Throwable t) {
                    Log.w(TAG, "peerConnection.close() error: " + t.getMessage());
                }
                try { mPeerConnection.dispose(); } catch (Throwable t) {
                    Log.w(TAG, "peerConnection.dispose() error: " + t.getMessage());
                }
                mPeerConnection = null;
            }
            cleanupTracks();
            mAudioConstraints = null;
        }
        if (ACTIVE_MANAGERS.decrementAndGet() < 0) ACTIVE_MANAGERS.set(0);
        refreshMicPower();
    }

    private void cleanupTracks() {
        // Dispose this manager's own mic track (removing it from the live set);
        // the shared AudioSource is owned app-wide and released when the app exits.
        try { if (mLocalTrack != null) { sTracks.remove(mLocalTrack); mLocalTrack.dispose(); } } catch (Throwable ignored) {}
        try { if (mLocalStream != null) mLocalStream.dispose(); } catch (Throwable ignored) {}
        mLocalStream = null;
        mLocalTrack = null;
        mAudioSource = null;
    }

    private void notifyError(String msg) {
        if (mListener != null) {
            try { mListener.onError(msg); } catch (Throwable ignored) {}
        }
    }

    public boolean isDisposed() {
        return mDisposed;
    }

    public PeerConnection peerConnection() {
        synchronized (mLock) {
            return mPeerConnection;
        }
    }

    public static boolean isWebRtcAvailable() {
        return sSharedFactory != null;
    }
}