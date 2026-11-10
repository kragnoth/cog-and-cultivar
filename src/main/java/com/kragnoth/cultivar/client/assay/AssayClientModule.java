package com.kragnoth.cultivar.client.assay;

import com.kragnoth.cultivar.client.ClientModule;
import com.kragnoth.cultivar.content.assay.AssayModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class AssayClientModule implements ClientModule {
    @Override
    public String id() {
        return "assay";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        // Touch the partial now so it is registered before the first model bake.
        AssayingEngineRenderer.DRUM.toString();
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(AssayModule.ENGINE_BE.get(), AssayingEngineRenderer::new));
    }
}
