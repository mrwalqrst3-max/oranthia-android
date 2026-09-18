package com.lli.com.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.List;

public class VpnGuard {
    private static final String[] VPN_PACKAGES = {
            "com.nordvpn.android", "com.expressvpn.vpn.quickaccess", "com.surfshark.vpnclient.android",
            "com.windscribe.vpn.client.android.mobile", "com.privateinternetaccess.android", "app.cyberpunk.vpnclient",
            "com.getsurfboard", "com.anchorfree.hotspotshield", "org.potato.network", "com.tunnelbear.android",
            "com.att.vpn", "com.hotspotshield", "com.free.vpn.super.hotspot.open", "vpn.free",
            "com.lionvpn", "protonvpn.android", "com.protonvpn.android", "com.vpn.v2ray", "com.v2ray.pro",
            "org.torproject.android", "com.psiphon3", "com.blokada.app", "com.bytefrick.vpn", "com.nia.product",
            "me.uproot.android", "com.krypton.vpn", "com.vpn.unlimited", "io.kloong.vpn", "com.securevpn",
            "com.securetunnel", "com.root.unlimited", "com.openvpn.connect", "com.ipvanish.vpn",
            "com.cyberghost.vpn", "com.torguard.vpnclient.android", "com.vyprvpn.android"};

    private static final String[] SYSTEM_VPN_PACKAGES = {
            "com.android.vpndialogs", "android.system.vpndialogs", "com.oppo.vpn", "cn.oneplus.vpn"};

    public static boolean isVpnPresent(Context ctx) {
        if (ctx == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network[] nets = cm.getAllNetworks();
                if (nets != null) {
                    for (Network n : nets) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            List<PackageInfo> pkgs = ctx.getPackageManager().getInstalledPackages(0);
            if (pkgs != null) {
                outer:
                for (PackageInfo pi : pkgs) {
                    if (pi == null || pi.packageName == null) continue;
                    String pn = pi.packageName.toLowerCase(java.util.Locale.US);
                    if (pn.isEmpty()) continue;
                    for (String sp : SYSTEM_VPN_PACKAGES) {
                        if (pn.equals(sp)) continue outer;
                    }
                    for (String vp : VPN_PACKAGES) {
                        if (pn.equals(vp) || pn.startsWith(vp)) return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}