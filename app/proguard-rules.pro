# Clash of Legends - R8/ProGuard rules
# The game uses direct Java calls only (no reflection).
# Keep the strongest obfuscation for everything else.

-optimizationpasses 5
-overloadaggressively
-allowaccessmodification
-mergeinterfacesaggressively

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions

# Prevent R8 from dropping useful annotations / exception meta needed by dialogs.
-keepattributes SourceFile, LineNumberTable

# App entry points referenced by AndroidManifest (kept un-obfuscated by name).
-keep class com.lli.com.GameApp { *; }
-keep class com.lli.com.MainActivity { *; }
-keep class com.lli.com.R$raw { *; }

# All UI/core game classes may be renamed freely by R8.
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn android.annotation.**

# ============ WebRTC (org.webrtc) — must be kept intact ============
# WebRTC uses JNI native bindings resolved by exact class/method names.
-keep class org.webrtc.** { *; }
-keep class org.webrtc.voiceengine.** { *; }
-dontwarn org.webrtc.**

# ============ OkHttp / Okio (WebSocket signaling) ============
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
# OkHttp internals reflectively instantiate some classes.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okio.**
-dontwarn java.lang.invoke.