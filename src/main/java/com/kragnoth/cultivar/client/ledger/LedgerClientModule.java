package com.kragnoth.cultivar.client.ledger;

import com.kragnoth.cultivar.client.ClientModule;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class LedgerClientModule implements ClientModule {
    @Override
    public String id() {
        return "ledger";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        LedgerItem.openScreen = stack -> Minecraft.getInstance().setScreen(new LedgerScreen(stack));
    }
}
