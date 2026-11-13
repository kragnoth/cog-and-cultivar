package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.api.soil.SoilConditioner;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FarmBlock;

/**
 * Which soils a plant's soil requirements accept, without a world: the soil as-is, or shifted the ways
 * {@link Hydration} allows (heat -1; water beside +1 except on farmland, whose moisture already counts it; a Bell
 * Cloche +1), and optionally by soil conditioners ({@link Reach}). Used by JEI's Growing page and tests. Light isn't a soil property, so it's ignored here.
 */
public final class SoilMatch {
    public enum Fit { AS_IS, WITH_SHIFT, NO }

    private SoilMatch() {}

    /**
     * How far soil conditioners can move each property, in steps down / up (0 where no conditioner pushes that way).
     * {@link #NONE} matches the world without conditioners.
     */
    public record Reach(int acidDown, int acidUp, int nutDown, int nutUp, int humDown, int humUp) {
        public static final Reach NONE = new Reach(0, 0, 0, 0, 0, 0);

        public static Reach of(Iterable<SoilConditioner> conditioners, int maxSteps) {
            int ad = 0, au = 0, nd = 0, nu = 0, hd = 0, hu = 0;
            for (SoilConditioner c : conditioners) {
                ad |= c.acidity() < 0 ? 1 : 0;
                au |= c.acidity() > 0 ? 1 : 0;
                nd |= c.nutrients() < 0 ? 1 : 0;
                nu |= c.nutrients() > 0 ? 1 : 0;
                hd |= c.humidity() < 0 ? 1 : 0;
                hu |= c.humidity() > 0 ? 1 : 0;
            }
            return new Reach(ad * maxSteps, au * maxSteps, nd * maxSteps, nu * maxSteps, hd * maxSteps, hu * maxSteps);
        }
    }

    public static Fit fit(List<GrowthCondition> requirements, Soil soil, int strength) {
        return fit(requirements, soil, strength, Reach.NONE);
    }

    /** As {@link #fit(List, Soil, int)}, also counting soils that conditioners within {@code reach} make fit. */
    public static Fit fit(List<GrowthCondition> requirements, Soil soil, int strength, Reach reach) {
        List<GrowthCondition> soilNeeds = requirements.stream().filter(GrowthCondition::isSoilProperty).toList();
        if (accepts(soilNeeds, soil, soil.humidity(), soil.acidity(), soil.nutrients(), strength)) {
            return Fit.AS_IS;
        }
        SoilLevels.Humidity[] hums = SoilLevels.Humidity.values();
        SoilLevels.Acidity[] acids = SoilLevels.Acidity.values();
        SoilLevels.Nutrients[] nuts = SoilLevels.Nutrients.values();
        int top = isFarmland(soil) ? 1 : 2;
        for (int h = -1 - reach.humDown(); h <= top + reach.humUp(); h++) {
            for (int a = -reach.acidDown(); a <= reach.acidUp(); a++) {
                for (int n = -reach.nutDown(); n <= reach.nutUp(); n++) {
                    int hi = soil.humidity().ordinal() + h, ai = soil.acidity().ordinal() + a, ni = soil.nutrients().ordinal() + n;
                    if ((h | a | n) == 0 || hi < 0 || hi >= hums.length || ai < 0 || ai >= acids.length || ni < 0 || ni >= nuts.length) {
                        continue;
                    }
                    if (accepts(soilNeeds, soil, hums[hi], acids[ai], nuts[ni], strength)) {
                        return Fit.WITH_SHIFT;
                    }
                }
            }
        }
        return Fit.NO;
    }

    public static boolean isFarmland(Soil soil) {
        return soil.blocks().stream().anyMatch(b -> b.value().defaultBlockState().hasProperty(FarmBlock.MOISTURE));
    }

    private static boolean accepts(List<GrowthCondition> needs, Soil soil, SoilLevels.Humidity humidity,
                                   SoilLevels.Acidity acidity, SoilLevels.Nutrients nutrients, int strength) {
        GrowthContext ctx = new GrowthContext(null, BlockPos.ZERO, Optional.of(soil), Optional.of(humidity),
                Optional.of(acidity), Optional.of(nutrients));
        return GrowthCondition.testAll(needs, ctx, strength) == GrowthResponse.FERTILE;
    }
}
