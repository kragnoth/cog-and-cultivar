package com.kragnoth.cultivar.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/**
 * Client-side half of a feature (renderers, model loaders, screens, overlays). Only ever loaded on
 * physical clients. Add implementations to {@link CultivarClient#MODULES}.
 */
public interface ClientModule {
    String id();

    default boolean shouldLoad() {
        return true;
    }

    void onConstructClient(IEventBus modBus, ModContainer container);
}
