package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.core.condition.LightCondition;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropInfo;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.content.crop.plant.PlantStatus;
import com.kragnoth.cultivar.content.field.FieldPlanting;
import com.kragnoth.cultivar.content.irrigation.IrrigationModule;
import com.kragnoth.cultivar.content.irrigation.OllaBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Readouts: status headline, which need is failing (and what the spot has), yield odds, grafting frames, the Olla. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReadoutTests {
    private ReadoutTests() {}

    private static Holder<Plant> plant(GameTestHelper h, String id) {
        return h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.parse(id)));
    }

    /** Every translation key used anywhere in the lines, so tests don't depend on English text. */
    private static boolean hasKey(List<Component> lines, String key) {
        return lines.stream().anyMatch(l -> hasKey(l, key));
    }

    private static boolean hasKey(Component c, String key) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(key)) {
                return true;
            }
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component a && hasKey(a, key)) {
                    return true;
                }
            }
        }
        return c.getSiblings().stream().anyMatch(s -> hasKey(s, key));
    }

    @GameTest(template = "empty")
    public static void shadedWheatIsStalledByLight(GameTestHelper h) {
        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        BlockPos shaded = new BlockPos(0, 1, 0), open = new BlockPos(2, 1, 0);
        for (BlockPos p : new BlockPos[]{shaded, open}) {
            h.setBlock(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(p.above(), Blocks.AIR);
        }
        h.setBlock(shaded, ClocheModule.SHADE_CLOCHE.get().fit(CropModule.TIMBER_TRELLIS.get().defaultBlockState()));
        h.setBlock(open, CropModule.TIMBER_TRELLIS.get());
        h.setBlock(new BlockPos(1, 2, 0), Blocks.GLOWSTONE); // bright enough for wheat whatever the sky does
        CropBlockEntity dark = (CropBlockEntity) h.getBlockEntity(shaded), lit = (CropBlockEntity) h.getBlockEntity(open);
        dark.plant(Genome.base(wheat), 1);
        lit.plant(Genome.base(wheat), 1);
        h.runAfterDelay(5, () -> {
            PlantStatus status = PlantStatus.of(dark.plant());
            h.assertTrue(status.kind() == PlantStatus.Kind.STALLED, "shaded wheat is stalled, got " + status.kind());
            var failing = status.failing();
            h.assertTrue(failing.size() == 1 && failing.getFirst().condition() instanceof LightCondition,
                    "the light requirement is the one failing: " + failing);
            h.assertTrue(failing.getFirst().current().isPresent(), "the failing line says what light the crop has");
            h.assertTrue(hasKey(CropInfo.lines(dark, CropInfo.Detail.BRIEF), "cultivar.status.stalled")
                    && hasKey(CropInfo.lines(dark, CropInfo.Detail.BRIEF), "cultivar.condition.light.min"), "Jade says stalled and why");

            PlantStatus ok = PlantStatus.of(lit.plant());
            h.assertTrue(ok.kind() == PlantStatus.Kind.GROWING && ok.failing().isEmpty(), "lit wheat grows, got " + ok.kind());
            h.assertTrue(PlantStatus.stagesPerDay(lit.plant(), ok) > 0, "growing wheat has a growth rate");
            lit.plant().setStage(wheat.value().matureStage());
            h.assertTrue(PlantStatus.of(lit.plant()).kind() == PlantStatus.Kind.RIPE, "ripe");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void noSoilAndPlantingHints(GameTestHelper h) {
        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        BlockPos onStone = new BlockPos(0, 1, 0);
        h.setBlock(onStone.below(), Blocks.STONE);
        h.setBlock(onStone, CropModule.TIMBER_TRELLIS.get());
        CropBlockEntity crop = (CropBlockEntity) h.getBlockEntity(onStone);
        crop.plant(Genome.base(wheat), 0);
        h.assertTrue(PlantStatus.of(crop.plant()).kind() == PlantStatus.Kind.NO_SOIL, "a trellis on stone has no soil");

        BlockPos farmland = new BlockPos(2, 0, 0);
        h.setBlock(farmland, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(farmland.above(), Blocks.AIR);
        h.assertTrue(FieldPlanting.soilProblem(h.getLevel(), h.absolutePos(farmland.above()), Genome.base(wheat)).isEmpty(),
                "moist farmland suits wheat: no problem reported");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void yieldOddsAndBands(GameTestHelper h) {
        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        BlockPos at = new BlockPos(0, 1, 0);
        h.setBlock(at.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(at, CropModule.TIMBER_TRELLIS.get());
        CropBlockEntity crop = (CropBlockEntity) h.getBlockEntity(at);
        crop.plant(Genome.base(wheat), 0);
        var yields = PlantStatus.yields(crop.plant(), true);
        h.assertTrue(!yields.isEmpty() && yields.stream().allMatch(y -> y.chance() > 0 && y.average() > 0), "wheat has yields");
        h.assertTrue("reliable".equals(CropInfo.band(1.0)) && "very_rarely".equals(CropInfo.band(0.03))
                && "sometimes".equals(CropInfo.band(0.3)), "bands");
        h.assertTrue(PlantStatus.yields(crop.plant(), false).stream().allMatch(y -> y.chance() <= 1), "unassayed odds use one roll");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void graftingFrameSaysWhatItWaitsFor(GameTestHelper h) {
        BlockPos frame = new BlockPos(1, 1, 1);
        h.setBlock(frame.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(frame, CropModule.TIMBER_TRELLIS.get().defaultBlockState().setValue(TrellisBlock.CROSS, true));
        CropBlockEntity cross = (CropBlockEntity) h.getBlockEntity(frame);
        h.assertTrue(hasKey(CropInfo.lines(cross), "cultivar.cross.no_parents"), "an empty frame says it has no ripe parents");

        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        BlockPos parent = frame.east();
        h.setBlock(parent.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(parent, CropModule.TIMBER_TRELLIS.get());
        ((CropBlockEntity) h.getBlockEntity(parent)).plant(Genome.base(wheat), wheat.value().matureStage());
        h.assertTrue(hasKey(CropInfo.lines(cross), "cultivar.cross.one_parent"), "one ripe parent can only spread");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ollaReadout(GameTestHelper h) {
        BlockPos olla = new BlockPos(1, 0, 1);
        h.setBlock(olla, IrrigationModule.OLLA.get().defaultBlockState().setValue(OllaBlock.FILLED, true));
        h.setBlock(olla.above().east(), Blocks.FARMLAND);
        var lines = OllaBlock.readout(h.getLevel(), h.absolutePos(olla), h.getBlockState(olla));
        h.assertTrue(hasKey(lines, "cultivar.olla.filled") && hasKey(lines, "cultivar.olla.watering"), "filled olla reports what it waters");
        h.assertTrue(h.getBlockEntity(olla) != null, "olla has a block entity for goggles");
        h.succeed();
    }
}
