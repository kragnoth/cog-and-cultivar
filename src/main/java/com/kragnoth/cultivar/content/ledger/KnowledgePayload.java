package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.Cultivar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the player's full knowledge set (small; sent on login, respawn, dimension change and on change). */
public record KnowledgePayload(LedgerItem.Data data) implements CustomPacketPayload {
    public static final Type<KnowledgePayload> TYPE = new Type<>(Cultivar.id("knowledge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgePayload> STREAM_CODEC =
            LedgerItem.Data.STREAM_CODEC.map(KnowledgePayload::new, KnowledgePayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
