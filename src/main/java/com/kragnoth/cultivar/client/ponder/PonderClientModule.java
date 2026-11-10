package com.kragnoth.cultivar.client.ponder;

import com.kragnoth.cultivar.client.ClientModule;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** Ponder scenes: how to spread, improve, crossbreed and assay crops. */
public final class PonderClientModule implements ClientModule {
    @Override
    public String id() {
        return "ponder";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        PonderIndex.addPlugin(new CultivarPonderPlugin());
    }
}
