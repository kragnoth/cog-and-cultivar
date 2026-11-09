package com.kragnoth.cultivar.api.influence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * Datapack entry: blocks that modify crops within range (beehives, sprinklers, heaters...).
 * JSON path: {@code data/<namespace>/cultivar/crop_influence/<id>.json}.
 *
 * <p>Stacking: several blocks of the <i>same</i> entry don't stack; the strongest one in range applies.
 * Different entries combine (multipliers multiply, bonuses add), then the totals are clamped by server
 * config so stacked sources can't run away.
 *
 * <p>Lookup cost: crops cache their influence and refresh it on a slow interval or a neighbour change.
 * Sources that are POIs (beehives, bee nests) are found through the POI index, not by scanning blocks.
 *
 * @param blocks     blocks that act as this source
 * @param radius     horizontal reach in blocks (square, centred on the source)
 * @param height     vertical reach above and below the source
 * @param effects    effects at full strength
 * @param scaling    optional live strength (e.g. bee occupancy); absent means always full strength
 */
public record CropInfluence(HolderSet<Block> blocks,
                            int radius,
                            int height,
                            InfluenceEffects effects,
                            Optional<InfluenceScaling> scaling) {
    public static final Codec<CropInfluence> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks").forGetter(CropInfluence::blocks),
            Codec.intRange(0, 16).optionalFieldOf("radius", 4).forGetter(CropInfluence::radius),
            Codec.intRange(0, 8).optionalFieldOf("height", 2).forGetter(CropInfluence::height),
            InfluenceEffects.CODEC.fieldOf("effects").forGetter(CropInfluence::effects),
            InfluenceScaling.CODEC.optionalFieldOf("scaling").forGetter(CropInfluence::scaling)
    ).apply(i, CropInfluence::new));
}
