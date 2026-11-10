package com.kragnoth.cultivar.content.irrigation;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Phase 4, water: the Sprinkler. Any pipe mod using NeoForge's fluid capability can feed it. */
public final class IrrigationModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);

    public static final DeferredBlock<SprinklerBlock> SPRINKLER = BLOCKS.register("sprinkler",
            () -> new SprinklerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.5f).sound(SoundType.COPPER).noOcclusion()));
    public static final DeferredItem<BlockItem> SPRINKLER_ITEM = ITEMS.registerSimpleBlockItem(SPRINKLER);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SprinklerBlockEntity>> SPRINKLER_BE =
            BLOCK_ENTITIES.register("sprinkler", () -> BlockEntityType.Builder.of(SprinklerBlockEntity::new, SPRINKLER.get()).build(null));

    @Override
    public String id() {
        return "irrigation";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        // Fluid input from every side: Create pipes/pumps, Pipez, Modern Dynamics and tanks all use this capability.
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK, SPRINKLER_BE.get(), (be, side) -> be.tank));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreModule.TAB.getKey()) {
                e.accept(SPRINKLER_ITEM.get());
            }
        });
    }
}
