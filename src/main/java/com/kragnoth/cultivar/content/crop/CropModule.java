package com.kragnoth.cultivar.content.crop;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Trellis frames, the crop block entity and the genome-carrying seed item. */
public final class CropModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);

    public static final DeferredBlock<TrellisBlock> TIMBER_TRELLIS = trellis("timber_trellis", TrellisTier.TIMBER, SoundType.SCAFFOLDING);
    public static final DeferredBlock<TrellisBlock> IRON_TRELLIS = trellis("iron_trellis", TrellisTier.IRON, SoundType.CHAIN);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CropBlockEntity>> CROP_BE =
            BLOCK_ENTITIES.register("crop", () -> BlockEntityType.Builder.of(CropBlockEntity::new,
                    TIMBER_TRELLIS.get(), IRON_TRELLIS.get()).build(null));

    public static final DeferredItem<CultivarSeedItem> SEED = ITEMS.registerItem("seed", CultivarSeedItem::new);

    private static DeferredBlock<TrellisBlock> trellis(String name, TrellisTier tier, SoundType sound) {
        DeferredBlock<TrellisBlock> block = BLOCKS.register(name, () -> new TrellisBlock(tier, BlockBehaviour.Properties.of()
                .noCollission().noOcclusion().instabreak().sound(sound).pushReaction(PushReaction.DESTROY)
                .randomTicks()));
        Item.Properties itemProps = tier.fireproof() ? new Item.Properties().fireResistant() : new Item.Properties();
        ITEMS.register(name, () -> new BlockItem(block.get(), itemProps));
        return block;
    }

    @Override
    public String id() {
        return "crop";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(CropModule::fillTab);
    }

    private static void fillTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CoreModule.TAB.getKey()) {
            return;
        }
        event.accept(TIMBER_TRELLIS.get());
        event.accept(IRON_TRELLIS.get());
        event.getParameters().holders().lookup(CultivarRegistries.PLANT).ifPresent(plants ->
                plants.listElements().forEach(plant -> event.accept(CultivarSeedItem.of(Genome.base(plant)),
                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)));
    }
}
