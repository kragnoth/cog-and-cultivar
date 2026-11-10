package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import com.kragnoth.cultivar.content.trial.TrialBedBlockEntity;
import com.kragnoth.cultivar.content.trial.TrialModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrialBedTests {
    private TrialBedTests() {}

    private static ResourceKey<Plant> key(String path) {
        return ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace(path));
    }

    @GameTest(template = "empty")
    public static void trialPlotDiscoversMutationsIntoTheLedger(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 1; x <= 3; x++) {
            h.setBlock(new BlockPos(x, 1, 1), TrialModule.TRIAL_BED.get());
        }
        TrialBedBlockEntity bed = (TrialBedBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(3, 1, 1)), Direction.UP);
        h.assertTrue(items.getSlots() == 3 * TrialBedBlockEntity.SLOTS, "touching trial beds form one plot");
        for (ItemStack in : new ItemStack[]{new ItemStack(Items.WHEAT_SEEDS, 4), new ItemStack(Items.POTATO, 4),
                new ItemStack(LedgerModule.LEDGER.get())}) {
            ItemStack rest = in;
            for (int i = 0; i < items.getSlots() && !rest.isEmpty(); i++) {
                rest = items.insertItem(i, rest, false);
            }
            h.assertTrue(rest.isEmpty(), "fed " + in);
        }
        h.assertTrue(Math.abs(bed.rollChance() - 0.05 * 3 / 81.0) < 1e-9, "chance scales with plot size toward a 9x9");

        var found = bed.roll(level, RandomSource.create(1L), 1.0);
        h.assertTrue(found.isPresent() && found.get().child().is(key("beetroot")), "wheat x potato tested: beetroot found");
        ItemStack ledger = items.extractItem(TrialBedBlockEntity.LEDGER, 1, true).isEmpty()
                ? bed.inventory.getStackInSlot(TrialBedBlockEntity.LEDGER) : null;
        for (int i = 0; ledger == null && i < items.getSlots(); i++) {
            if (items.getStackInSlot(i).getItem() instanceof LedgerItem) {
                ledger = items.getStackInSlot(i);
            }
        }
        var data = LedgerItem.data(ledger);
        h.assertTrue(data.knows(key("wheat")) && data.knows(key("potato")) && data.knows(key("beetroot")),
                "the recipe's parents and child are recorded");
        h.assertTrue(bed.roll(level, RandomSource.create(2L), 1.0).isEmpty(), "nothing left to test with this stock");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void trialPlotGrowsAnythingAndRecordsRealMutations(GameTestHelper h) {
        var level = h.getLevel();
        var plants = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT);
        // A row of three: producer, frame, producer (checkerboard by absolute x + z).
        BlockPos start = new BlockPos(1, 1, 1);
        if (((h.absolutePos(start).getX() + h.absolutePos(start).getZ()) & 1) == 1) {
            start = start.east();
        }
        BlockPos left = start, mid = start.east(), right = start.east(2);
        for (BlockPos p : new BlockPos[]{left, mid, right}) {
            h.setBlock(p, TrialModule.TRIAL_BED.get());
            h.setBlock(p.above(), net.minecraft.world.level.block.Blocks.AIR); // test frame's barrier roof
        }
        TrialBedBlockEntity l = (TrialBedBlockEntity) h.getBlockEntity(left);
        TrialBedBlockEntity m = (TrialBedBlockEntity) h.getBlockEntity(mid);
        TrialBedBlockEntity r = (TrialBedBlockEntity) h.getBlockEntity(right);
        l.inventory.setStackInSlot(TrialBedBlockEntity.LEDGER, new ItemStack(LedgerModule.LEDGER.get()));
        RandomSource random = RandomSource.create(3L);
        for (TrialBedBlockEntity b : new TrialBedBlockEntity[]{l, m, r}) {
            b.work(level, random);
        }
        var T = com.kragnoth.cultivar.content.crop.TrellisBlock.CROSS;
        h.assertTrue(!h.getBlockState(left.above()).getValue(T) && h.getBlockState(mid.above()).getValue(T)
                && !h.getBlockState(right.above()).getValue(T), "trellises set up as producer / frame / producer");

        // Nether wart needs soul sand; on a trial bed it grows on its "farmland" anyway.
        var crop = (com.kragnoth.cultivar.content.crop.CropBlockEntity) h.getBlockEntity(left.above());
        crop.plant(com.kragnoth.cultivar.api.genetics.Genome.base(plants.getHolderOrThrow(key("nether_wart"))), 0);
        for (int i = 0; i < 400 && crop.stage() == 0; i++) {
            crop.randomTick(level, random);
        }
        h.assertTrue(crop.stage() > 0, "limiters removed: nether wart grows off soul sand");

        // Wheat and potato mature beside the frame; a beetroot child appears on it as if bred.
        crop.uproot();
        crop.plant(com.kragnoth.cultivar.api.genetics.Genome.base(plants.getHolderOrThrow(key("wheat"))), 7);
        ((com.kragnoth.cultivar.content.crop.CropBlockEntity) h.getBlockEntity(right.above()))
                .plant(com.kragnoth.cultivar.api.genetics.Genome.base(plants.getHolderOrThrow(key("potato"))), 7);
        h.setBlock(mid.above(), h.getBlockState(mid.above()).setValue(T, false));
        ((com.kragnoth.cultivar.content.crop.CropBlockEntity) h.getBlockEntity(mid.above()))
                .plant(com.kragnoth.cultivar.api.genetics.Genome.base(plants.getHolderOrThrow(key("beetroot"))), 0);
        m.work(level, random);
        var data = LedgerItem.data(l.inventory.getStackInSlot(TrialBedBlockEntity.LEDGER));
        h.assertTrue(data.knows(key("wheat")) && data.knows(key("potato")) && data.knows(key("beetroot")),
                "the real cross is recorded in the Ledger");
        h.assertTrue(h.getBlockState(mid.above()).getValue(T), "the frame is cleared and re-armed");
        h.succeed();
    }
}
