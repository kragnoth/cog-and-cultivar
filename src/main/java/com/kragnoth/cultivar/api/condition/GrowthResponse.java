package com.kragnoth.cultivar.api.condition;

/**
 * Result of testing a growth condition. Declared from best to worst; when several conditions apply,
 * the worst response wins ({@link #worst}).
 */
public enum GrowthResponse {
    /** Conditions met: the plant may grow this tick. */
    FERTILE,
    /** Conditions not met: no growth, no harm. */
    INFERTILE,
    /** Hostile: the plant regresses one stage. */
    LETHAL,
    /** Destroyed outright (e.g. lava). */
    KILL;

    public static GrowthResponse worst(GrowthResponse a, GrowthResponse b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public boolean canGrow() {
        return this == FERTILE;
    }
}
