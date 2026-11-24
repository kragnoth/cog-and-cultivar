package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthConditionType;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

/**
 * Whitelist (or blacklist) of biomes; ignored once strength reaches {@code ignore_at_strength}.
 * <pre>{"type": "cultivar:biome", "biomes": "#minecraft:is_jungle", "ignore_at_strength": 8}</pre>
 */
public record BiomeCondition(HolderSet<Biome> biomes, boolean blacklist, int ignoreAtStrength) implements GrowthCondition {
    public static final MapCodec<BiomeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(BiomeCondition::biomes),
            Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(BiomeCondition::blacklist),
            Codec.intRange(0, 100).optionalFieldOf("ignore_at_strength", 100).forGetter(BiomeCondition::ignoreAtStrength)
    ).apply(i, BiomeCondition::new));

    @Override
    public GrowthConditionType<?> type() {
        return CoreConditions.BIOME.get();
    }

    @Override
    public java.util.Optional<net.minecraft.network.chat.Component> describe() {
        String what = biomes.unwrapKey().map(t -> "#" + t.location())
                .orElseGet(() -> biomes.stream().map(h -> h.unwrapKey().map(k -> k.location().getPath()).orElse("?"))
                        .limit(3).collect(java.util.stream.Collectors.joining(", ")) + (biomes.size() > 3 ? ", ..." : ""));
        return java.util.Optional.of(net.minecraft.network.chat.Component.translatable(
                blacklist ? "cultivar.condition.biome.not" : "cultivar.condition.biome", what));
    }

    @Override
    public java.util.Optional<net.minecraft.network.chat.Component> current(GrowthContext context) {
        return context.level().getBiome(context.pos()).unwrapKey()
                .map(k -> net.minecraft.network.chat.Component.translatableWithFallback(
                        "biome." + k.location().getNamespace() + "." + k.location().getPath(), k.location().getPath()));
    }

    @Override
    public GrowthResponse test(GrowthContext context, int strength) {
        if (strength >= ignoreAtStrength) {
            return GrowthResponse.FERTILE;
        }
        boolean inSet = biomes.contains(context.level().getBiome(context.pos()));
        return inSet != blacklist ? GrowthResponse.FERTILE : GrowthResponse.INFERTILE;
    }
}
