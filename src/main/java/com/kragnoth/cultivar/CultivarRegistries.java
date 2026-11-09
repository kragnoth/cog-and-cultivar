package com.kragnoth.cultivar;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central DeferredRegister holders. Content classes register into these;
 * datapack registries (plant, soil, weed, mutation, fertilizer, trellis_material)
 * get wired here via DataPackRegistryEvent.NewRegistry in Phase 1.
 */
public final class CultivarRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Cultivar.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Cultivar.MOD_ID);

    private CultivarRegistries() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);
        TABS.register(modBus);
    }
}
