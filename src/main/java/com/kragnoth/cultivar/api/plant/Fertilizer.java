package com.kragnoth.cultivar.api.plant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/**
 * Datapack entry: items that act as fertilizer on crops. Fluid fertigation is a separate system.
 * JSON path: {@code data/<namespace>/cultivar/fertilizer/<id>.json}.
 *
 * @param potency growth ticks applied per use
 */
public record Fertilizer(HolderSet<Item> items,
                         int potency,
                         boolean triggersMutation,
                         boolean triggersWeeds) {
    public static final Codec<Fertilizer> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(Fertilizer::items),
            Codec.intRange(1, 16).optionalFieldOf("potency", 1).forGetter(Fertilizer::potency),
            Codec.BOOL.optionalFieldOf("triggers_mutation", false).forGetter(Fertilizer::triggersMutation),
            Codec.BOOL.optionalFieldOf("triggers_weeds", false).forGetter(Fertilizer::triggersWeeds)
    ).apply(i, Fertilizer::new));
}
