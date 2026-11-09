package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.core.influence.HoneyLevelScaling;
import com.kragnoth.cultivar.content.core.influence.InfluenceLookup;
import com.kragnoth.cultivar.content.crop.CropModule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Beehive and cloche influence on a real server, using the shipped crop_influence data. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfluenceTests {
    private InfluenceTests() {}

    private static void near(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < 1e-6, what + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = "empty", batch = "influence_hive")
    public static void fullBeehivePollinates(GameTestHelper h) {
        BlockPos crop = new BlockPos(0, 1, 0);
        h.setBlock(crop.below(), Blocks.FARMLAND);
        h.setBlock(crop, CropModule.TIMBER_TRELLIS.get());
        h.setBlock(crop.east(3), Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
        // POI registration happens on the next server task, so evaluate a couple of ticks later.
        h.runAfterDelay(2, () -> {
            InfluenceEffects fx = InfluenceLookup.compute(h.getLevel(), h.absolutePos(crop));
            near(h, fx.mutationMultiplier(), 1.25, "mutation multiplier");
            near(h, fx.weedMultiplier(), 0.75, "weed multiplier");
            h.assertTrue(fx.fertilityBonus() == 1 && fx.mutativityBonus() == 1, "fertility and mutativity bonus +1");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void emptyHiveIsWeak(GameTestHelper h) {
        double scale = HoneyLevelScaling.INSTANCE.scale(h.getLevel(), BlockPos.ZERO, Blocks.BEEHIVE.defaultBlockState());
        near(h, scale, 1 / 6.0, "empty hive strength");
        InfluenceEffects fx = new InfluenceEffects(1, 1.25, 1, 1, 0.75).scaled(scale);
        near(h, fx.mutationMultiplier(), 1 + 0.25 / 6, "mutation at 1/6");
        h.assertTrue(fx.fertilityBonus() == 0, "integer bonuses round down to 0");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void clocheAboveCrop(GameTestHelper h) {
        BlockPos crop = new BlockPos(0, 1, 0);
        h.setBlock(crop, CropModule.TIMBER_TRELLIS.get());
        h.setBlock(crop.above(), ClocheModule.BELL_CLOCHE.get());
        InfluenceEffects fx = InfluenceLookup.compute(h.getLevel(), h.absolutePos(crop));
        near(h, fx.growthMultiplier(), 1.1, "cloche growth");
        near(h, fx.weedMultiplier(), 0.5, "cloche weed suppression");
        h.succeed();
    }
}
