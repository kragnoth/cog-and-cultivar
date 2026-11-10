package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Botanical Ledger (spec §3.12). */
public final class LedgerModule implements CultivarModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Cultivar.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LedgerItem.Data>> LEDGER_DATA =
            COMPONENTS.registerComponentType("ledger", b -> b.persistent(LedgerItem.Data.CODEC)
                    .networkSynchronized(LedgerItem.Data.STREAM_CODEC));
    public static final DeferredItem<LedgerItem> LEDGER = ITEMS.registerItem("botanical_ledger",
            LedgerItem::new, new Item.Properties().stacksTo(1));

    @Override
    public String id() {
        return "ledger";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CoreModule.TAB.getKey()) {
                event.accept(LEDGER.get());
            }
        });
    }
}
