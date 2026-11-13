package com.kragnoth.cultivar.content.core.lookup;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.api.soil.SoilHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Which Cultivar soil a block is: the block itself, or what a {@link SoilHost} (Planter Bed, Trial Bed) holds. */
public final class SoilLookup {
    private SoilLookup() {}

    public static Optional<Soil> at(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState soilState = state.getBlock() instanceof SoilHost host ? host.hostedSoil(level, pos) : state;
        if (soilState == null) {
            return Optional.empty();
        }
        return level.registryAccess().registryOrThrow(CultivarRegistries.SOIL).stream()
                .filter(soil -> soil.blocks().contains(soilState.getBlockHolder()))
                .findFirst();
    }

    /** On an ideal soil host (Trial Bed): requirements, weeds and mutation habitats are ignored. */
    public static boolean isIdealHost(Level level, BlockPos soilPos) {
        return level.getBlockState(soilPos).getBlock() instanceof SoilHost host && host.isIdeal(level, soilPos);
    }
}
