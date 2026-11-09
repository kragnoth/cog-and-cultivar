package com.kragnoth.cultivar.api.condition;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.mojang.serialization.Codec;

/**
 * One environmental requirement of a plant or mutation (light range, soil humidity, biome, heat...).
 * Serialised as {@code {"type": "<condition type id>", ...type-specific fields}}.
 * New condition kinds are added by registering a {@link GrowthConditionType}.
 */
public interface GrowthCondition {
    Codec<GrowthCondition> CODEC = CultivarRegistries.GROWTH_CONDITION_TYPE.byNameCodec()
            .dispatch(GrowthCondition::type, GrowthConditionType::codec);

    GrowthConditionType<?> type();

    /**
     * Tests the condition. Server-side only. Must be cheap and must not load chunks or mutate the world.
     *
     * @param strength the crop's expressed strength stat; higher strength widens tolerance
     */
    GrowthResponse test(GrowthContext context, int strength);

    /** Combined response of a list of conditions (worst wins; empty list is fertile). */
    static GrowthResponse testAll(Iterable<? extends GrowthCondition> conditions, GrowthContext context, int strength) {
        GrowthResponse result = GrowthResponse.FERTILE;
        for (GrowthCondition condition : conditions) {
            result = GrowthResponse.worst(result, condition.test(context, strength));
            if (result == GrowthResponse.KILL) {
                break;
            }
        }
        return result;
    }
}
