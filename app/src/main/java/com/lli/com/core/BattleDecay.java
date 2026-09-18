package com.lli.com.core;

/** Temporary in-battle stat decay.
 *  High agility/defense values slowly wear off during a battle only:
 *  the effect (full value) persists for the first ~35 seconds, then starts to fall
 *  until it runs out. The larger the base stat, the slower it decays, so extreme
 *  values practically never run out inside a normal battle. Base stats are never
 *  modified; the effective value is recomputed per tick and disappears after battle. */
public class BattleDecay {
    private static final double PERSIST_SEC = 35.0;

    public static double effective(double base, double elapsedSec) {
        if (base <= 0) return 0;
        double eff = base;
        if (elapsedSec > PERSIST_SEC) {
            double t = elapsedSec - PERSIST_SEC;
            double totalDecayTime = 60.0 + (base / 8.0);
            double frac = Math.min(1.0, t / totalDecayTime);
            eff = base * (1.0 - frac);
        }
        return Math.max(0, eff);
    }
}