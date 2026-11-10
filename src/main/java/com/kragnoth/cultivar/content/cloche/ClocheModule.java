package com.kragnoth.cultivar.content.cloche;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Bell Cloche (spec §3.2): fitted over a trellis as a blockstate ({@code cloche=true}). Its effect is the
 * {@code bell_cloche} crop_influence entry, matched by that property, so packs can retune it in JSON.
 */
public final class ClocheModule implements CultivarModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);

    public static final DeferredItem<BellClocheItem> BELL_CLOCHE = ITEMS.registerItem("bell_cloche",
            BellClocheItem::new, new Item.Properties().stacksTo(16));

    @Override
    public String id() {
        return "cloche";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CoreModule.TAB.getKey()) {
                event.accept(BELL_CLOCHE.get());
            }
        });
    }
}
