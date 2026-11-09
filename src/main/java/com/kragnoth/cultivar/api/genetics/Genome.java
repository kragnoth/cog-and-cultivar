package com.kragnoth.cultivar.api.genetics;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.Map;

/**
 * The full genetic identity of a seed or crop: its species plus one allele pair per stat.
 * Stored as the {@code cultivar:genome} data component and in crop block entities.
 *
 * <p>Stats are keyed by {@link ResourceKey} rather than {@link Holder}: holder identity differs between
 * registry references and {@code DeferredHolder}s, while keys always compare correctly.
 * Stats missing from {@link #stats()} read as (min, min), so adding a new stat never invalidates old seeds.
 */
public record Genome(Holder<Plant> species, Map<ResourceKey<Stat>, AllelePair> stats) {
    public static final Codec<Genome> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(CultivarRegistries.PLANT).fieldOf("species").forGetter(Genome::species),
            Codec.unboundedMap(ResourceKey.codec(CultivarRegistries.STAT_KEY), AllelePair.CODEC)
                    .optionalFieldOf("stats", Map.of()).forGetter(Genome::stats)
    ).apply(i, Genome::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Genome> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(CultivarRegistries.PLANT), Genome::species,
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceKey<Stat>, AllelePair, Map<ResourceKey<Stat>, AllelePair>>map(
                    HashMap::new, ResourceKey.streamCodec(CultivarRegistries.STAT_KEY), AllelePair.STREAM_CODEC), Genome::stats,
            Genome::new);

    public Genome {
        stats = Map.copyOf(stats);
    }

    /** A genome with every registered stat at its minimum. */
    public static Genome base(Holder<Plant> species) {
        return new Genome(species, Map.of());
    }

    public AllelePair get(ResourceKey<Stat> stat) {
        AllelePair pair = stats.get(stat);
        if (pair != null) {
            return pair;
        }
        Stat def = CultivarRegistries.STAT.get(stat);
        int min = def == null ? 1 : def.defaultMin();
        return new AllelePair(min, min);
    }

    public AllelePair get(Holder<Stat> stat) {
        return get(stat.unwrapKey().orElseThrow(() -> new IllegalArgumentException("Unregistered stat holder")));
    }

    public Genome withSpecies(Holder<Plant> newSpecies) {
        return new Genome(newSpecies, stats);
    }

    public Genome with(ResourceKey<Stat> stat, AllelePair pair) {
        Map<ResourceKey<Stat>, AllelePair> copy = new HashMap<>(stats);
        copy.put(stat, pair);
        return new Genome(species, copy);
    }
}
