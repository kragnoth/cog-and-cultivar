package com.kragnoth.cultivar.api.soil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/**
 * Datapack entry: items that amend the soil they're used on, shifting its properties a step at a time.
 * JSON path: {@code data/<namespace>/cultivar/soil_conditioner/<id>.json}.
 *
 * <p>Each use adds its steps to the soil block's amendment (stored on that block position, so it outlives trellises).
 * A property's total amendment is capped at the server's {@code soil.conditionerMaxSteps}; opposite conditioners
 * cancel (lime +1 acidity, sulfur -1). Positive acidity is towards alkaline, positive nutrients richer,
 * positive humidity wetter.
 */
public record SoilConditioner(HolderSet<Item> items, int acidity, int nutrients, int humidity) {
    private static final Codec<Integer> STEP = Codec.intRange(-3, 3);

    public static final Codec<SoilConditioner> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(SoilConditioner::items),
            STEP.optionalFieldOf("acidity", 0).forGetter(SoilConditioner::acidity),
            STEP.optionalFieldOf("nutrients", 0).forGetter(SoilConditioner::nutrients),
            STEP.optionalFieldOf("humidity", 0).forGetter(SoilConditioner::humidity)
    ).apply(i, SoilConditioner::new));
}
