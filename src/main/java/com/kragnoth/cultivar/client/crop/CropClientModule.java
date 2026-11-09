package com.kragnoth.cultivar.client.crop;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.ClientModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Wraps every trellis blockstate model with {@link CropBakedModel}. */
public final class CropClientModule implements ClientModule {
    @Override
    public String id() {
        return "crop";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(CropClientModule::wrapModels);
    }

    private static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            var id = location.id();
            boolean trellisBlockState = id.getNamespace().equals(Cultivar.MOD_ID)
                    && id.getPath().endsWith("_trellis")
                    && !"inventory".equals(location.variant());
            return trellisBlockState ? new CropBakedModel(model) : model;
        });
    }
}
