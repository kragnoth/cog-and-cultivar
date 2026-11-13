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

/** Water: the Olla (early, keeps farmland moist without a water block) and the Sprinkler (any NeoForge fluid pipe feeds it). */
public final class IrrigationModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);

    public static final DeferredBlock<SprinklerBlock> SPRINKLER = BLOCKS.register("sprinkler",
            () -> new SprinklerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.5f).sound(SoundType.COPPER).noOcclusion()));
    public static final DeferredItem<BlockItem> SPRINKLER_ITEM = ITEMS.registerSimpleBlockItem(SPRINKLER);

    /** Early, cheap: keeps farmland moist without a water block (see {@link OllaBlock}). */
    public static final DeferredBlock<OllaBlock> OLLA = BLOCKS.register("olla",
            () -> new OllaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                    .strength(1.25f, 4.2f).sound(SoundType.DECORATED_POT).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> OLLA_ITEM = ITEMS.registerSimpleBlockItem(OLLA);

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
        com.kragnoth.cultivar.api.environment.CultivarEnvironment.addMoistureSource(OllaBlock::moistens);
        // Fluid input from every side: Create pipes/pumps, Pipez, Modern Dynamics and tanks all use this capability.
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK, SPRINKLER_BE.get(), (be, side) -> be.tank));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreModule.TAB.getKey()) {
                e.accept(OLLA_ITEM.get());
                e.accept(SPRINKLER_ITEM.get());
            }
        });
    }
}
