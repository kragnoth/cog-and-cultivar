package com.kragnoth.cultivar.api.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A block that holds a Cultivar plant: the trellis (any segment of a stack) and, next, field crops. Features that act
 * on "the plant or its soil" (conditioners, sprinklers, readouts, tools) check for this instead of a concrete block,
 * so a new kind of plant block works with them by implementing it.
 */
public interface PlantHolder {
    /** The crop at {@code pos} (resolving tall stacks to their root), if one is planted. */
    Optional<? extends CropView> crop(BlockGetter level, BlockPos pos);

    /** The soil block under the plant at {@code pos}, planted or not. */
    BlockPos soilPos(BlockGetter level, BlockPos pos);

    /**
     * Harvests a mature plant in place (it stays planted and regrows), sending produce to {@code drops}. Used by
     * right-click harvest and by Create's Mechanical Harvester for every holder, so a new kind of plant block is
     * harvester-compatible by implementing this. Returns whether anything was harvested. Server side.
     */
    boolean harvestInPlace(Level level, BlockPos pos, Consumer<ItemStack> drops);
}
