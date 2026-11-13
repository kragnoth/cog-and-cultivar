package com.kragnoth.cultivar.api.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * Keeps vanilla farmland moist as if water were within its usual 4-block reach. Consulted by a mixin on
 * {@code FarmBlock#isNearWater}, so moisture, trampling-to-dirt and every mod that reads MOISTURE agree.
 * Planned users: irrigation channels, mulch, early-game wetting blocks. Keep it cheap: farmland checks often.
 */
@FunctionalInterface
public interface MoistureSource {
    boolean keepsMoist(LevelReader level, BlockPos farmland);
}
