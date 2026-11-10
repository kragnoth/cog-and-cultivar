package com.kragnoth.cultivar.client.loupe;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.ClientModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class LoupeClientModule implements ClientModule {
    @Override
    public String id() {
        return "loupe";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.CROSSHAIR, Cultivar.id("loupe"), LoupeOverlay::render));
    }
}
