package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.irrigation.IrrigationModule;
import com.kragnoth.cultivar.content.irrigation.OllaBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Olla keeps vanilla farmland moist like water, only when filled, within its radius, from under the field. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OllaTests {
    private OllaTests() {}

    private static int moistureAfterTicks(GameTestHelper h, BlockPos farmland) {
        var level = h.getLevel();
        BlockPos abs = h.absolutePos(farmland);
        for (int i = 0; i < 12; i++) {
            BlockState state = level.getBlockState(abs);
            if (!state.is(Blocks.FARMLAND)) {
                return -1; // dried out to dirt
            }
            state.randomTick(level, abs, level.random);
        }
        BlockState state = level.getBlockState(abs);
        return state.is(Blocks.FARMLAND) ? state.getValue(FarmBlock.MOISTURE) : -1;
    }

    @GameTest(template = "empty")
    public static void filledOllaKeepsFarmlandMoist(GameTestHelper h) {
        // Olla buried one layer under the field; farmland directly above it, at the edge of its reach, and past it.
        BlockPos olla = new BlockPos(0, 0, 0);
        BlockPos above = olla.above(), edge = olla.above().east(OllaBlock.radius()), past = olla.above().east(OllaBlock.radius() + 1);
        h.setBlock(olla, IrrigationModule.OLLA.get().defaultBlockState());
        for (BlockPos p : new BlockPos[]{above, edge, past}) {
            h.setBlock(p.below(), Blocks.STONE);
            h.setBlock(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        }
        h.setBlock(olla, IrrigationModule.OLLA.get().defaultBlockState());
        h.assertTrue(moistureAfterTicks(h, above) < 7, "an empty olla does nothing");

        h.setBlock(above, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(olla, IrrigationModule.OLLA.get().defaultBlockState().setValue(OllaBlock.FILLED, true));
        h.assertTrue(moistureAfterTicks(h, above) == 7, "farmland on top of a filled olla stays moist");
        h.assertTrue(moistureAfterTicks(h, edge) == 7, "farmland at the olla's reach stays moist");
        h.assertTrue(moistureAfterTicks(h, past) < 7, "farmland past its reach dries out");
        h.succeed();
    }
}
