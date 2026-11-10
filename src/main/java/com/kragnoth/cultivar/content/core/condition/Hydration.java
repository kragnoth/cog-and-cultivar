package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Effective soil humidity: the soil's datapack value, one step wetter if water touches the soil block's side
 * (a channel or a waterlogged block) or a Planter Bed holds water, one step drier on unwatered farmland. Four block reads; pure function of
 * world state, so the server's growth checks and the client's goggle readout always agree.
 */
public final class Hydration {
    private Hydration() {}

    public static SoilLevels.Humidity effective(BlockGetter level, BlockPos soilPos, SoilLevels.Humidity base) {
        if (CultivarServerConfig.SPEC.isLoaded() && !CultivarServerConfig.WATER_SHIFTS_HUMIDITY.get()) {
            return base;
        }
        int shift = 0;
        BlockState host = level.getBlockState(soilPos);
        if (host.getBlock() instanceof com.kragnoth.cultivar.api.soil.SoilHost h && h.suppliesWater(level, soilPos)) {
            shift = 1;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(soilPos.relative(d)).is(FluidTags.WATER)) {
                shift = 1;
                break;
            }
        }
        BlockState soil = level.getBlockState(soilPos);
        if (shift == 0 && soil.hasProperty(FarmBlock.MOISTURE) && soil.getValue(FarmBlock.MOISTURE) == 0) {
            shift = -1;
        }
        SoilLevels.Humidity[] all = SoilLevels.Humidity.values();
        return all[Math.clamp(base.ordinal() + shift, 0, all.length - 1)];
    }
}
