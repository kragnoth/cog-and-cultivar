package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthConditionType;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Soil property requirements. No registered soil under the crop is always INFERTILE.
 * <pre>{"type": "cultivar:humidity", "value": "wet", "comparison": "equal", "tolerance": 0.15}</pre>
 */
public final class SoilConditions {
    private SoilConditions() {}

    static <E extends Enum<E>> GrowthResponse test(Optional<E> actual, E target, ToleranceWindow.Comparison comparison,
                                                    double tolerance, int strength) {
        return actual.filter(a -> ToleranceWindow.accepts(a.ordinal(), target.ordinal(), comparison, tolerance, strength))
                .isPresent() ? GrowthResponse.FERTILE : GrowthResponse.INFERTILE;
    }

    public record Humidity(SoilLevels.Humidity value, ToleranceWindow.Comparison comparison, double tolerance)
            implements GrowthCondition {
        public static final MapCodec<Humidity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SoilLevels.Humidity.CODEC.fieldOf("value").forGetter(Humidity::value),
                ToleranceWindow.Comparison.CODEC.optionalFieldOf("comparison", ToleranceWindow.Comparison.EQUAL).forGetter(Humidity::comparison),
                Codec.doubleRange(0, 4).optionalFieldOf("tolerance", 0.15).forGetter(Humidity::tolerance)
        ).apply(i, Humidity::new));

        @Override
        public GrowthConditionType<?> type() {
            return CoreConditions.HUMIDITY.get();
        }

        @Override
        public GrowthResponse test(GrowthContext context, int strength) {
            return SoilConditions.test(context.humidity(), value, comparison, tolerance, strength);
        }
    }

    public record Acidity(SoilLevels.Acidity value, ToleranceWindow.Comparison comparison, double tolerance)
            implements GrowthCondition {
        public static final MapCodec<Acidity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SoilLevels.Acidity.CODEC.fieldOf("value").forGetter(Acidity::value),
                ToleranceWindow.Comparison.CODEC.optionalFieldOf("comparison", ToleranceWindow.Comparison.EQUAL).forGetter(Acidity::comparison),
                Codec.doubleRange(0, 4).optionalFieldOf("tolerance", 0.2).forGetter(Acidity::tolerance)
        ).apply(i, Acidity::new));

        @Override
        public GrowthConditionType<?> type() {
            return CoreConditions.ACIDITY.get();
        }

        @Override
        public GrowthResponse test(GrowthContext context, int strength) {
            return SoilConditions.test(context.acidity(), value, comparison, tolerance, strength);
        }
    }

    public record Nutrients(SoilLevels.Nutrients value, ToleranceWindow.Comparison comparison, double tolerance)
            implements GrowthCondition {
        public static final MapCodec<Nutrients> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SoilLevels.Nutrients.CODEC.fieldOf("value").forGetter(Nutrients::value),
                ToleranceWindow.Comparison.CODEC.optionalFieldOf("comparison", ToleranceWindow.Comparison.AT_LEAST).forGetter(Nutrients::comparison),
                Codec.doubleRange(0, 4).optionalFieldOf("tolerance", 0.1).forGetter(Nutrients::tolerance)
        ).apply(i, Nutrients::new));

        @Override
        public GrowthConditionType<?> type() {
            return CoreConditions.NUTRIENTS.get();
        }

        @Override
        public GrowthResponse test(GrowthContext context, int strength) {
            return SoilConditions.test(context.nutrients(), value, comparison, tolerance, strength);
        }
    }
}
