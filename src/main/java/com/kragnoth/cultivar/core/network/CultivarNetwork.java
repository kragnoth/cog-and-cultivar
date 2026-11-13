package com.kragnoth.cultivar.core.network;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Single network channel for the mod. Modules add payloads via {@link CultivarModule#registerPayloads}.
 *
 * <p><b>Bump {@link #PROTOCOL_VERSION} whenever any payload is added, removed or changes shape.</b>
 * Mismatched versions are rejected cleanly at login instead of desyncing mid-game.
 * Prefer block-entity sync and data components over custom payloads where possible.
 */
public final class CultivarNetwork {
    public static final String PROTOCOL_VERSION = "5";

    private CultivarNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CultivarNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Cultivar.MOD_ID).versioned(PROTOCOL_VERSION);
        for (CultivarModule module : Cultivar.modules()) {
            module.registerPayloads(registrar);
        }
    }
}
