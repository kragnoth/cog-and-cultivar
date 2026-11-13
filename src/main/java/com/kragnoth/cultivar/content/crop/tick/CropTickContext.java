package com.kragnoth.cultivar.content.crop.tick;

import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.content.core.lookup.SoilLookup;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/**
 * Everything a {@link CropTickPhase} gets for one random tick. A record so new fields can be added later without
 * breaking phases already written against it.
 *
 * @param holder the block entity that owns the plant (a trellis today; field crops next)
 * @param plant  the plant itself: genome, stage, growth, harvest (shared by every holder)
 * @param fx     combined crop influence at the holder (beehives, cloches...)
 */
public record CropTickContext(BlockEntity holder, PlantCore plant, ServerLevel level, RandomSource random, InfluenceEffects fx) {
    /** Where the plant is rooted. */
    public BlockPos pos() {
        return holder.getBlockPos();
    }

    /** True when the soil under the plant is an ideal host (a Trial Bed), which lifts requirements and weeds. */
    public boolean onIdealHost() {
        return SoilLookup.isIdealHost(level, pos().below());
    }

    /** The holder as a trellis, for phases that only make sense there (weeds, breeding). */
    public Optional<CropBlockEntity> trellis() {
        return holder instanceof CropBlockEntity crop ? Optional.of(crop) : Optional.empty();
    }
}
