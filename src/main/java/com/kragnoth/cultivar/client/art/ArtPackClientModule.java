package com.kragnoth.cultivar.client.art;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.ClientModule;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The default art is kitbashed from vanilla textures. The Excalibur-kitbashed set ships inside the jar as a built-in
 * resource pack ({@code resourcepacks/excalibur_style}), off by default: players running Excalibur turn it on in
 * the Resource Packs screen. Both sets come from tools/art/build_art.py.
 */
public final class ArtPackClientModule implements ClientModule {
    @Override
    public String id() {
        return "art_packs";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        modBus.addListener((AddPackFindersEvent e) -> e.addPackFinders(Cultivar.id("resourcepacks/excalibur_style"),
                PackType.CLIENT_RESOURCES, Component.translatable("pack.cultivar.excalibur_style"),
                PackSource.BUILT_IN, false, Pack.Position.TOP));
    }
}
