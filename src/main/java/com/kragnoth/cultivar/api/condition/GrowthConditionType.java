package com.kragnoth.cultivar.api.condition;

import com.mojang.serialization.MapCodec;

/** Registered in {@code CultivarRegistries.GROWTH_CONDITION_TYPE}; maps a type id to its codec. */
public record GrowthConditionType<T extends GrowthCondition>(MapCodec<T> codec) {}
