package com.kragnoth.cultivar.api.plant;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.modifier.PlantModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

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
 * @param seeds             vanilla/modded items that plant this species on a trellis (converted to a base genome)
 * @param render            client display: block-atlas textures spread across the stages, and the plant shape
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
                    List<PlantModifier> modifiers,
                    Optional<HolderSet<Item>> seeds,
                    Optional<PlantRender> render,
                    int height) {
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
            PlantModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Plant::modifiers),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("seeds").forGetter(Plant::seeds),
            PlantRender.CODEC.optionalFieldOf("render").forGetter(Plant::render),
            Codec.intRange(1, 4).optionalFieldOf("height", 1).forGetter(Plant::height)
    ).apply(i, Plant::new));

    public Plant {
        products = List.copyOf(products);
        clipProducts = List.copyOf(clipProducts);
        requirements = List.copyOf(requirements);
        modifiers = List.copyOf(modifiers);
    }

    /**
     * Trellis segments the plant occupies after growing through {@code stage} (1 for normal plants).
     * Tall plants fill their {@code height} evenly across their stages.
     */
    public int blocksAt(int stage) {
        return height <= 1 ? 1 : (int) Math.ceil((stage + 1) * height / (double) stages);
    }

    public int matureStage() {
        return stages - 1;
    }
}
