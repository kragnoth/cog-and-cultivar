package com.kragnoth.cultivar.content.soil;

import com.kragnoth.cultivar.Cultivar;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;

/**
 * Server → client: every soil amendment in one chunk (replaces the client's copy). Sent when a player starts watching
 * the chunk and whenever an amendment in it changes. Usually empty or a handful of entries.
 */
public record AmendmentsPayload(ChunkPos chunk, Map<BlockPos, Amendment> amendments) implements CustomPacketPayload {
    public static final Type<AmendmentsPayload> TYPE = new Type<>(Cultivar.id("soil_amendments"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AmendmentsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.map(ChunkPos::new, ChunkPos::toLong), AmendmentsPayload::chunk,
            ByteBufCodecs.map(java.util.HashMap::new, BlockPos.STREAM_CODEC, Amendment.STREAM_CODEC, 65536), AmendmentsPayload::amendments,
            AmendmentsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
