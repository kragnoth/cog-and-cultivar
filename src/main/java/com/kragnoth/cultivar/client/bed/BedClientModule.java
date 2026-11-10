package com.kragnoth.cultivar.client.bed;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.ClientModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class BedClientModule implements ClientModule {
    @Override
    public String id() {
        return "bed";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        modBus.addListener((ModelEvent.ModifyBakingResult event) -> event.getModels().replaceAll((location, model) ->
                location.id().equals(Cultivar.id("planter_bed")) && !"inventory".equals(location.variant())
                        ? new PlanterBedBakedModel(model) : model));
    }
}
