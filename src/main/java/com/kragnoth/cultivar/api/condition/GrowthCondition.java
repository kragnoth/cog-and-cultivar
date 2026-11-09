package com.kragnoth.cultivar.api.condition;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.mojang.serialization.Codec;

/**
 * One environmental requirement of a plant or mutation (light range, soil humidity, heat, biome...).
 * Serialised as {@code {"type": "<condition type id>", ...type-specific fields}}.
 *
 * <p>Evaluation methods are added in Phase 1 alongside the growth tick. New condition kinds are
 * added by registering a {@link GrowthConditionType}; no changes to this interface are needed.
 */
public interface GrowthCondition {
    Codec<GrowthCondition> CODEC = CultivarRegistries.GROWTH_CONDITION_TYPE.byNameCodec()
            .dispatch(GrowthCondition::type, GrowthConditionType::codec);

    GrowthConditionType<?> type();
}
