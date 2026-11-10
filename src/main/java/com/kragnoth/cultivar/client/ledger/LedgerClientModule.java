package com.kragnoth.cultivar.client.ledger;

import com.kragnoth.cultivar.client.ClientModule;
import com.kragnoth.cultivar.content.ledger.ClientKnowledge;
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
        // Knowledge belongs to one server's player; forget it when leaving.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> ClientKnowledge.set(LedgerItem.Data.EMPTY));
    }
}
