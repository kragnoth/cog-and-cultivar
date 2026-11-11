package com.kragnoth.cultivar.content.tools;

import com.kragnoth.cultivar.Cultivar;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client → server, from the Seed Satchel screen: act on the satchel in {@code hand}. {@code index} is the entry
 * (ignored by TOGGLE_COLLECT and VOID_UNASSAYED); the server re-validates everything.
 */
public record SatchelActionPayload(InteractionHand hand, int index, Action action) implements CustomPacketPayload {
    public enum Action { TAKE, VOID, VOID_UNASSAYED, TOGGLE_COLLECT }

    public static final Type<SatchelActionPayload> TYPE = new Type<>(Cultivar.id("satchel_action"));
    public static final StreamCodec<ByteBuf, SatchelActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL.map(b -> b ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, h -> h == InteractionHand.OFF_HAND),
            SatchelActionPayload::hand,
            ByteBufCodecs.VAR_INT, SatchelActionPayload::index,
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
            SatchelActionPayload::action,
            SatchelActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
