package com.kragnoth.cultivar.content.assay;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Assaying Engine (spec §3.12, Phase 2c). */
public final class AssayModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);

    /** Stress impact per RPM, in Create's units (a millstone is 4). */
    public static final double STRESS_IMPACT = 2.0;

    public static final DeferredBlock<AssayingEngineBlock> ENGINE = BLOCKS.register("assaying_engine",
            () -> new AssayingEngineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> ENGINE_ITEM = ITEMS.registerSimpleBlockItem(ENGINE);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssayingEngineBlockEntity>> ENGINE_BE =
            BLOCK_ENTITIES.register("assaying_engine", () -> BlockEntityType.Builder.of(AssayingEngineBlockEntity::new,
                    ENGINE.get()).build(null));

    @Override
    public String id() {
        return "assay";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(ENGINE.get(), () -> STRESS_IMPACT)));
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, ENGINE_BE.get(), (be, side) -> be.automation));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreModule.TAB.getKey()) {
                e.accept(ENGINE_ITEM.get());
            }
        });
    }
}
