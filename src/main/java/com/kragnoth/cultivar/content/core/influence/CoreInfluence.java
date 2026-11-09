package com.kragnoth.cultivar.content.core.influence;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.influence.InfluenceScalingType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Built-in influence scaling types. Registered by {@code CoreModule}. */
public final class CoreInfluence {
    public static final DeferredRegister<InfluenceScalingType<?>> SCALING_TYPES =
            DeferredRegister.create(CultivarRegistries.INFLUENCE_SCALING_TYPE_KEY, Cultivar.MOD_ID);

    public static final DeferredHolder<InfluenceScalingType<?>, InfluenceScalingType<HoneyLevelScaling>> HONEY_LEVEL =
            SCALING_TYPES.register("honey_level", () -> new InfluenceScalingType<>(HoneyLevelScaling.CODEC));

    private CoreInfluence() {}
}
