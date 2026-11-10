package com.kragnoth.cultivar.api.soil;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A block that holds a soil for the crop above it (the Planter Bed). Crops resolve their soil through this, so a
 * bed of mud is flooded soil, a bed of sand is arid, etc. Addon containers can implement it too.
 */
public interface SoilHost {
    /** The soil block this host holds, or null if it's empty. */
    @Nullable
    BlockState hostedSoil(BlockGetter level, BlockPos pos);

    /** Whether the host currently supplies water to its soil (one humidity step wetter, like water beside soil). */
    default boolean suppliesWater(BlockGetter level, BlockPos pos) {
        return false;
    }
}
