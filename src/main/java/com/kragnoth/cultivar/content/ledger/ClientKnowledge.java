package com.kragnoth.cultivar.content.ledger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The local player's knowledge as last synced. Plain data with no client classes, so the payload handler and the
 * JEI plugin can share it; empty on a dedicated server. Listeners run on the client main thread.
 */
public final class ClientKnowledge {
    private static volatile LedgerItem.Data data = LedgerItem.Data.EMPTY;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientKnowledge() {}

    public static LedgerItem.Data get() {
        return data;
    }

    public static void set(LedgerItem.Data next) {
        if (next.equals(data)) {
            return;
        }
        data = next;
        LISTENERS.forEach(Runnable::run);
    }

    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }
}
