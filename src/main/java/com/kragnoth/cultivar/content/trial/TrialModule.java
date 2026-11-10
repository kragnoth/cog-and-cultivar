package com.kragnoth.cultivar.content.trial;

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

/** Trial Bed: a plot that cross-tests stocked seeds over time and fills a Ledger with the mutations it finds. */
public final class TrialModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);

    public static final DeferredBlock<TrialBedBlock> TRIAL_BED = BLOCKS.register("trial_bed",
            () -> new TrialBedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.5f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> TRIAL_BED_ITEM = ITEMS.registerSimpleBlockItem(TRIAL_BED);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrialBedBlockEntity>> TRIAL_BED_BE =
            BLOCK_ENTITIES.register("trial_bed", () -> BlockEntityType.Builder.of(TrialBedBlockEntity::new, TRIAL_BED.get()).build(null));

    @Override
    public String id() {
        return "trial";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TRIAL_BED_BE.get(),
                (be, side) -> new com.kragnoth.cultivar.content.bed.PlotItemHandler(
                        () -> be.plot().stream().map(b -> (net.neoforged.neoforge.items.IItemHandler) b.automation).toList())));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreModule.TAB.getKey()) {
                e.accept(TRIAL_BED_ITEM.get());
            }
        });
    }
}
