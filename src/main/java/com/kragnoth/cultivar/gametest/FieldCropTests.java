package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.core.condition.SoilMatch;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.field.FieldCropBlock;
import com.kragnoth.cultivar.content.field.FieldCropBlockEntity;
import com.kragnoth.cultivar.content.field.FieldCropModule;
import com.kragnoth.cultivar.content.field.FieldPlanting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Field crops: planting rules, right-click harvest + replant, wheat-style break drops, popping off lost soil. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FieldCropTests {
    private FieldCropTests() {}

    private static Holder<Plant> plant(GameTestHelper h, String id) {
        return h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.parse(id)));
    }

    @GameTest(template = "empty")
    public static void plantHarvestReplantAndBreak(GameTestHelper h) {
        var level = h.getLevel();
        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        BlockPos soil = new BlockPos(1, 0, 1);
        h.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(soil.above(), Blocks.AIR); // the test area is walled in barriers
        ItemStack seeds = CultivarSeedItem.of(Genome.base(wheat));
        seeds.setCount(2);

        h.assertTrue(FieldPlanting.tryPlant(level, h.absolutePos(soil), seeds, null) == FieldPlanting.Result.PLANTED, "wheat seed plants on farmland");
        h.assertTrue(seeds.getCount() == 1, "one seed spent");
        BlockPos at = soil.above();
        h.assertBlockPresent(FieldCropModule.FIELD_CROP.get(), at);
        FieldCropBlockEntity crop = (FieldCropBlockEntity) h.getBlockEntity(at);
        h.assertTrue(crop.genome() != null && crop.genome().species().is(wheat) && crop.stage() == 0, "genome kept, stage 0");
        h.assertTrue(FieldPlanting.tryPlant(level, h.absolutePos(soil), seeds, null) == FieldPlanting.Result.PASS, "occupied spot: no second plant");

        // Ripe: age mirrors growth, right-click harvest yields produce and replants.
        crop.plant().setStage(wheat.value().matureStage());
        h.assertTrue(h.getBlockState(at).getValue(FieldCropBlock.AGE) == 7, "ripe crop shows age 7");
        List<ItemStack> drops = new ArrayList<>();
        var block = FieldCropModule.FIELD_CROP.get();
        h.assertTrue(block.harvestInPlace(level, h.absolutePos(at), drops::add), "ripe crop harvests in place");
        h.assertTrue(!drops.isEmpty() && crop.plant().hasPlant() && !crop.isMature(), "produce dropped, plant stays and regrows");

        // Breaking ripe: seed + a harvest, like wheat.
        crop.plant().setStage(wheat.value().matureStage());
        List<ItemStack> broken = Block.getDrops(h.getBlockState(at), level, h.absolutePos(at), crop);
        h.assertTrue(broken.stream().anyMatch(s -> s.getItem() instanceof CultivarSeedItem), "breaking returns the seed");
        h.assertTrue(broken.size() >= 2, "breaking a ripe crop also gives produce");

        // Losing its soil pops it off.
        h.setBlock(soil, Blocks.STONE);
        h.assertBlockNotPresent(FieldCropModule.FIELD_CROP.get(), at);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refusesWrongSoilTallPlantsAndNonSoil(GameTestHelper h) {
        var level = h.getLevel();
        var access = level.registryAccess();
        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        ItemStack seed = CultivarSeedItem.of(Genome.base(wheat));

        h.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        h.assertTrue(FieldPlanting.tryPlant(level, h.absolutePos(new BlockPos(1, 0, 1)), seed, null) == FieldPlanting.Result.PASS,
                "stone isn't a soil: the click passes through");

        // A Cultivar soil wheat can't grow on, even with water, cloches or heat.
        var wrong = access.registryOrThrow(CultivarRegistries.SOIL).stream()
                .filter(s -> SoilMatch.fit(wheat.value().requirements(), s, 1) == SoilMatch.Fit.NO)
                .flatMap(s -> s.blocks().stream()).filter(b -> !(b.value() instanceof com.kragnoth.cultivar.api.soil.SoilHost)).findFirst();
        if (wrong.isPresent()) {
            BlockPos p = new BlockPos(3, 0, 1);
            h.setBlock(p, wrong.get().value());
            h.setBlock(p.above(), Blocks.AIR);
            h.assertTrue(FieldPlanting.tryPlant(level, h.absolutePos(p), seed, null) == FieldPlanting.Result.REFUSED,
                    "wheat refuses " + wrong.get().getRegisteredName());
        }

        // Plants taller than a block still need a trellis.
        BlockPos farmland = new BlockPos(1, 0, 3);
        h.setBlock(farmland, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(farmland.above(), Blocks.AIR);
        ItemStack cane = CultivarSeedItem.of(Genome.base(plant(h, "minecraft:sugar_cane")));
        h.assertTrue(FieldPlanting.tryPlant(level, h.absolutePos(farmland), cane, null) == FieldPlanting.Result.REFUSED,
                "sugar cane needs a trellis");
        h.assertBlockNotPresent(FieldCropModule.FIELD_CROP.get(), farmland.above());
        h.succeed();
    }

    /** Field crops grow by the same rules as trellis crops: random ticks on good soil take them to maturity. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void growsOnRandomTicks(GameTestHelper h) {
        var level = h.getLevel();
        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        BlockPos soil = new BlockPos(1, 0, 1);
        h.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(soil.above(), Blocks.AIR); // the test area is walled in barriers
        h.setBlock(soil.above().east(), Blocks.GLOWSTONE.defaultBlockState()); // light regardless of time of day
        FieldPlanting.tryPlant(level, h.absolutePos(soil), CultivarSeedItem.of(Genome.base(wheat)), null);
        FieldCropBlockEntity crop = (FieldCropBlockEntity) h.getBlockEntity(soil.above());
        for (int i = 0; i < 4000 && !crop.isMature(); i++) {
            crop.randomTick(level, level.random);
        }
        h.assertTrue(crop.isMature(), "wheat matures on moist lit farmland");
        h.succeed();
    }
}
