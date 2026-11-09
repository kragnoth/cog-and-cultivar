package com.kragnoth.cultivar.content.core.growth;

import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.api.plant.Plant;

/** Growth, weed and harvest odds (spec §2.3, §2.6, §2.7). Pure functions; callers supply the random roll. */
public final class GrowthMath {
    private GrowthMath() {}

    /**
     * Chance this tick advances the plant one stage:
     * {@code soil × (base + growth × bonus × globalMultiplier) × influence}, clamped to [0, 1].
     */
    public static double growthChance(Plant plant, int growthStat, double soilModifier, double globalMultiplier,
                                      InfluenceEffects influence) {
        double chance = soilModifier * (plant.growthChance() + growthStat * plant.growthBonus() * globalMultiplier)
                * influence.growthMultiplier();
        return Math.max(0, Math.min(1, chance));
    }

    /**
     * Chance a growth tick goes to weeds instead of the plant. With a plant present this is
     * {@code (max - floor((max + resistance) / 2)) / max} (resistance 1 of 10 = 50 %, 10 of 10 = 0 %);
     * empty frames are 50 %. Multiplied by the weed influence (bees suppress).
     */
    public static double weedChance(boolean hasPlant, int resistance, int resistanceMax, InfluenceEffects influence) {
        double base = hasPlant
                ? (double) (resistanceMax - (resistanceMax + resistance) / 2) / resistanceMax
                : 0.5;
        return Math.max(0, Math.min(1, base * influence.weedMultiplier()));
    }

    /** Product rolls per harvest: {@code floor((gain + 3) / 3)}, so gain 1→1, 3→2, 6→3, 9→4. */
    public static int harvestRolls(int gain) {
        return Math.max(1, (gain + 3) / 3);
    }
}
