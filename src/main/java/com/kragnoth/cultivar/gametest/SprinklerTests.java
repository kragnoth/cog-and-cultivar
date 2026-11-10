package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.irrigation.IrrigationModule;
import com.kragnoth.cultivar.content.irrigation.SprinklerBlock;
import com.kragnoth.cultivar.content.irrigation.SprinklerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SprinklerTests {
    private SprinklerTests() {}

    @GameTest(template = "empty")
    public static void sprinklerGrowsCropsMoistensFarmlandAndSpreadsMoss(GameTestHelper h) {
        BlockPos sprinkler = new BlockPos(1, 2, 1);
        BlockPos soil = new BlockPos(1, 1, 2);
        h.setBlock(sprinkler.below(), Blocks.STONE);
        h.setBlock(sprinkler, IrrigationModule.SPRINKLER.get().defaultBlockState().setValue(SprinklerBlock.FACING, Direction.UP));
        h.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        h.setBlock(soil.above(), Blocks.WHEAT);
        BlockPos moss = new BlockPos(4, 1, 1);
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, 1, 0), new BlockPos(6, 1, 3))) {
            h.setBlock(p, Blocks.STONE); // moss spreads onto stone
        }
        h.setBlock(moss, Blocks.MOSS_BLOCK);

        var level = h.getLevel();
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(sprinkler), Direction.DOWN);
        h.assertTrue(fluids != null, "pipes see a fluid handler on every side");
        h.assertTrue(fluids.fill(new FluidStack(Fluids.LAVA, 100), IFluidHandler.FluidAction.EXECUTE) == 0, "water only");
        h.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE) == 500, "accepts water");
        h.assertTrue(fluids.drain(100, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "pipes can't pull water back out");

        SprinklerBlockEntity be = (SprinklerBlockEntity) h.getBlockEntity(sprinkler);
        RandomSource random = RandomSource.create(7L);
        for (int i = 0; i < 5000; i++) {
            be.pulse(level, random, 4, 0.05);
        }
        h.assertTrue(h.getBlockState(soil).getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE, "farmland kept moist");
        h.assertTrue(h.getBlockState(soil.above()).getValue(CropBlock.AGE) > 0, "wheat grew from extra random ticks");
        for (int i = 0; i < 500; i++) {
            be.pulse(level, random, 4, 1.0);
        }
        boolean spread = false;
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(moss).offset(-1, 0, -1), h.absolutePos(moss).offset(2, 1, 2))) {
            if (!p.equals(h.absolutePos(moss)) && (level.getBlockState(p).is(Blocks.MOSS_CARPET) || level.getBlockState(p).is(Blocks.MOSS_BLOCK))) {
                spread = true;
                break;
            }
        }
        h.assertTrue(spread, "bone-meal pulses spread moss");
        h.succeed();
    }
}
