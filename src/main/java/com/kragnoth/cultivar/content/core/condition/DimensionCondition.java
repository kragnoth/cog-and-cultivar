package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthConditionType;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Whitelist (or blacklist) of dimensions; ignored once strength reaches {@code ignore_at_strength}.
 * <pre>{"type": "cultivar:dimension", "dimensions": ["minecraft:the_nether"]}</pre>
 */
public record DimensionCondition(List<ResourceKey<Level>> dimensions, boolean blacklist, int ignoreAtStrength)
        implements GrowthCondition {
    public static final MapCodec<DimensionCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).listOf().fieldOf("dimensions").forGetter(DimensionCondition::dimensions),
            Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(DimensionCondition::blacklist),
            Codec.intRange(0, 100).optionalFieldOf("ignore_at_strength", 100).forGetter(DimensionCondition::ignoreAtStrength)
    ).apply(i, DimensionCondition::new));

    public DimensionCondition {
        dimensions = List.copyOf(dimensions);
    }

    @Override
    public GrowthConditionType<?> type() {
        return CoreConditions.DIMENSION.get();
    }

    @Override
    public GrowthResponse test(GrowthContext context, int strength) {
        if (strength >= ignoreAtStrength) {
            return GrowthResponse.FERTILE;
        }
        boolean inList = dimensions.contains(context.level().dimension());
        return inList != blacklist ? GrowthResponse.FERTILE : GrowthResponse.INFERTILE;
    }
}
