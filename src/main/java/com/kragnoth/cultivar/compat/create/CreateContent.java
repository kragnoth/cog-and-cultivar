package com.kragnoth.cultivar.compat.create;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Create-touching registrations, kept out of {@link CreateModule} so that class has no Create imports. */
final class CreateContent {
    private CreateContent() {}

    static void register(IEventBus modBus) {
        CultivarAttributes.TYPES.register(modBus);
        CultivarDisplaySources.SOURCES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(CultivarDisplaySources::bind));
    }
}
