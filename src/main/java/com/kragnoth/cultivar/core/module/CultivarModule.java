package com.kragnoth.cultivar.core.module;

import com.kragnoth.cultivar.core.datagen.DataBootstraps;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * A self-contained feature slice (trellises, irrigation, conservatory, a compat integration...).
 *
 * <p>Each module owns its own DeferredRegisters, payloads and datagen. To add one: create a class
 * implementing this interface and add it to {@link CultivarModules#ALL}. That one line is the only shared
 * file a feature PR has to touch.
 *
 * <p>Rules:
 * <ul>
 *   <li>Common code only. Client hooks go in a {@code client} package via {@code ClientModule}.</li>
 *   <li>Module classes must not import optional-mod classes directly. Keep those in separate classes that
 *       are only touched after {@link #shouldLoad()} returns true.</li>
 *   <li>Always register the module's blocks and items, even when its gameplay is disabled by config. A server
 *       and client with different registries can't connect; toggle behaviour with server config instead.</li>
 * </ul>
 */
public interface CultivarModule {
    /** Short unique id, used in logs. */
    String id();

    /** Return false to skip this module entirely (e.g. an optional mod isn't installed). Evaluated once at startup. */
    default boolean shouldLoad() {
        return true;
    }

    /** Called from the mod constructor: register DeferredRegisters and mod-bus listeners here. */
    default void onConstruct(IEventBus modBus, ModContainer container) {}

    /** Register network payloads. The registrar is already versioned with the mod-wide protocol. */
    default void registerPayloads(PayloadRegistrar registrar) {}

    /**
     * Contribute subcommands to the shared root command (named after the mod id). Add your own literal with its own
     * permission {@code requires}; the root itself carries none. Called once per command registration.
     */
    default void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root) {}

    /** Datagen only: contribute datapack-registry entries (plants, soils, mutations...). */
    default void bootstrapData(DataBootstraps data) {}
}
