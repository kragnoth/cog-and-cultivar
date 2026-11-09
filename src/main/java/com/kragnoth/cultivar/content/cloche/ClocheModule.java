package com.kragnoth.cultivar.content.cloche;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Bell Cloche (spec §3.2): a one-crop greenhouse built purely on the crop-influence system. */
public final class ClocheModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);

    public static final DeferredBlock<BellClocheBlock> BELL_CLOCHE = BLOCKS.register("bell_cloche",
            () -> new BellClocheBlock(BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS)
                    .noOcclusion().isViewBlocking((s, l, p) -> false)));

    static {
        ITEMS.register("bell_cloche", () -> new BlockItem(BELL_CLOCHE.get(), new Item.Properties()));
    }

    @Override
    public String id() {
        return "cloche";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CoreModule.TAB.getKey()) {
                event.accept(BELL_CLOCHE.get());
            }
        });
    }
}
