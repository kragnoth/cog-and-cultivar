package com.kragnoth.cultivar;

import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.kragnoth.cultivar.core.datagen.CultivarDataGen;
import com.kragnoth.cultivar.core.module.CultivarModule;
import com.kragnoth.cultivar.core.module.CultivarModules;
import com.kragnoth.cultivar.core.network.CultivarNetwork;
import com.kragnoth.cultivar.core.registry.RegistrySetup;
import com.kragnoth.cultivar.core.work.ServerWorkQueue;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Common (both sides) entry point. Client-only setup lives in {@code client.CultivarClient}. */
@Mod(Cultivar.MOD_ID)
public final class Cultivar {
    public static final String MOD_ID = "cultivar";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static List<CultivarModule> modules = List.of();

    public Cultivar(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CultivarServerConfig.SPEC);

        RegistrySetup.register(modBus);
        CultivarNetwork.register(modBus);
        CultivarDataGen.register(modBus);
        ServerWorkQueue.register(NeoForge.EVENT_BUS);

        List<CultivarModule> loaded = new ArrayList<>();
        for (Supplier<CultivarModule> factory : CultivarModules.ALL) {
            CultivarModule module = factory.get();
            if (!module.shouldLoad()) {
                LOGGER.info("Skipping module '{}'", module.id());
                continue;
            }
            module.onConstruct(modBus, container);
            loaded.add(module);
        }
        modules = List.copyOf(loaded);
        LOGGER.info("Loaded modules: {}", modules.stream().map(CultivarModule::id).toList());
    }

    /** Modules that passed {@link CultivarModule#shouldLoad()}, in index order. */
    public static List<CultivarModule> modules() {
        return modules;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
