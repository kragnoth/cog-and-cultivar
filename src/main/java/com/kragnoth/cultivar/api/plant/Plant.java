package com.kragnoth.cultivar.api.plant;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.modifier.PlantModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Datapack entry describing a species. JSON path: {@code data/<namespace>/cultivar/plant/<id>.json}.
 * Only {@code stages} is required; everything else has a default so new fields never break old packs.
 *
 * @param stages            number of growth stages (last stage = mature)
 * @param afterHarvestStage stage the plant returns to after harvest (perennials)
 * @param growthChance      base chance per growth tick to advance a stage
 * @param growthBonus       added to growthChance per point of the growth stat
 * @param cloneable         whether a single mature neighbour can spread a copy onto a grafting frame
 * @param spreadChance      chance per eligible tick for that clone to happen
 */
public record Plant(int stages,
                    int afterHarvestStage,
                    double growthChance,
                    double growthBonus,
                    boolean cloneable,
                    double spreadChance,
                    List<Product> products,
                    List<Product> clipProducts,
                    List<GrowthCondition> requirements,
                    List<PlantModifier> modifiers) {
    public static final Codec<Plant> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 16).fieldOf("stages").forGetter(Plant::stages),
            Codec.intRange(0, 15).optionalFieldOf("after_harvest_stage", 0).forGetter(Plant::afterHarvestStage),
            Codec.doubleRange(0, 1).optionalFieldOf("growth_chance", 0.75).forGetter(Plant::growthChance),
            Codec.doubleRange(0, 1).optionalFieldOf("growth_bonus", 0.025).forGetter(Plant::growthBonus),
            Codec.BOOL.optionalFieldOf("cloneable", true).forGetter(Plant::cloneable),
            Codec.doubleRange(0, 1).optionalFieldOf("spread_chance", 0.1).forGetter(Plant::spreadChance),
            Product.CODEC.listOf().optionalFieldOf("products", List.of()).forGetter(Plant::products),
            Product.CODEC.listOf().optionalFieldOf("clip_products", List.of()).forGetter(Plant::clipProducts),
            GrowthCondition.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(Plant::requirements),
            PlantModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Plant::modifiers)
    ).apply(i, Plant::new));

    public Plant {
        products = List.copyOf(products);
        clipProducts = List.copyOf(clipProducts);
        requirements = List.copyOf(requirements);
        modifiers = List.copyOf(modifiers);
    }

    public int matureStage() {
        return stages - 1;
    }
}
