package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

/**
 * Effective soil humidity: the soil's datapack value,
 * <ul>
 *   <li>one step wetter if water touches the side of a non-farmland soil (farmland's own moisture already counts
 *       nearby water: moist farmland is its base value, dry farmland one step drier), or a Planter Bed
 *       holds water;</li>
 *   <li>one more step wetter under a Bell Cloche (on the trellis standing on the soil);</li>
 *   <li>one step drier if a lit heat source ({@code #cultivar:soil_heaters}: a burning Blaze Burner, lit campfire,
 *       magma block) sits beside or under it;</li>
 *   <li>plus any humidity steps from soil conditioners ({@link com.kragnoth.cultivar.content.soil.SoilAmendments});</li>
 *   <li>steps add up and clamp at arid / flooded; with no water or heat, unwatered farmland reads one step drier.</li>
 * </ul>
 * A handful of block reads and a pure function of world state, so the server's growth checks and the client's goggle
 * readout always agree.
 */
public final class Hydration {
    private Hydration() {}

    public static SoilLevels.Humidity effective(BlockGetter level, BlockPos soilPos, SoilLevels.Humidity base) {
        SoilLevels.Humidity[] all = SoilLevels.Humidity.values();
        int amended = com.kragnoth.cultivar.content.soil.SoilAmendments.get(level, soilPos).humidity();
        if (CultivarServerConfig.SPEC.isLoaded() && !CultivarServerConfig.WATER_SHIFTS_HUMIDITY.get()) {
            return all[Math.clamp(base.ordinal() + amended, 0, all.length - 1)];
        }
        boolean wet = false;
        BlockState host = level.getBlockState(soilPos);
        if (host.getBlock() instanceof com.kragnoth.cultivar.api.soil.SoilHost h && h.suppliesWater(level, soilPos)) {
            wet = true;
        }
        // Farmland already tracks nearby water in its MOISTURE (that's what makes it read wet), so water beside it
        // doesn't count again: moist farmland is its base humidity, dry farmland one step drier.
        boolean farmland = host.hasProperty(FarmBlock.MOISTURE);
        boolean heated = isHeat(level.getBlockState(soilPos.below()));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = soilPos.relative(d);
            wet |= !farmland && level.getFluidState(side).is(FluidTags.WATER);
            heated |= isHeat(level.getBlockState(side));
        }
        int shift = (wet ? 1 : 0) - (heated ? 1 : 0);
        if (farmland && !wet && !heated && host.getValue(FarmBlock.MOISTURE) == 0) {
            shift = -1;
        }
        BlockState above = level.getBlockState(soilPos.above());
        if (above.hasProperty(TrellisBlock.CLOCHE) && above.getValue(TrellisBlock.CLOCHE)) {
            shift += 1;
        }
        shift += amended;
        return all[Math.clamp(base.ordinal() + shift, 0, all.length - 1)];
    }

    /** A tagged heater that is actually burning: lit if it has a LIT state, a Blaze Burner only while fuelled. */
    public static boolean isHeat(BlockState state) {
        if (!state.is(HEATERS)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            return false;
        }
        return !state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)
                || BlazeBurnerBlock.getHeatLevelOf(state).isAtLeast(BlazeBurnerBlock.HeatLevel.FADING);
    }

    public static final TagKey<Block> HEATERS = TagKey.create(Registries.BLOCK, Cultivar.id("soil_heaters"));
}
