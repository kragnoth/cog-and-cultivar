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
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import java.util.List;

/** Assayers (spec §3.12): andesite, brass and chromatic tiers of one horizontal-crafter machine. */
public final class AssayModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);

    public static final DeferredBlock<AssayingEngineBlock> ENGINE = tier("assaying_engine", AssayTier.ANDESITE, MapColor.STONE);
    public static final DeferredBlock<AssayingEngineBlock> BRASS_ENGINE = tier("brass_assaying_engine", AssayTier.BRASS, MapColor.TERRACOTTA_YELLOW);
    public static final DeferredBlock<AssayingEngineBlock> CHROMATIC_ENGINE = tier("chromatic_assaying_engine", AssayTier.CHROMATIC, MapColor.COLOR_PURPLE);
    public static final DeferredItem<BlockItem> ENGINE_ITEM = ITEMS.registerSimpleBlockItem(ENGINE);
    public static final DeferredItem<BlockItem> BRASS_ENGINE_ITEM = ITEMS.registerSimpleBlockItem(BRASS_ENGINE);
    public static final DeferredItem<BlockItem> CHROMATIC_ENGINE_ITEM = ITEMS.registerSimpleBlockItem(CHROMATIC_ENGINE);

    /** All tiers share one block entity type. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssayingEngineBlockEntity>> ENGINE_BE =
            BLOCK_ENTITIES.register("assaying_engine", () -> BlockEntityType.Builder.of(AssayingEngineBlockEntity::new,
                    ENGINE.get(), BRASS_ENGINE.get(), CHROMATIC_ENGINE.get()).build(null));

    private static DeferredBlock<AssayingEngineBlock> tier(String id, AssayTier tier, MapColor colour) {
        return BLOCKS.register(id, () -> new AssayingEngineBlock(BlockBehaviour.Properties.of().mapColor(colour)
                .strength(2.0f).sound(SoundType.METAL).noOcclusion(), tier));
    }

    /** Every tier, lowest first. */
    public static List<DeferredBlock<AssayingEngineBlock>> tiers() {
        return List.of(ENGINE, BRASS_ENGINE, CHROMATIC_ENGINE);
    }

    /** Stress impact per RPM from config ({@code assayer.stressImpact}, default 3). */
    private static double stressImpact() {
        return CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.ASSAY_STRESS.get() : 3.0;
    }

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
                tiers().forEach(b -> BlockStressValues.IMPACTS.register(b.get(), AssayModule::stressImpact))));
        // One handler per unit: a pipe or funnel on any member feeds and empties them all.
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, ENGINE_BE.get(), (be, side) -> be.unitHandler()));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreModule.TAB.getKey()) {
                tiers().forEach(b -> e.accept(b.get()));
            }
        });
    }
}
