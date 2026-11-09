package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.condition.GrowthConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Built-in growth condition types. Registered by {@code CoreModule}. */
public final class CoreConditions {
    public static final DeferredRegister<GrowthConditionType<?>> TYPES =
            DeferredRegister.create(CultivarRegistries.GROWTH_CONDITION_TYPE_KEY, Cultivar.MOD_ID);

    public static final DeferredHolder<GrowthConditionType<?>, GrowthConditionType<LightCondition>> LIGHT =
            TYPES.register("light", () -> new GrowthConditionType<>(LightCondition.CODEC));
    public static final DeferredHolder<GrowthConditionType<?>, GrowthConditionType<SoilConditions.Humidity>> HUMIDITY =
            TYPES.register("humidity", () -> new GrowthConditionType<>(SoilConditions.Humidity.CODEC));
    public static final DeferredHolder<GrowthConditionType<?>, GrowthConditionType<SoilConditions.Acidity>> ACIDITY =
            TYPES.register("acidity", () -> new GrowthConditionType<>(SoilConditions.Acidity.CODEC));
    public static final DeferredHolder<GrowthConditionType<?>, GrowthConditionType<SoilConditions.Nutrients>> NUTRIENTS =
            TYPES.register("nutrients", () -> new GrowthConditionType<>(SoilConditions.Nutrients.CODEC));
    public static final DeferredHolder<GrowthConditionType<?>, GrowthConditionType<BiomeCondition>> BIOME =
            TYPES.register("biome", () -> new GrowthConditionType<>(BiomeCondition.CODEC));
    public static final DeferredHolder<GrowthConditionType<?>, GrowthConditionType<DimensionCondition>> DIMENSION =
            TYPES.register("dimension", () -> new GrowthConditionType<>(DimensionCondition.CODEC));

    private CoreConditions() {}
}
