package com.kragnoth.cultivar.api.soil;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Ordered soil property scales. Ordinal order is meaningful: growth requirements compare positions
 * on the scale, and strength widens the accepted window. Never reorder existing constants; append only.
 */
public final class SoilLevels {
    private SoilLevels() {}

    public enum Humidity implements StringRepresentable {
        ARID, DRY, DAMP, WET, WATERY, FLOODED;

        public static final Codec<Humidity> CODEC = StringRepresentable.fromEnum(Humidity::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Acidity implements StringRepresentable {
        HIGHLY_ACIDIC, ACIDIC, SLIGHTLY_ACIDIC, NEUTRAL, SLIGHTLY_ALKALINE, ALKALINE, HIGHLY_ALKALINE;

        public static final Codec<Acidity> CODEC = StringRepresentable.fromEnum(Acidity::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Nutrients implements StringRepresentable {
        NONE, VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH;

        public static final Codec<Nutrients> CODEC = StringRepresentable.fromEnum(Nutrients::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
