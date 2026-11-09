package com.kragnoth.cultivar.api.genetics;

/**
 * A heritable stat (gain, growth, strength...). Registered in {@code CultivarRegistries.STAT}.
 * Behaviour lives in the systems that read the stat; this type only describes its range and display colour.
 *
 * @param defaultMin lowest allele value (server config may narrow it)
 * @param defaultMax highest allele value (server config may narrow it)
 * @param color      ARGB colour for genome renders and tooltips
 */
public record Stat(int defaultMin, int defaultMax, int color) {
    public Stat {
        if (defaultMin > defaultMax) {
            throw new IllegalArgumentException("Stat min " + defaultMin + " > max " + defaultMax);
        }
    }

    public int clamp(int value) {
        return Math.max(defaultMin, Math.min(defaultMax, value));
    }
}
