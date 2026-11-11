package com.kragnoth.cultivar.client.tools;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.ClientModule;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Item model properties: spade shows a sprout when holding a plant; satchel shows its fitted dial. */
public final class ToolsClientModule implements ClientModule {
    @Override
    public String id() {
        return "tools";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        com.kragnoth.cultivar.content.tools.SeedSatchelItem.openScreen =
                hand -> net.minecraft.client.Minecraft.getInstance().setScreen(new SatchelScreen(hand));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            ItemProperties.register(ToolsModule.TRANSPLANTING_SPADE.get(), Cultivar.id("holding"),
                    (stack, level, entity, seed) -> stack.has(ToolsModule.SPADE_PLANT.get()) ? 1f : 0f);
            ItemProperties.register(ToolsModule.SEED_SATCHEL.get(), Cultivar.id("dial"),
                    (stack, level, entity, seed) -> stack.has(ToolsModule.SATCHEL_DIAL.get()) ? 1f : 0f);
        }));
    }
}
