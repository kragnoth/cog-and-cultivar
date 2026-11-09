package com.kragnoth.cultivar.content.core.influence;

import com.kragnoth.cultivar.api.influence.InfluenceScaling;
import com.kragnoth.cultivar.api.influence.InfluenceScalingType;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code {"type": "cultivar:honey_level"}}: (1 + honey level) / 6, so an empty hive gives 0.17 and a full one 1.0.
 * Honey only rises while bees forage, so this tracks an active colony. It reads a blockstate, so it's free.
 */
public record HoneyLevelScaling() implements InfluenceScaling {
    public static final HoneyLevelScaling INSTANCE = new HoneyLevelScaling();
    public static final MapCodec<HoneyLevelScaling> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public InfluenceScalingType<?> type() {
        return CoreInfluence.HONEY_LEVEL.get();
    }

    @Override
    public double scale(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            return 0;
        }
        return (1 + state.getValue(BeehiveBlock.HONEY_LEVEL)) / 6.0;
    }
}
