package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.content.bed.PlanterBedBlockEntity;
import com.kragnoth.cultivar.content.bed.PlanterBedModule;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanterBedTests {
    private PlanterBedTests() {}

    @GameTest(template = "empty")
    public static void bedPlotPlantsWatersHarvestsAndShares(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        h.setBlock(a, PlanterBedModule.BED.get());
        h.setBlock(b, PlanterBedModule.BED.get());
        PlanterBedBlockEntity bedA = (PlanterBedBlockEntity) h.getBlockEntity(a);
        PlanterBedBlockEntity bedB = (PlanterBedBlockEntity) h.getBlockEntity(b);
        bedA.setSoil(Blocks.FARMLAND.defaultBlockState());
        h.setBlock(a.above(), CropModule.TIMBER_TRELLIS.get());

        // Feed through bed B: the whole plot shares it.
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(b), Direction.UP);
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(b), Direction.UP);
        h.assertTrue(items.getSlots() == 2 * PlanterBedBlockEntity.SLOTS, "two touching beds form one plot");
        ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS, 4);
        for (int i = 0; i < items.getSlots() && !seeds.isEmpty(); i++) {
            seeds = items.insertItem(i, seeds, false);
        }
        h.assertTrue(seeds.isEmpty(), "vanilla seeds accepted");
        h.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 5000), IFluidHandler.FluidAction.EXECUTE) == 5000, "water spreads across the plot");
        h.assertTrue(bedA.tank.getFluidAmount() == 2500 && bedB.tank.getFluidAmount() == 2500, "one pipe waters the plot evenly");

        CropBlockEntity crop = (CropBlockEntity) h.getBlockEntity(a.above());
        h.assertTrue(crop.soil().isPresent(), "the bed acts as its soil");
        bedA.work(level, true);
        h.assertTrue(crop.hasPlant(), "bed A planted from seeds stored in bed B");

        crop.plant(crop.genome(), crop.genome().species().value().matureStage());
        crop.uproot();
        crop.plant(com.kragnoth.cultivar.api.genetics.Genome.base(level.registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("wheat")))), 7);
        bedA.work(level, true);
        boolean gotWheat = false;
        for (int i = 0; i < items.getSlots(); i++) {
            if (items.extractItem(i, 64, true).is(Items.WHEAT)) {
                gotWheat = true;
            }
        }
        h.assertTrue(gotWheat && !crop.isMature(), "mature crop harvested into the plot outputs");
        h.assertTrue(com.kragnoth.cultivar.content.core.condition.Hydration.effective(level, h.absolutePos(a),
                com.kragnoth.cultivar.api.soil.SoilLevels.Humidity.WET) == com.kragnoth.cultivar.api.soil.SoilLevels.Humidity.WATERY,
                "a watered bed is one humidity step wetter");
        h.succeed();
    }

    private static com.kragnoth.cultivar.api.genetics.Genome wheat(GameTestHelper h, int gain) {
        var wheat = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("wheat")));
        var stats = new java.util.HashMap<>(com.kragnoth.cultivar.api.genetics.Genome.base(wheat).stats());
        stats.put(com.kragnoth.cultivar.content.core.CoreModule.GAIN.getKey(), new com.kragnoth.cultivar.api.genetics.AllelePair(gain, gain));
        return new com.kragnoth.cultivar.api.genetics.Genome(wheat, stats);
    }

    @GameTest(template = "empty")
    public static void bedPlacesAndStripsTrellisAndCloche(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, PlanterBedModule.BED.get());
        h.setBlock(pos.above(), Blocks.AIR); // the test frame's barrier roof sits here
        PlanterBedBlockEntity bed = (PlanterBedBlockEntity) h.getBlockEntity(pos);
        bed.setSoil(Blocks.FARMLAND.defaultBlockState());
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), Direction.UP);
        h.assertTrue(items.insertItem(PlanterBedBlockEntity.TRELLIS, new ItemStack(CropModule.TIMBER_TRELLIS.get()), false).isEmpty(),
                "pipes can feed trellises");
        h.assertTrue(items.insertItem(PlanterBedBlockEntity.CLOCHE, new ItemStack(com.kragnoth.cultivar.content.cloche.ClocheModule.BELL_CLOCHE.get()), false).isEmpty(),
                "pipes can feed cloches");

        bed.work(level, false);
        h.assertTrue(h.getBlockState(pos.above()).is(CropModule.TIMBER_TRELLIS.get()), "a fed trellis is placed on top, found "
                + h.getBlockState(pos.above()) + ", supply " + bed.inventory.getStackInSlot(PlanterBedBlockEntity.TRELLIS));
        bed.work(level, false);
        h.assertTrue(h.getBlockState(pos.above()).getValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CLOCHE), "a fed cloche covers it");
        ((CropBlockEntity) h.getBlockEntity(pos.above())).plant(wheat(h, 4), 3);
        h.assertTrue(items.extractItem(PlanterBedBlockEntity.SEEDS, 1, true).isEmpty()
                && items.extractItem(PlanterBedBlockEntity.FERTILIZER, 1, true).isEmpty(),
                "unpowered: pipes pull only crop outputs");
        bed.inventory.setStackInSlot(PlanterBedBlockEntity.SEEDS, new ItemStack(Items.WHEAT_SEEDS, 3));

        h.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        bed.work(level, false);
        h.assertTrue(!h.getBlockState(pos.above()).getValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CLOCHE)
                && !bed.inventory.getStackInSlot(PlanterBedBlockEntity.CLOCHE).isEmpty(), "redstone takes the cloche back");
        bed.work(level, false);
        h.assertTrue(h.getBlockState(pos.above()).isAir() && !bed.inventory.getStackInSlot(PlanterBedBlockEntity.TRELLIS).isEmpty(),
                "then the trellis");
        h.assertTrue(!bed.inventory.getStackInSlot(PlanterBedBlockEntity.SEEDS).isEmpty(), "the plant is banked as a seed");
        h.assertTrue(!items.extractItem(PlanterBedBlockEntity.TRELLIS, 1, false).isEmpty()
                && !items.extractItem(PlanterBedBlockEntity.SEEDS, 1, false).isEmpty(), "powered: dump mode, every slot can be pulled");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bedUpgradesToBetterSeedsAndBanksBredChildren(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos producer = new BlockPos(1, 1, 1), breeder = new BlockPos(2, 1, 1);
        for (BlockPos p : new BlockPos[]{producer, breeder}) {
            h.setBlock(p, PlanterBedModule.BED.get());
            ((PlanterBedBlockEntity) h.getBlockEntity(p)).setSoil(Blocks.FARMLAND.defaultBlockState());
            h.setBlock(p.above(), CropModule.TIMBER_TRELLIS.get());
        }
        PlanterBedBlockEntity prod = (PlanterBedBlockEntity) h.getBlockEntity(producer);
        PlanterBedBlockEntity breed = (PlanterBedBlockEntity) h.getBlockEntity(breeder);
        CropBlockEntity prodCrop = (CropBlockEntity) h.getBlockEntity(producer.above());
        CropBlockEntity breedCrop = (CropBlockEntity) h.getBlockEntity(breeder.above());

        // Breeder: a grafting frame the bed adopts and re-arms after a child is born.
        h.setBlock(breeder.above(), h.getBlockState(breeder.above()).setValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CROSS, true));
        breed.work(level, false);
        h.assertTrue(breed.isBreeding(), "a bed under a grafting frame becomes a breeder");
        h.setBlock(breeder.above(), h.getBlockState(breeder.above()).setValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CROSS, false));
        breedCrop = (CropBlockEntity) h.getBlockEntity(breeder.above());
        breedCrop.plant(wheat(h, 9), 0); // as if just born
        breed.work(level, false);
        h.assertTrue(h.getBlockState(breeder.above()).getValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CROSS), "frame re-armed");

        // Producer: mature low-Gain wheat; the stock now holds the bred Gain 9 seed. Harvest swaps it in.
        prodCrop.plant(wheat(h, 2), 7);
        for (int i = 0; i < 60 && prodCrop.genome() != null
                && com.kragnoth.cultivar.content.core.genetics.Genetics.expressed(prodCrop.genome(),
                com.kragnoth.cultivar.content.core.CoreModule.GAIN.getKey(),
                com.kragnoth.cultivar.content.core.genetics.Genetics.current()) == 2; i++) {
            prod.work(level, false); // waits out the breeder hold, then harvests and upgrades
        }
        int gain = com.kragnoth.cultivar.content.core.genetics.Genetics.expressed(prodCrop.genome(),
                com.kragnoth.cultivar.content.core.CoreModule.GAIN.getKey(), com.kragnoth.cultivar.content.core.genetics.Genetics.current());
        h.assertTrue(gain == 9, "the producer upgraded to the bred Gain 9 seed, got " + gain);
        h.succeed();
    }
}
