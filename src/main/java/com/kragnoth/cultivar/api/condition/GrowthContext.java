package com.kragnoth.cultivar.api.condition;

import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Everything a {@link GrowthCondition} may look at. Built server-side by the crop when (re)evaluating
 * its requirements. Effective soil values are separate from the base {@link Soil} so soil conditioning
 * (Phase 5) can shift them without changing condition code.
 *
 * @param soil the soil under the crop, if the block below is a registered soil
 */
public record GrowthContext(Level level,
                            BlockPos pos,
                            Optional<Soil> soil,
                            Optional<SoilLevels.Humidity> humidity,
                            Optional<SoilLevels.Acidity> acidity,
                            Optional<SoilLevels.Nutrients> nutrients) {

    /** Context whose effective soil values are just the base soil's. */
    public static GrowthContext of(Level level, BlockPos pos, Optional<Soil> soil) {
        return new GrowthContext(level, pos, soil,
                soil.map(Soil::humidity), soil.map(Soil::acidity), soil.map(Soil::nutrients));
    }
}
