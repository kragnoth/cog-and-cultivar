package com.kragnoth.cultivar.api.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A diploid pair of integer alleles for one stat. Higher value is dominant. */
public record AllelePair(int dominant, int recessive) {
    public static final Codec<AllelePair> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("dominant").forGetter(AllelePair::dominant),
            Codec.INT.fieldOf("recessive").forGetter(AllelePair::recessive)
    ).apply(i, AllelePair::of));

    public static final StreamCodec<ByteBuf, AllelePair> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AllelePair::dominant,
            ByteBufCodecs.VAR_INT, AllelePair::recessive,
            AllelePair::of);

    /** Orders the two values so the higher one is dominant. */
    public static AllelePair of(int a, int b) {
        return new AllelePair(Math.max(a, b), Math.min(a, b));
    }
}
