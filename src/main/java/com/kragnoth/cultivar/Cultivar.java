package com.kragnoth.cultivar;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Cultivar.MOD_ID)
public final class Cultivar {
    public static final String MOD_ID = "cultivar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Cultivar(IEventBus modBus, ModContainer container) {
        CultivarRegistries.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
