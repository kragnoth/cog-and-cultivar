package com.kragnoth.cultivar.api.influence;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How strongly a source currently applies, from 0 (off) to 1 (full). For example: a beehive scales by
 * the number of bees inside, and a powered apiary by its RPM. Serialised as {@code {"type": "<id>", ...}}.
 */
public interface InfluenceScaling {
    Codec<InfluenceScaling> CODEC = CultivarRegistries.INFLUENCE_SCALING_TYPE.byNameCodec()
            .dispatch(InfluenceScaling::type, InfluenceScalingType::codec);

    InfluenceScalingType<?> type();

    /** Current strength of the source at {@code pos}, 0..1. Server-side; must be cheap (no chunk loads). */
    double scale(ServerLevel level, BlockPos pos, BlockState state);
}
