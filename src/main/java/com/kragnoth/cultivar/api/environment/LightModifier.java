package com.kragnoth.cultivar.api.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * Changes the light a crop reads, without changing world light (a Shade Cloche caps it, a Lamp Cloche raises it).
 * Registered with {@link CultivarEnvironment}; applied in registration order to the raw brightness at the crop.
 */
@FunctionalInterface
public interface LightModifier {
    int adjust(BlockGetter level, BlockPos cropPos, int light);
}
