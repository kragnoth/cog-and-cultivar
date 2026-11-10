package com.kragnoth.cultivar.api.influence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Modifiers a nearby source applies to crops in range, at full strength (scale = 1).
 * Partial scale interpolates each value toward its neutral default.
 *
 * @param growthMultiplier   multiplies the per-tick growth chance (neutral 1)
 * @param mutationMultiplier multiplies a mutation's success chance when crossing (neutral 1)
 * @param fertilityBonus     added to parent Vigour when rolling to be chosen as a parent: "pollination" (neutral 0)
 * @param mutativityBonus    added to Vigour when rolling stat drift on offspring, so more drift, biased upward (neutral 0)
 * @param weedMultiplier     multiplies the chance a tick goes to weeds (neutral 1; below 1 suppresses weeds)
 */
public record InfluenceEffects(double growthMultiplier,
                               double mutationMultiplier,
                               int fertilityBonus,
                               int mutativityBonus,
                               double weedMultiplier) {
    public static final InfluenceEffects NONE = new InfluenceEffects(1, 1, 0, 0, 1);

    public static final Codec<InfluenceEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, 4).optionalFieldOf("growth_multiplier", 1.0).forGetter(InfluenceEffects::growthMultiplier),
            Codec.doubleRange(0, 4).optionalFieldOf("mutation_multiplier", 1.0).forGetter(InfluenceEffects::mutationMultiplier),
            Codec.intRange(0, 10).optionalFieldOf("fertility_bonus", 0).forGetter(InfluenceEffects::fertilityBonus),
            Codec.intRange(0, 10).optionalFieldOf("mutativity_bonus", 0).forGetter(InfluenceEffects::mutativityBonus),
            Codec.doubleRange(0, 4).optionalFieldOf("weed_multiplier", 1.0).forGetter(InfluenceEffects::weedMultiplier)
    ).apply(i, InfluenceEffects::new));

    /** Effects at a fractional strength; integer bonuses round down. */
    public InfluenceEffects scaled(double scale) {
        double s = Math.max(0, Math.min(1, scale));
        return new InfluenceEffects(
                1 + (growthMultiplier - 1) * s,
                1 + (mutationMultiplier - 1) * s,
                (int) Math.floor(fertilityBonus * s),
                (int) Math.floor(mutativityBonus * s),
                1 + (weedMultiplier - 1) * s);
    }
}
