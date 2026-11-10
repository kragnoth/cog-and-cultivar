package com.kragnoth.cultivar.api.plant;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Datapack entry for a blight/weed type. JSON path: {@code data/<namespace>/cultivar/weed/<id>.json}.
 *
 * @param lethal       mature weed can regress the host plant
 * @param aggressive   mature weed can spread to neighbouring crops
 * @param rakeProducts rolled when a <em>mature</em> weed is cleared, so blight can be cleanup material
 * @param habitat      where it can take root (tested on the trellis like plant requirements); empty = anywhere.
 *                     Spreading by an aggressive weed ignores it.
 */
public record Weed(int stages,
                   double growthChance,
                   double spawnChance,
                   boolean lethal,
                   boolean aggressive,
                   List<Product> rakeProducts,
                   List<ResourceLocation> textures,
                   List<GrowthCondition> habitat) {
    public static final Codec<Weed> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 16).optionalFieldOf("stages", 4).forGetter(Weed::stages),
            Codec.doubleRange(0, 1).optionalFieldOf("growth_chance", 0.5).forGetter(Weed::growthChance),
            Codec.doubleRange(0, 1).optionalFieldOf("spawn_chance", 0.1).forGetter(Weed::spawnChance),
            Codec.BOOL.optionalFieldOf("lethal", false).forGetter(Weed::lethal),
            Codec.BOOL.optionalFieldOf("aggressive", false).forGetter(Weed::aggressive),
            Product.CODEC.listOf().optionalFieldOf("rake_products", List.of()).forGetter(Weed::rakeProducts),
            ResourceLocation.CODEC.listOf().optionalFieldOf("textures", List.of()).forGetter(Weed::textures),
            GrowthCondition.CODEC.listOf().optionalFieldOf("habitat", List.of()).forGetter(Weed::habitat)
    ).apply(i, Weed::new));

    public Weed {
        rakeProducts = List.copyOf(rakeProducts);
        textures = List.copyOf(textures);
        habitat = List.copyOf(habitat);
    }
}
