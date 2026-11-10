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
}
