package com.kragnoth.cultivar.api.modifier;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.mojang.serialization.Codec;

/**
 * A behaviour attached to a plant (thorns, light emission, redstone when mature...).
 * Serialised as {@code {"type": "<modifier type id>", ...type-specific fields}}.
 * Hook methods are added as the systems that call them land; all of them will be {@code default} no-ops.
 */
public interface PlantModifier {
    Codec<PlantModifier> CODEC = CultivarRegistries.PLANT_MODIFIER_TYPE.byNameCodec()
            .dispatch(PlantModifier::type, PlantModifierType::codec);

    PlantModifierType<?> type();
}
