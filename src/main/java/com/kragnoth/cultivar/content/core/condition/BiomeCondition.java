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
    public GrowthResponse test(GrowthContext context, int strength) {
        if (strength >= ignoreAtStrength) {
            return GrowthResponse.FERTILE;
        }
        boolean inSet = biomes.contains(context.level().getBiome(context.pos()));
        return inSet != blacklist ? GrowthResponse.FERTILE : GrowthResponse.INFERTILE;
    }
}
