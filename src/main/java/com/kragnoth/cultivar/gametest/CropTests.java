package com.kragnoth.cultivar.gametest;

import net.minecraft.world.level.block.FarmBlock;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.content.core.condition.Hydration;
import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Block-level checks on a real server: starter data loads, planting works, NBT round-trips. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CropTests {
    private CropTests() {}

    @GameTest(template = "empty")
    public static void plantAndPersist(GameTestHelper h) {
        Holder<Plant> wheat = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("wheat")));
        BlockPos soil = new BlockPos(0, 0, 0);
        BlockPos crop = soil.above();
        h.setBlock(soil, Blocks.FARMLAND);
        h.setBlock(crop, CropModule.TIMBER_TRELLIS.get());

        CropBlockEntity be = (CropBlockEntity) h.getBlockEntity(crop);
        h.assertTrue(be.plant(Genome.base(wheat)), "planting on an empty trellis should succeed");
        h.assertTrue(be.hasPlant() && be.stage() == 0, "plant present at stage 0");

        var registries = h.getLevel().registryAccess();
        var tag = be.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(h.absolutePos(crop), be.getBlockState(), tag, registries);
        h.assertTrue(loaded instanceof CropBlockEntity c && c.genome() != null
                && c.genome().species().is(wheat), "genome survives save/load");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void starterDataLoaded(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        h.assertTrue(access.registryOrThrow(CultivarRegistries.PLANT).size() >= 17, "all vanilla crops loaded");
        h.assertTrue(access.registryOrThrow(CultivarRegistries.SOIL).size() >= 6, "soils loaded");
        h.assertTrue(access.registryOrThrow(CultivarRegistries.MUTATION).size() >= 15, "vanilla mutation tree loaded");
        // Every parent pair forks only on habitat: two mutations sharing parents must not share conditions.
        var mutations = access.registryOrThrow(CultivarRegistries.MUTATION).stream().toList();
        for (int i = 0; i < mutations.size(); i++) {
            for (int j = i + 1; j < mutations.size(); j++) {
                var a = mutations.get(i);
                var b = mutations.get(j);
                if (a.matches(b.parentA(), b.parentB())) {
                    h.assertTrue(!a.conditions().isEmpty() && !b.conditions().isEmpty(),
                            "forked mutations of the same parents both need habitat conditions");
                }
            }
        }
        h.assertTrue(access.registryOrThrow(CultivarRegistries.WEED).size() >= 1, "weeds loaded");
        h.succeed();
    }

    /** Vanilla farmland crops grow wherever vanilla grows them: moist (even beside water) or dry farmland, light 9-15. */
    @GameTest(template = "empty")
    public static void vanillaCropsGrowOnVanillaFarmland(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        var soils = access.registryOrThrow(CultivarRegistries.SOIL);
        var farmland = soils.stream().filter(s -> s.blocks().contains(Blocks.FARMLAND.builtInRegistryHolder())).findFirst().orElseThrow();
        BlockPos moist = new BlockPos(1, 1, 1), dry = new BlockPos(4, 1, 1);
        h.setBlock(moist, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(moist.east(), Blocks.WATER);
        h.setBlock(dry, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        var moistHum = Hydration.effective(h.getLevel(), h.absolutePos(moist), farmland.humidity());
        var dryHum = Hydration.effective(h.getLevel(), h.absolutePos(dry), farmland.humidity());
        for (String id : java.util.List.of("wheat", "carrot", "potato", "beetroot", "pumpkin", "melon", "torchflower", "pitcher_plant")) {
            Plant plant = access.registryOrThrow(CultivarRegistries.PLANT).get(ResourceLocation.withDefaultNamespace(id));
            h.assertTrue(plant != null, id + " should load");
            var needs = plant.requirements().stream().filter(com.kragnoth.cultivar.api.condition.GrowthCondition::isSoilProperty).toList();
            for (var humidity : java.util.List.of(moistHum, dryHum)) {
                var ctx = new com.kragnoth.cultivar.api.condition.GrowthContext(h.getLevel(), BlockPos.ZERO, java.util.Optional.of(farmland),
                        java.util.Optional.of(humidity), java.util.Optional.of(farmland.acidity()), java.util.Optional.of(farmland.nutrients()));
                h.assertTrue(com.kragnoth.cultivar.api.condition.GrowthCondition.testAll(needs, ctx, 1)
                        == com.kragnoth.cultivar.api.condition.GrowthResponse.FERTILE, id + " should grow on " + humidity + " farmland");
            }
            for (var c : plant.requirements()) {
                if (c instanceof com.kragnoth.cultivar.content.core.condition.LightCondition light) {
                    h.assertTrue(light.min() <= 9 && light.max() >= 15, id + " should grow at vanilla light 9-15");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void waterShiftsHumidity(GameTestHelper h) {
        BlockPos wet = new BlockPos(1, 1, 1), dry = new BlockPos(4, 1, 1), mud = new BlockPos(7, 1, 1);
        h.setBlock(wet, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(wet.east(), Blocks.WATER);
        h.setBlock(dry, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        h.setBlock(mud, Blocks.MUD);
        h.setBlock(mud.west(), Blocks.WATER);
        var level = h.getLevel();
        h.assertTrue(Hydration.effective(level, h.absolutePos(wet), SoilLevels.Humidity.WET) == SoilLevels.Humidity.WET,
                "water beside moist farmland doesn't count twice: wheat's standard layout reads wet");
        h.assertTrue(Hydration.effective(level, h.absolutePos(dry), SoilLevels.Humidity.WET) == SoilLevels.Humidity.DAMP,
                "dry farmland reads damp");
        h.assertTrue(Hydration.effective(level, h.absolutePos(mud), SoilLevels.Humidity.FLOODED) == SoilLevels.Humidity.FLOODED,
                "flooded is the ceiling");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blightFollowsHabitat(GameTestHelper h) {
        BlockPos mud = new BlockPos(1, 2, 1), sand = new BlockPos(4, 2, 1), podzol = new BlockPos(7, 2, 1);
        h.setBlock(mud, Blocks.MUD);
        h.setBlock(sand, Blocks.SAND);
        h.setBlock(podzol, Blocks.PODZOL);
        var trellis = com.kragnoth.cultivar.content.crop.CropModule.TIMBER_TRELLIS.get().defaultBlockState();
        for (BlockPos soil : new BlockPos[]{mud, sand, podzol}) {
            h.setBlock(soil.above(), trellis);
        }
        java.util.function.Function<BlockPos, java.util.Set<String>> eligible = soil ->
                ((com.kragnoth.cultivar.content.crop.CropBlockEntity) h.getBlockEntity(soil.above())).eligibleWeeds().stream()
                        .map(w -> w.unwrapKey().orElseThrow().location().getPath()).collect(java.util.stream.Collectors.toSet());
        var onMud = eligible.apply(mud);
        var onSand = eligible.apply(sand);
        var onPodzol = eligible.apply(podzol);
        h.assertTrue(onMud.contains("bog_rot") && onMud.contains("rust_rot") && !onMud.contains("dry_rot"), "mud: " + onMud);
        h.assertTrue(onSand.contains("dry_rot") && onSand.contains("ash_blight") && onSand.contains("leach_creep")
                && !onSand.contains("bog_rot"), "sand: " + onSand);
        h.assertTrue(onPodzol.contains("sour_moss") && onPodzol.contains("glut_canker") && !onPodzol.contains("ash_blight"),
                "podzol: " + onPodzol);

        var crop = (com.kragnoth.cultivar.content.crop.CropBlockEntity) h.getBlockEntity(mud.above());
        var bog = crop.eligibleWeeds().stream().filter(w -> w.is(com.kragnoth.cultivar.Cultivar.id("bog_rot"))).findFirst().orElseThrow();
        crop.setWeed(bog, 0);
        java.util.List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>();
        crop.clearWeeds(drops::add);
        h.assertTrue(drops.isEmpty() && !crop.hasWeed(), "an immature blight clears with no drops");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heatAndClocheShiftHumidity(GameTestHelper h) {
        BlockPos fire = new BlockPos(1, 2, 1), cold = new BlockPos(4, 2, 1), both = new BlockPos(7, 2, 1);
        h.setBlock(fire, Blocks.COARSE_DIRT);
        h.setBlock(fire.below(), Blocks.MAGMA_BLOCK);
        h.setBlock(cold, Blocks.COARSE_DIRT);
        h.setBlock(cold.east(), Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        h.setBlock(both, Blocks.MOSS_BLOCK);
        h.setBlock(both.east(), Blocks.CAMPFIRE);
        h.setBlock(both.west(), Blocks.WATER);
        var level = h.getLevel();
        h.assertTrue(Hydration.effective(level, h.absolutePos(fire), SoilLevels.Humidity.DRY) == SoilLevels.Humidity.ARID,
                "a magma block under dry soil makes it arid");
        h.assertTrue(Hydration.effective(level, h.absolutePos(cold), SoilLevels.Humidity.DRY) == SoilLevels.Humidity.DRY,
                "an unlit campfire does nothing");
        h.assertTrue(Hydration.effective(level, h.absolutePos(both), SoilLevels.Humidity.DAMP) == SoilLevels.Humidity.DAMP,
                "water and heat cancel");
        BlockPos jar = new BlockPos(10, 2, 1);
        h.setBlock(jar, Blocks.COARSE_DIRT);
        h.setBlock(jar.above(), com.kragnoth.cultivar.content.crop.CropModule.TIMBER_TRELLIS.get().defaultBlockState()
                .setValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CLOCHE, true));
        h.assertTrue(Hydration.effective(level, h.absolutePos(jar), SoilLevels.Humidity.DRY) == SoilLevels.Humidity.DAMP,
                "a Bell Cloche makes the soil one step wetter");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tallCropNeedsStackedTrellis(GameTestHelper h) {
        var cane = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("sugar_cane")));
        BlockPos base = new BlockPos(1, 2, 1);
        h.setBlock(base.below(), Blocks.MUD);
        h.setBlock(base, CropModule.TIMBER_TRELLIS.get());
        CropBlockEntity crop = (CropBlockEntity) h.getBlockEntity(base);
        crop.plant(Genome.base(cane), 5);
        crop.clampToSpace();
        h.assertTrue(crop.stage() == 1 && crop.segmentsMissing() == 2, "one trellis holds one block of cane");

        h.setBlock(base.above(), CropModule.TIMBER_TRELLIS.get());
        h.setBlock(base.above(2), CropModule.IRON_TRELLIS.get());
        h.assertTrue(crop.segmentsAvailable() == 3 && crop.segmentsMissing() == 0, "two segments give room for 3 blocks");
        CropBlockEntity segment = (CropBlockEntity) h.getBlockEntity(base.above());
        h.assertTrue(segment.isSegment() && !segment.plant(Genome.base(cane)), "segments never take a plant of their own");
        h.assertTrue(CropBlockEntity.resolve(h.getLevel(), h.absolutePos(base.above(2))).orElseThrow() == crop, "segments resolve to the base");

        crop.uproot();
        crop.plant(Genome.base(cane), 5);
        h.setBlock(base.above(2), Blocks.AIR);
        h.assertTrue(crop.stage() == 3, "removing the top segment shrinks the cane to fit two blocks");
        h.setBlock(base.above(), Blocks.AIR);
        h.assertTrue(crop.stage() == 1, "and to one block");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tallHarvestYieldsPerSegmentCut(GameTestHelper h) {
        var cane = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("sugar_cane")));
        BlockPos base = new BlockPos(1, 2, 1);
        h.setBlock(base.below(), Blocks.MUD);
        h.setBlock(base, CropModule.TIMBER_TRELLIS.get());
        h.setBlock(base.above(), CropModule.TIMBER_TRELLIS.get());
        h.setBlock(base.above(2), CropModule.TIMBER_TRELLIS.get());
        CropBlockEntity crop = (CropBlockEntity) h.getBlockEntity(base);
        crop.plant(Genome.base(cane), cane.value().matureStage());
        java.util.List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>();
        h.assertTrue(crop.harvest(drops::add), "mature cane harvests");
        // Gain 1 = one roll per segment; 3 tall back to 1 = two segments cut.
        h.assertTrue(drops.size() == 2, "two segments cut give two rolls, got " + drops.size());
        h.assertTrue(cane.value().blocksAt(crop.stage()) == 1, "cane drops back to one block");
        h.succeed();
    }
}
