package com.kragnoth.cultivar.api.plant;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * Datapack entry: crossing {@code parentA} with {@code parentB} can yield {@code child}.
 * Parent order does not matter. JSON path: {@code data/<namespace>/cultivar/mutation/<id>.json}.
 */
public record Mutation(Holder<Plant> parentA,
                       Holder<Plant> parentB,
                       Holder<Plant> child,
                       double chance,
                       List<GrowthCondition> conditions) {
    public static final Codec<Mutation> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(CultivarRegistries.PLANT).fieldOf("parent_a").forGetter(Mutation::parentA),
            RegistryFixedCodec.create(CultivarRegistries.PLANT).fieldOf("parent_b").forGetter(Mutation::parentB),
            RegistryFixedCodec.create(CultivarRegistries.PLANT).fieldOf("child").forGetter(Mutation::child),
            Codec.doubleRange(0, 1).optionalFieldOf("chance", 0.25).forGetter(Mutation::chance),
            GrowthCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(Mutation::conditions)
    ).apply(i, Mutation::new));

    public Mutation {
        conditions = List.copyOf(conditions);
    }

    public boolean matches(Holder<Plant> a, Holder<Plant> b) {
        return (parentA.is(a) && parentB.is(b)) || (parentA.is(b) && parentB.is(a));
    }
}
