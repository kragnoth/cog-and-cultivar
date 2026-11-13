package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthConditionType;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** {@code {"type": "cultivar:light", "min": 10, "max": 15, "tolerance": 0.5}}. Reads {@link CropLight} (cloches can override). */
public record LightCondition(int min, int max, double tolerance) implements GrowthCondition {
    public static final MapCodec<LightCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 15).optionalFieldOf("min", 10).forGetter(LightCondition::min),
            Codec.intRange(0, 15).optionalFieldOf("max", 15).forGetter(LightCondition::max),
            Codec.doubleRange(0, 4).optionalFieldOf("tolerance", 0.5).forGetter(LightCondition::tolerance)
    ).apply(i, LightCondition::new));

    @Override
    public GrowthConditionType<?> type() {
        return CoreConditions.LIGHT.get();
    }

    @Override
    public GrowthResponse test(GrowthContext context, int strength) {
        int light = CropLight.at(context.level(), context.pos());
        return ToleranceWindow.accepts(light, min, max, ToleranceWindow.Comparison.EQUAL, tolerance, strength)
                ? GrowthResponse.FERTILE : GrowthResponse.INFERTILE;
    }
}
