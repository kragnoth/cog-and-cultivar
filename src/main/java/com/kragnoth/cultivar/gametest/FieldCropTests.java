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

    private static FieldCropBlockEntity fieldCrop(GameTestHelper h, BlockPos at, Holder<Plant> plant, int stage, int room) {
        h.setBlock(at.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        for (int i = 1; i < room; i++) {
            h.setBlock(at.above(i), Blocks.AIR);
        }
        h.setBlock(at, FieldCropModule.FIELD_CROP.get());
        FieldCropBlockEntity crop = (FieldCropBlockEntity) h.getBlockEntity(at);
        crop.plant().set(Genome.base(plant), stage, false);
        return crop;
    }

    /** Tall plants stack field-crop segments like cane; breaking a segment cuts the plant down to below it. */
    @GameTest(template = "empty")
    public static void tallFieldCropsStackAndCut(GameTestHelper h) {
        var level = h.getLevel();
        Holder<Plant> cane = plant(h, "minecraft:sugar_cane");
        int mature = cane.value().matureStage();
        int tall = cane.value().blocksAt(mature);
        BlockPos base = new BlockPos(1, 1, 1);
        FieldCropBlockEntity crop = fieldCrop(h, base, cane, mature, tall);
        for (int i = 1; i < tall; i++) {
            h.assertTrue(h.getBlockState(base.above(i)).is(FieldCropModule.FIELD_CROP.get())
                    && h.getBlockState(base.above(i)).getValue(FieldCropBlock.UPPER), "segment " + i + " placed");
        }
        level.destroyBlock(h.absolutePos(base.above(tall - 1)), true);
        h.assertTrue(cane.value().blocksAt(crop.stage()) <= tall - 1, "breaking the top segment cut the plant down");
        h.assertTrue(h.getBlockState(base).is(FieldCropModule.FIELD_CROP.get()), "the base stays");

        crop.plant().setStage(mature);
        List<ItemStack> drops = new ArrayList<>();
        h.assertTrue(FieldCropModule.FIELD_CROP.get().harvestInPlace(level, h.absolutePos(base.above()), drops::add),
                "harvesting through an upper segment harvests the plant");
        int after = cane.value().blocksAt(crop.stage());
        h.assertTrue(!drops.isEmpty() && after < tall, "harvest yields produce and leaves a shorter plant");
        h.assertBlockNotPresent(FieldCropModule.FIELD_CROP.get(), base.above(after));
        h.succeed();
    }

    /** Shears, spade, satchel and a trellis all work on field crops. */
    @GameTest(template = "empty")
    public static void toolsWorkOnFieldCrops(GameTestHelper h) {
        var level = h.getLevel();
        Holder<Plant> wheat = plant(h, "minecraft:wheat");
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        java.util.function.BiFunction<ItemStack, BlockPos, net.minecraft.world.InteractionResult> use = (stack, pos) -> {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(pos)),
                    net.minecraft.core.Direction.UP, h.absolutePos(pos), false);
            return stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        };

        // Shears: a cutting, one stage back.
        BlockPos a = new BlockPos(0, 1, 0);
        FieldCropBlockEntity clipped = fieldCrop(h, a, wheat, 4, 1);
        use.apply(new ItemStack(com.kragnoth.cultivar.content.tools.ToolsModule.PRUNING_SHEARS.get()), a);
        h.assertTrue(clipped.stage() == 3, "shears clip a field crop back a stage");

        // Spade: lift (block goes, soil stays), then set down on other farmland at the same stage.
        BlockPos b = new BlockPos(2, 1, 0), c = new BlockPos(4, 1, 0);
        fieldCrop(h, b, wheat, 5, 1);
        ItemStack spade = new ItemStack(com.kragnoth.cultivar.content.tools.ToolsModule.TRANSPLANTING_SPADE.get());
        use.apply(spade, b);
        h.assertBlockNotPresent(FieldCropModule.FIELD_CROP.get(), b);
        h.assertBlockPresent(Blocks.FARMLAND, b.below());
        h.setBlock(c.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(c, Blocks.AIR);
        use.apply(spade, c.below());
        h.assertTrue(h.getBlockEntity(c) instanceof FieldCropBlockEntity moved && moved.stage() == 5, "spade sets the plant down at its stage");

        // Satchel on a ripe field crop: harvest and gather seed; it stays planted.
        BlockPos d = new BlockPos(0, 1, 2);
        FieldCropBlockEntity ripe = fieldCrop(h, d, wheat, wheat.value().matureStage(), 1);
        use.apply(new ItemStack(com.kragnoth.cultivar.content.tools.ToolsModule.SEED_SATCHEL.get()), d);
        h.assertTrue(ripe.plant().hasPlant() && !ripe.isMature(), "satchel harvests a ripe field crop");

        // Trellis takeover: the plant moves into the trellis with its growth.
        BlockPos e = new BlockPos(2, 1, 2);
        FieldCropBlockEntity field = fieldCrop(h, e, wheat, 4, 1);
        com.kragnoth.cultivar.content.field.FieldTakeover.intoTrellis(level, field,
                new ItemStack(com.kragnoth.cultivar.content.crop.CropModule.TIMBER_TRELLIS.get()), player);
        h.assertTrue(h.getBlockEntity(e) instanceof com.kragnoth.cultivar.content.crop.CropBlockEntity trellis
                && trellis.hasPlant() && trellis.stage() == 4, "a trellis takes over the field crop at its stage");
        h.succeed();
    }
}
