package com.kragnoth.cultivar.core.registry;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.influence.CropInfluence;
import com.kragnoth.cultivar.api.plant.Fertilizer;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.api.soil.Soil;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Wires the registries declared in {@link CultivarRegistries} into NeoForge. */
public final class RegistrySetup {
    private RegistrySetup() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegistrySetup::onNewRegistry);
        modBus.addListener(RegistrySetup::onNewDatapackRegistry);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.register(CultivarRegistries.STAT);
        event.register(CultivarRegistries.GROWTH_CONDITION_TYPE);
        event.register(CultivarRegistries.PLANT_MODIFIER_TYPE);
        event.register(CultivarRegistries.INFLUENCE_SCALING_TYPE);
    }

    private static void onNewDatapackRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Same codec for disk and network: clients need full entries for tooltips, JEI and rendering.
        // Order matters: registries referenced by others must come first (plant before mutation).
        event.dataPackRegistry(CultivarRegistries.SOIL, Soil.CODEC, Soil.CODEC);
        event.dataPackRegistry(CultivarRegistries.PLANT, Plant.CODEC, Plant.CODEC);
        event.dataPackRegistry(CultivarRegistries.MUTATION, Mutation.CODEC, Mutation.CODEC);
        event.dataPackRegistry(CultivarRegistries.WEED, Weed.CODEC, Weed.CODEC);
        event.dataPackRegistry(CultivarRegistries.FERTILIZER, Fertilizer.CODEC, Fertilizer.CODEC);
        event.dataPackRegistry(CultivarRegistries.CROP_INFLUENCE, CropInfluence.CODEC, CropInfluence.CODEC);
    }
}
