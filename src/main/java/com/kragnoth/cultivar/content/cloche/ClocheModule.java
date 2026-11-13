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
 * Bell Cloche (spec §3.2): fitted over a trellis as a blockstate ({@code cloche=true}, {@code cloche_type}). Its
 * effect is the {@code bell_cloche} crop_influence entry, matched by that property, so packs can retune it in JSON.
 * Shade and Lamp Cloches are Bell Cloches that also override the light the crop reads (see {@code CropLight}).
 */
public final class ClocheModule implements CultivarModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);

    public static final DeferredItem<BellClocheItem> BELL_CLOCHE = ITEMS.registerItem("bell_cloche",
            p -> new BellClocheItem(ClocheType.BELL, p), new Item.Properties().stacksTo(16));
    /** Smoked glass on blackened steel: the crop beneath reads light no brighter than {@code cloche.shadeMaxLight}. */
    public static final DeferredItem<BellClocheItem> SHADE_CLOCHE = ITEMS.registerItem("shade_cloche",
            p -> new BellClocheItem(ClocheType.SHADE, p), new Item.Properties().stacksTo(16));
    /** Amber glass round a lamp: the crop beneath reads light at least {@code cloche.lampMinLight}. Lights nothing else. */
    public static final DeferredItem<BellClocheItem> LAMP_CLOCHE = ITEMS.registerItem("lamp_cloche",
            p -> new BellClocheItem(ClocheType.LAMP, p), new Item.Properties().stacksTo(16));

    @Override
    public String id() {
        return "cloche";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        ClocheEnvironment.register();
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CoreModule.TAB.getKey()) {
                event.accept(BELL_CLOCHE.get());
                event.accept(SHADE_CLOCHE.get());
                event.accept(LAMP_CLOCHE.get());
            }
        });
    }
}
