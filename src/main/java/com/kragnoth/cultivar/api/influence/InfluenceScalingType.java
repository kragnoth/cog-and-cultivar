package com.kragnoth.cultivar.api.influence;

import com.mojang.serialization.MapCodec;

/** Registered in {@code CultivarRegistries.INFLUENCE_SCALING_TYPE}; maps a type id to its codec. */
public record InfluenceScalingType<T extends InfluenceScaling>(MapCodec<T> codec) {}
