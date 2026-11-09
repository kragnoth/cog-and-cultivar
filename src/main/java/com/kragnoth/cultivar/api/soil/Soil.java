package com.kragnoth.cultivar.api.soil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

/**
 * Datapack entry: which blocks count as this soil and their base properties.
 * JSON path: {@code data/<namespace>/cultivar/soil/<id>.json}.
 */
public record Soil(HolderSet<Block> blocks,
                   SoilLevels.Humidity humidity,
                   SoilLevels.Acidity acidity,
                   SoilLevels.Nutrients nutrients,
                   float growthModifier) {
    public static final Codec<Soil> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks").forGetter(Soil::blocks),
            SoilLevels.Humidity.CODEC.optionalFieldOf("humidity", SoilLevels.Humidity.WET).forGetter(Soil::humidity),
            SoilLevels.Acidity.CODEC.optionalFieldOf("acidity", SoilLevels.Acidity.SLIGHTLY_ACIDIC).forGetter(Soil::acidity),
            SoilLevels.Nutrients.CODEC.optionalFieldOf("nutrients", SoilLevels.Nutrients.HIGH).forGetter(Soil::nutrients),
            Codec.floatRange(0f, 16f).optionalFieldOf("growth_modifier", 1f).forGetter(Soil::growthModifier)
    ).apply(i, Soil::new));
}
