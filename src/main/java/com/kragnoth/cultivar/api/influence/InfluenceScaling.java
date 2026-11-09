package com.kragnoth.cultivar.api.influence;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.mojang.serialization.Codec;

/**
 * How strongly a source currently applies, from 0 (off) to 1 (full). For example: a beehive scales by
 * the number of bees inside, and a powered apiary by its RPM. Serialised as {@code {"type": "<id>", ...}}.
 * The evaluation method is added in Phase 1 with the influence lookup.
 */
public interface InfluenceScaling {
    Codec<InfluenceScaling> CODEC = CultivarRegistries.INFLUENCE_SCALING_TYPE.byNameCodec()
            .dispatch(InfluenceScaling::type, InfluenceScalingType::codec);

    InfluenceScalingType<?> type();
}
