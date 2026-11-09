package com.kragnoth.cultivar.api.genetics;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * The full genetic identity of a seed or crop: its species plus one allele pair per stat.
 * Stored as the {@code cultivar:genome} data component and in crop block entities.
 * Stats missing from {@link #stats()} are treated as the stat's minimum, so adding a new stat
 * never invalidates existing seeds.
 */
public record Genome(Holder<Plant> species, Map<Holder<Stat>, AllelePair> stats) {
    private static final StreamCodec<ByteBuf, Holder<Stat>> STAT_HOLDER_STREAM_CODEC = ResourceLocation.STREAM_CODEC.map(
            id -> CultivarRegistries.STAT.getHolderOrThrow(ResourceKey.create(CultivarRegistries.STAT_KEY, id)),
            holder -> holder.unwrapKey().orElseThrow().location());

    public static final Codec<Genome> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(CultivarRegistries.PLANT).fieldOf("species").forGetter(Genome::species),
            Codec.unboundedMap(CultivarRegistries.STAT.holderByNameCodec(), AllelePair.CODEC)
                    .optionalFieldOf("stats", Map.of()).forGetter(Genome::stats)
    ).apply(i, Genome::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Genome> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(CultivarRegistries.PLANT), Genome::species,
            ByteBufCodecs.<RegistryFriendlyByteBuf, Holder<Stat>, AllelePair, Map<Holder<Stat>, AllelePair>>map(
                    HashMap::new, STAT_HOLDER_STREAM_CODEC, AllelePair.STREAM_CODEC), Genome::stats,
            Genome::new);

    public Genome {
        stats = Map.copyOf(stats);
    }

    /** Allele pair for a stat, defaulting to (min, min) when absent. */
    public AllelePair get(Holder<Stat> stat) {
        AllelePair pair = stats.get(stat);
        if (pair != null) {
            return pair;
        }
        int min = stat.value().defaultMin();
        return new AllelePair(min, min);
    }
}
