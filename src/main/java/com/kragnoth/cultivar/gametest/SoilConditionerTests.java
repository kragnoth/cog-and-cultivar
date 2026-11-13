package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.content.core.condition.Hydration;
import com.kragnoth.cultivar.content.core.condition.SoilMatch;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.soil.Amendment;
import com.kragnoth.cultivar.content.soil.SoilAmendments;
import com.kragnoth.cultivar.content.soil.SoilModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Soil conditioners: stepping, capping, cancelling, persistence through replanting, and clearing with the soil. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoilConditionerTests {
    private SoilConditionerTests() {}

    @GameTest(template = "empty")
    public static void limeAndSulfurStepAndCap(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos soil = new BlockPos(0, 0, 0);
        BlockPos crop = soil.above();
        h.setBlock(soil, Blocks.FARMLAND);
        h.setBlock(crop, CropModule.TIMBER_TRELLIS.get());
        BlockPos abs = h.absolutePos(soil);
        CropBlockEntity be = (CropBlockEntity) h.getBlockEntity(crop);
        SoilLevels.Acidity base = be.soil().orElseThrow().acidity();
        int max = SoilAmendments.maxSteps();

        ItemStack lime = new ItemStack(SoilModule.AGRICULTURAL_LIME.get(), 8);
        for (int i = 1; i <= max; i++) {
            h.assertTrue(SoilAmendments.tryApply(level, abs, lime, null) == SoilAmendments.Result.APPLIED, "lime use " + i + " applies");
        }
        h.assertTrue(SoilAmendments.tryApply(level, abs, lime, null) == SoilAmendments.Result.SATURATED, "capped at max steps");
        h.assertTrue(lime.getCount() == 8 - max, "one lime spent per applied use, none when saturated");
        h.assertTrue(SoilAmendments.get(level, abs).acidity() == max, "amendment stored on the soil");
        h.assertTrue(be.acidity().orElseThrow().ordinal() == Math.min(base.ordinal() + max, SoilLevels.Acidity.values().length - 1),
                "crop sees the soil shifted alkaline");

        ItemStack sulfur = new ItemStack(SoilModule.SULFUR_DUST.get(), 8);
        for (int i = 0; i < max; i++) {
            SoilAmendments.tryApply(level, abs, sulfur, null);
        }
        h.assertTrue(SoilAmendments.get(level, abs).isEmpty(), "sulfur cancels lime back to none");

        h.assertTrue(SoilAmendments.tryApply(level, h.absolutePos(new BlockPos(2, 0, 2)), lime, null) == SoilAmendments.Result.PASS,
                "air isn't a soil");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void amendmentOutlivesTrellisAndClearsWithSoil(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos soil = new BlockPos(0, 0, 0);
        h.setBlock(soil, Blocks.FARMLAND);
        h.setBlock(soil.above(), CropModule.TIMBER_TRELLIS.get());
        BlockPos abs = h.absolutePos(soil);
        SoilAmendments.tryApply(level, abs, new ItemStack(SoilModule.COMPOST.get()), null);
        h.setBlock(soil.above(), Blocks.AIR);
        h.setBlock(soil.above(), CropModule.TIMBER_TRELLIS.get());
        h.assertTrue(SoilAmendments.get(level, abs).nutrients() == 1, "compost stays through a trellis rebuild");

        // Saved with the chunk.
        var chunk = level.getChunkAt(abs);
        var tag = SoilAmendments.MAP_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                chunk.getExistingDataOrNull(SoilModule.AMENDMENTS)).getOrThrow();
        h.assertTrue(SoilAmendments.MAP_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow().get(abs).nutrients() == 1,
                "amendments round-trip through NBT");

        level.destroyBlock(abs, false);
        SoilAmendments.clear(level, abs);  // what the break event does for a player
        h.assertTrue(SoilAmendments.get(level, abs).isEmpty(), "breaking the soil clears it");
        h.succeed();
    }

    /** Humidity amendments (datapack conditioners can carry them) stack with water and cloches in Hydration. */
    @GameTest(template = "empty")
    public static void humidityAmendmentShiftsHydration(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos soil = new BlockPos(0, 0, 0);
        h.setBlock(soil, Blocks.DIRT);
        BlockPos abs = h.absolutePos(soil);
        SoilAmendments.set(level, abs, new Amendment(0, 0, -1));
        h.assertTrue(Hydration.effective(level, abs, SoilLevels.Humidity.DAMP) == SoilLevels.Humidity.DRY, "-1 humidity dries the soil");
        SoilAmendments.set(level, abs, Amendment.NONE);
        h.assertTrue(Hydration.effective(level, abs, SoilLevels.Humidity.DAMP) == SoilLevels.Humidity.DAMP, "cleared");
        h.succeed();
    }

    /** JEI's Growing page counts soils that the shipped conditioners can bring into range. */
    @GameTest(template = "empty")
    public static void conditionersWidenSoilMatch(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        var reach = SoilMatch.Reach.of(access.registryOrThrow(CultivarRegistries.SOIL_CONDITIONER), 2);
        h.assertTrue(reach.acidUp() == 2 && reach.acidDown() == 2 && reach.nutUp() == 2 && reach.nutDown() == 0,
                "lime, sulfur and compost give acidity ±2 and nutrients +2");
        // Any soil that fits a plant without conditioners still fits with them.
        for (Holder<Plant> plant : access.registryOrThrow(CultivarRegistries.PLANT).holders().toList()) {
            for (var soil : access.registryOrThrow(CultivarRegistries.SOIL)) {
                if (SoilMatch.fit(plant.value().requirements(), soil, 1) != SoilMatch.Fit.NO) {
                    h.assertTrue(SoilMatch.fit(plant.value().requirements(), soil, 1, reach) != SoilMatch.Fit.NO,
                            "conditioners never remove a fit");
                }
            }
        }
        h.succeed();
    }
}
