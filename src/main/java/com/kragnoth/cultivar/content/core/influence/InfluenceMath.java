package com.kragnoth.cultivar.content.core.influence;

import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;

/**
 * Combines effects from different influence entries acting on one crop: multipliers multiply,
 * bonuses add, then the totals are clamped (spec §3.15). "Same entry doesn't stack" is enforced by the
 * caller, which passes only the strongest instance of each entry.
 */
public final class InfluenceMath {
    private InfluenceMath() {}

    public record Caps(double maxGrowthMultiplier, double maxMutationMultiplier, double minWeedMultiplier,
                       int maxFertilityBonus, int maxMutativityBonus) {
        public static final Caps NONE = new Caps(Double.MAX_VALUE, Double.MAX_VALUE, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** Caps from server config. Only call on the server once configs are loaded. */
    public static Caps configuredCaps() {
        return new Caps(4.0, CultivarServerConfig.MAX_MUTATION_MULTIPLIER.get(),
                CultivarServerConfig.MIN_WEED_MULTIPLIER.get(), 3, 3);
    }

    public static InfluenceEffects combine(Iterable<InfluenceEffects> effects, Caps caps) {
        double growth = 1, mutation = 1, weed = 1;
        int fertility = 0, mutativity = 0;
        for (InfluenceEffects e : effects) {
            growth *= e.growthMultiplier();
            mutation *= e.mutationMultiplier();
            weed *= e.weedMultiplier();
            fertility += e.fertilityBonus();
            mutativity += e.mutativityBonus();
        }
        return new InfluenceEffects(
                Math.min(growth, caps.maxGrowthMultiplier()),
                Math.min(mutation, caps.maxMutationMultiplier()),
                Math.min(fertility, caps.maxFertilityBonus()),
                Math.min(mutativity, caps.maxMutativityBonus()),
                Math.max(weed, caps.minWeedMultiplier()));
    }
}
