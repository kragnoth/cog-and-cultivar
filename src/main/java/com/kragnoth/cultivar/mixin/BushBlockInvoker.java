package com.kragnoth.cultivar.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the crop exporter ask any bush-type crop (vanilla or modded) which soils it accepts. */
@Mixin(BushBlock.class)
public interface BushBlockInvoker {
    @Invoker("mayPlaceOn")
    boolean cultivar$mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos);
}
