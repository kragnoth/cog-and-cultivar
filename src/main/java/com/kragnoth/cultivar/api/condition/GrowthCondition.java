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
     * Tests the condition. Must be cheap, read-only, and must not load chunks or mutate the world. Growth runs it on the
     * server; readouts (goggles, Jade, planting hints) also run it on the client with synced state, so it should read
     * only what clients have too (blocks, light, biome, synced attachments).
     *
     * @param strength the crop's expressed strength stat; higher strength widens tolerance
     */
    GrowthResponse test(GrowthContext context, int strength);

    /**
     * True for conditions on the soil's own properties (humidity, acidity, nutrients). A plant with maximum
     * Strength is Hardy and skips these during growth; mutation habitats always test them.
     */
    default boolean isSoilProperty() {
        return false;
    }

    /**
     * What this requirement asks for, for readouts and seed tooltips ("Light 9+", "Humidity: wet or wetter"). Empty
     * when it asks for nothing worth showing (e.g. any light). Defaults to the condition type's name, translated as
     * {@code condition.<namespace>.<path>}.
     */
    default java.util.Optional<net.minecraft.network.chat.Component> describe() {
        var id = CultivarRegistries.GROWTH_CONDITION_TYPE.getKey(type());
        return java.util.Optional.of(id == null ? net.minecraft.network.chat.Component.literal("?")
                : net.minecraft.network.chat.Component.translatableWithFallback("condition." + id.getNamespace() + "." + id.getPath(), id.getPath()));
    }

    /** What this spot currently has for this requirement ("4", "moist"), shown next to a failing one. Optional. */
    default java.util.Optional<net.minecraft.network.chat.Component> current(GrowthContext context) {
        return java.util.Optional.empty();
    }

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
