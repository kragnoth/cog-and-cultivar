package com.kragnoth.cultivar.content.soil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Steps a soil block has been shifted by conditioners: positive acidity is towards alkaline, positive nutrients
 * richer, positive humidity wetter.
 */
public record Amendment(int acidity, int nutrients, int humidity) {
    public static final Amendment NONE = new Amendment(0, 0, 0);

    public static final Codec<Amendment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("acidity", 0).forGetter(Amendment::acidity),
            Codec.INT.optionalFieldOf("nutrients", 0).forGetter(Amendment::nutrients),
            Codec.INT.optionalFieldOf("humidity", 0).forGetter(Amendment::humidity)
    ).apply(i, Amendment::new));

    public static final StreamCodec<ByteBuf, Amendment> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE.map(Byte::intValue, Integer::byteValue), Amendment::acidity,
            ByteBufCodecs.BYTE.map(Byte::intValue, Integer::byteValue), Amendment::nutrients,
            ByteBufCodecs.BYTE.map(Byte::intValue, Integer::byteValue), Amendment::humidity,
            Amendment::new);

    public boolean isEmpty() {
        return acidity == 0 && nutrients == 0 && humidity == 0;
    }

    /** This amendment plus {@code acid/nut/hum} steps, each total clamped to {@code ±max}. */
    public Amendment plus(int acid, int nut, int hum, int max) {
        return new Amendment(Math.clamp(acidity + acid, -max, max), Math.clamp(nutrients + nut, -max, max),
                Math.clamp(humidity + hum, -max, max));
    }
}
