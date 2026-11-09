package com.kragnoth.cultivar.api.modifier;

import com.mojang.serialization.MapCodec;

/** Registered in {@code CultivarRegistries.PLANT_MODIFIER_TYPE}; maps a type id to its codec. */
public record PlantModifierType<T extends PlantModifier>(MapCodec<T> codec) {}
