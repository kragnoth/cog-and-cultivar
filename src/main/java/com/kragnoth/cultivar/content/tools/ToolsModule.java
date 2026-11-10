package com.kragnoth.cultivar.content.tools;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Phase 2a hand tools: Transplanting Spade, Pruning Shears, Hand Tillers, Seed Satchel and Brass Dial. */
public final class ToolsModule implements CultivarModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Cultivar.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TransplantingSpadeItem.Held>> SPADE_PLANT =
            COMPONENTS.registerComponentType("spade_plant", b -> b.persistent(TransplantingSpadeItem.Held.CODEC)
                    .networkSynchronized(TransplantingSpadeItem.Held.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SeedSatchelItem.Contents>> SATCHEL_CONTENTS =
            COMPONENTS.registerComponentType("satchel_contents", b -> b.persistent(SeedSatchelItem.Contents.CODEC)
                    .networkSynchronized(SeedSatchelItem.Contents.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SeedSatchelItem.Dial>> SATCHEL_DIAL =
            COMPONENTS.registerComponentType("satchel_dial", b -> b.persistent(SeedSatchelItem.Dial.CODEC)
                    .networkSynchronized(SeedSatchelItem.Dial.STREAM_CODEC));

    public static final DeferredItem<TransplantingSpadeItem> TRANSPLANTING_SPADE = ITEMS.registerItem("transplanting_spade",
            TransplantingSpadeItem::new, new Item.Properties().durability(250));
    public static final DeferredItem<PruningShearsItem> PRUNING_SHEARS = ITEMS.registerItem("pruning_shears",
            PruningShearsItem::new, new Item.Properties().durability(238));
    public static final DeferredItem<HandTillerItem> TIMBER_TILLER = ITEMS.registerItem("timber_tiller",
            p -> new HandTillerItem(false, p), new Item.Properties().durability(59));
    public static final DeferredItem<HandTillerItem> IRON_TILLER = ITEMS.registerItem("iron_tiller",
            p -> new HandTillerItem(true, p), new Item.Properties().durability(250));
    public static final DeferredItem<SeedSatchelItem> SEED_SATCHEL = ITEMS.registerItem("seed_satchel",
            SeedSatchelItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BRASS_DIAL = ITEMS.registerSimpleItem("brass_dial");
    /** Held in either hand, shows the crop readout (client overlay). Create goggles show the same readout. */
    public static final DeferredItem<Item> BOTANISTS_LOUPE = ITEMS.registerItem("botanists_loupe",
            Item::new, new Item.Properties().stacksTo(1));

    @Override
    public String id() {
        return "tools";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(SeedSatchelItem::onItemPickup);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CoreModule.TAB.getKey()) {
                ITEMS.getEntries().forEach(item -> event.accept(item.get()));
            }
        });
    }
}
