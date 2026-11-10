package com.kragnoth.cultivar.client;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.config.CultivarClientConfig;
import com.kragnoth.cultivar.client.crop.CropClientModule;
import com.kragnoth.cultivar.client.dev.SceneShooter;
import com.kragnoth.cultivar.client.tools.ToolsClientModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-only entry point. FML never loads this class on a dedicated server, so everything under
 * {@code client} may use rendering and input classes freely. The {@code checkSidedness} build task
 * keeps those imports out of every other package.
 */
@Mod(value = Cultivar.MOD_ID, dist = Dist.CLIENT)
public final class CultivarClient {
    /** Client module index. One line per feature that has client-side code. */
    static final List<Supplier<ClientModule>> MODULES = List.of(
            CropClientModule::new,
            ToolsClientModule::new
    );

    public CultivarClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CultivarClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        SceneShooter.registerIfEnabled();

        for (Supplier<ClientModule> factory : MODULES) {
            ClientModule module = factory.get();
            if (module.shouldLoad()) {
                module.onConstructClient(modBus, container);
            }
        }
    }
}
