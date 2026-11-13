package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.cloche.ClocheType;
import com.kragnoth.cultivar.content.core.condition.CropLight;
import com.kragnoth.cultivar.content.core.condition.LightCondition;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Shade and Lamp Cloches override only their own crop's light; neighbours read the real world light. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClocheLightTests {
    private ClocheLightTests() {}

    @GameTest(template = "empty")
    public static void shadeAndLampOverrideOnlyTheirCrop(GameTestHelper h) {
        var level = h.getLevel();
        BlockState trellis = CropModule.TIMBER_TRELLIS.get().defaultBlockState();
        BlockPos shaded = new BlockPos(0, 1, 0), lit = new BlockPos(2, 1, 0), plain = new BlockPos(1, 1, 0);
        for (BlockPos p : new BlockPos[]{shaded, lit, plain}) {
            h.setBlock(p.below(), Blocks.FARMLAND);
        }
        h.setBlock(shaded, ClocheModule.SHADE_CLOCHE.get().fit(trellis));
        h.setBlock(lit, ClocheModule.LAMP_CLOCHE.get().fit(trellis));
        h.setBlock(plain, trellis);
        h.setBlock(new BlockPos(0, 1, 1), Blocks.GLOWSTONE);  // bright beside the shaded crop
        h.assertTrue(ClocheType.fitted(h.getBlockState(shaded)) == ClocheType.SHADE, "shade cloche fitted");
        h.assertTrue(new ItemStack(ClocheModule.SHADE_CLOCHE.get()).is(ClocheType.SHADE.item()), "drops map back to the item");

        h.runAfterDelay(5, () -> {  // let the light engine settle
            BlockPos s = h.absolutePos(shaded), l = h.absolutePos(lit), n = h.absolutePos(plain);
            h.assertTrue(level.getRawBrightness(s, 0) >= 13, "real light beside glowstone is bright");
            h.assertTrue(CropLight.at(level, s) <= com.kragnoth.cultivar.content.cloche.ClocheEnvironment.shadeMax(), "shaded crop reads dark");
            h.assertTrue(CropLight.at(level, l) >= com.kragnoth.cultivar.content.cloche.ClocheEnvironment.lampMin(), "lamp crop reads bright");
            h.assertTrue(CropLight.at(level, n) == level.getRawBrightness(n, 0), "plain neighbour reads real light");
            var dark = new LightCondition(0, 7, 0);
            var ctx = new GrowthContext(level, s, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
            h.assertTrue(dark.test(ctx, 1) == GrowthResponse.FERTILE, "a dark-only plant grows under the shade beside glowstone");
            h.assertTrue(h.getBlockState(lit).getValue(TrellisBlock.CLOCHE), "lamp cloche counts as a cloche (humidity, influence)");
            h.succeed();
        });
    }
}
