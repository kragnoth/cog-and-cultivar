package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FarmBlock;

/**
 * Which soils a plant's soil requirements accept, without a world: the soil as-is, or shifted the ways
 * {@link Hydration} allows (heat -1; water beside +1 except on farmland, whose moisture already counts it; a Bell
 * Cloche +1). Used by JEI's Growing page and tests. Light isn't a soil property, so it's ignored here.
 */
public final class SoilMatch {
    public enum Fit { AS_IS, WITH_SHIFT, NO }

    private SoilMatch() {}

    public static Fit fit(List<GrowthCondition> requirements, Soil soil, int strength) {
        List<GrowthCondition> soilNeeds = requirements.stream().filter(GrowthCondition::isSoilProperty).toList();
        if (accepts(soilNeeds, soil, soil.humidity(), strength)) {
            return Fit.AS_IS;
        }
        SoilLevels.Humidity[] all = SoilLevels.Humidity.values();
        int base = soil.humidity().ordinal();
        int top = isFarmland(soil) ? 1 : 2;
        for (int shift = -1; shift <= top; shift++) {
            int i = base + shift;
            if (shift != 0 && i >= 0 && i < all.length && accepts(soilNeeds, soil, all[i], strength)) {
                return Fit.WITH_SHIFT;
            }
        }
        return Fit.NO;
    }

    public static boolean isFarmland(Soil soil) {
        return soil.blocks().stream().anyMatch(b -> b.value().defaultBlockState().hasProperty(FarmBlock.MOISTURE));
    }

    private static boolean accepts(List<GrowthCondition> needs, Soil soil, SoilLevels.Humidity humidity, int strength) {
        GrowthContext ctx = new GrowthContext(null, BlockPos.ZERO, Optional.of(soil), Optional.of(humidity),
                Optional.of(soil.acidity()), Optional.of(soil.nutrients()));
        return GrowthCondition.testAll(needs, ctx, strength) == GrowthResponse.FERTILE;
    }
}
