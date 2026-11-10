package com.kragnoth.cultivar.client.tooltip;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.ClientModule;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Create-style item descriptions ("Hold [Shift] for Summary") for every Cultivar block and item. Text lives in lang
 * as {@code <item description id>.tooltip.summary / .conditionN / .behaviourN / .controlN / .actionN}, the format
 * Create uses, so translators and pack makers edit them like any Create addon. Kinetic blocks also get Create's
 * stress readout.
 */
public final class TooltipClientModule implements ClientModule {
    @Override
    public String id() {
        return "tooltips";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            for (Item item : BuiltInRegistries.ITEM) {
                if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Cultivar.MOD_ID)) {
                    continue;
                }
                TooltipModifier modifier = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
                if (item == AssayModule.ENGINE_ITEM.get()) {
                    modifier = modifier.andThen(TooltipModifier.mapNull(KineticStats.create(item)));
                }
                TooltipModifier.REGISTRY.register(item, modifier);
            }
        }));
    }
}
