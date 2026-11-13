package com.kragnoth.cultivar.api;

import com.kragnoth.cultivar.api.condition.GrowthConditionType;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.api.influence.CropInfluence;
import com.kragnoth.cultivar.api.influence.InfluenceScalingType;
import com.kragnoth.cultivar.api.modifier.PlantModifierType;
import com.kragnoth.cultivar.api.plant.Fertilizer;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.api.soil.SoilConditioner;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Every Cultivar registry in one place.
 *
 * <p><b>Static registries</b> (code-defined, synced to clients, fixed at startup) hold <i>types</i>:
 * stats, growth-condition types, plant-modifier types. Addons register into them with a DeferredRegister.
 *
 * <p><b>Datapack registries</b> (JSON under {@code data/<namespace>/cultivar/<registry>/}) hold <i>content</i>:
 * plants, soils, mutations, weeds, fertilizers, soil conditioners. They reload with datapacks, are synced to clients on join,
 * and can be added or overridden by modpacks without code.
 */
public final class CultivarRegistries {
    public static final String NAMESPACE = "cultivar";

    // ---- static registry keys ----
    public static final ResourceKey<Registry<Stat>> STAT_KEY = key("stat");
    public static final ResourceKey<Registry<GrowthConditionType<?>>> GROWTH_CONDITION_TYPE_KEY = key("growth_condition_type");
    public static final ResourceKey<Registry<PlantModifierType<?>>> PLANT_MODIFIER_TYPE_KEY = key("plant_modifier_type");
    public static final ResourceKey<Registry<InfluenceScalingType<?>>> INFLUENCE_SCALING_TYPE_KEY = key("influence_scaling_type");

    // ---- datapack registry keys (registration order matters: referenced registries first) ----
    public static final ResourceKey<Registry<Soil>> SOIL = key("soil");
    public static final ResourceKey<Registry<Plant>> PLANT = key("plant");
    public static final ResourceKey<Registry<Mutation>> MUTATION = key("mutation");
    public static final ResourceKey<Registry<Weed>> WEED = key("weed");
    public static final ResourceKey<Registry<Fertilizer>> FERTILIZER = key("fertilizer");
    public static final ResourceKey<Registry<CropInfluence>> CROP_INFLUENCE = key("crop_influence");
    public static final ResourceKey<Registry<SoilConditioner>> SOIL_CONDITIONER = key("soil_conditioner");

    // ---- static registry instances ----
    public static final Registry<Stat> STAT = new RegistryBuilder<>(STAT_KEY).sync(true).create();
    public static final Registry<GrowthConditionType<?>> GROWTH_CONDITION_TYPE =
            new RegistryBuilder<>(GROWTH_CONDITION_TYPE_KEY).sync(true).create();
    public static final Registry<PlantModifierType<?>> PLANT_MODIFIER_TYPE =
            new RegistryBuilder<>(PLANT_MODIFIER_TYPE_KEY).sync(true).create();
    public static final Registry<InfluenceScalingType<?>> INFLUENCE_SCALING_TYPE =
            new RegistryBuilder<>(INFLUENCE_SCALING_TYPE_KEY).sync(true).create();

    private CultivarRegistries() {}

    private static <T> ResourceKey<Registry<T>> key(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(NAMESPACE, path));
    }
}
