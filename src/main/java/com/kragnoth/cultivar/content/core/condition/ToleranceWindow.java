package com.kragnoth.cultivar.content.core.condition;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Shared tolerance maths (spec §2.4): the accepted range around a target widens by
 * {@code floor(tolerance * strength)} on each side. {@link Comparison} can open one side completely.
 */
public final class ToleranceWindow {
    private ToleranceWindow() {}

    public enum Comparison implements StringRepresentable {
        EQUAL, AT_LEAST, AT_MOST;

        public static final Codec<Comparison> CODEC = StringRepresentable.fromEnum(Comparison::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** True if {@code actual} lies in the window around [{@code min}, {@code max}] widened by strength. */
    public static boolean accepts(int actual, int min, int max, Comparison comparison, double tolerance, int strength) {
        int widen = (int) Math.floor(tolerance * strength);
        int lower = comparison == Comparison.AT_MOST ? Integer.MIN_VALUE : min - widen;
        int upper = comparison == Comparison.AT_LEAST ? Integer.MAX_VALUE : max + widen;
        return actual >= lower && actual <= upper;
    }

    public static boolean accepts(int actual, int target, Comparison comparison, double tolerance, int strength) {
        return accepts(actual, target, target, comparison, tolerance, strength);
    }
}
