package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity.*;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssayTests {
    private AssayTests() {}

    @GameTest(template = "empty")
    public static void engineAssaysAndRecords(GameTestHelper h) {
        ResourceKey<Plant> wheatKey = ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("wheat"));
        Holder<Plant> wheat = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT).getHolderOrThrow(wheatKey);
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, AssayModule.ENGINE.get());
        AssayingEngineBlockEntity be = (AssayingEngineBlockEntity) h.getBlockEntity(pos);

        ItemStack seeds = CultivarSeedItem.of(Genome.base(wheat)).copyWithCount(2);
        h.assertTrue(!CultivarSeedItem.statsVisible(seeds), "fresh seeds are unassayed");
        be.automation.insertItem(SEED_IN, seeds, false);
        be.automation.insertItem(LEDGER, new ItemStack(LedgerModule.LEDGER.get()), false);
        h.assertTrue(be.automation.extractItem(SEED_IN, 1, true).isEmpty(), "inputs cannot be pulled by automation");

        be.advance(0);
        h.assertTrue(be.inventory.getStackInSlot(SEED_OUT).isEmpty(), "no rotation, no work");
        for (int i = 0; i < 26; i++) {
            be.advance(256);
        }
        ItemStack out = be.automation.extractItem(SEED_OUT, 64, false);
        h.assertTrue(out.getCount() == 2 && out.has(CoreModule.ASSAYED.get()), "both seeds come out assayed");
        h.assertTrue(CultivarSeedItem.statsVisible(out), "assayed seeds show stats");
        h.assertTrue(LedgerItem.data(be.inventory.getStackInSlot(LEDGER)).knows(wheatKey), "the ledger recorded wheat");
        h.succeed();
    }

    /** Touching Assayers form one unit: one handler fills them, hand loading spreads, one Ledger serves all. */
    @GameTest(template = "empty")
    public static void unitSharesInputOutputAndLedger(GameTestHelper h) {
        ResourceKey<Plant> wheatKey = ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("wheat"));
        Holder<Plant> wheat = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT).getHolderOrThrow(wheatKey);
        BlockPos a = new BlockPos(0, 1, 1), b = new BlockPos(1, 1, 1), c = new BlockPos(2, 1, 1);
        h.setBlock(a, AssayModule.ENGINE.get());
        h.setBlock(b, AssayModule.BRASS_ENGINE.get());
        h.setBlock(c, AssayModule.CHROMATIC_ENGINE.get());
        AssayingEngineBlockEntity ea = (AssayingEngineBlockEntity) h.getBlockEntity(a),
                eb = (AssayingEngineBlockEntity) h.getBlockEntity(b), ec = (AssayingEngineBlockEntity) h.getBlockEntity(c);
        h.assertTrue(ea.unit().size() == 3, "three touching Assayers form one unit, any tier");
        h.assertTrue(ea.unitHandler().getSlots() == 9, "one handler covers the unit");

        ItemStack rest = eb.insertIntoUnit(CultivarSeedItem.of(Genome.base(wheat)).copyWithCount(9));
        h.assertTrue(rest.isEmpty(), "all seeds fit");
        h.assertTrue(ea.inventory.getStackInSlot(SEED_IN).getCount() == 3 && ec.inventory.getStackInSlot(SEED_IN).getCount() == 3,
                "hand loading spreads seeds across the unit");
        h.assertTrue(ea.insertIntoUnit(new ItemStack(LedgerModule.LEDGER.get())).isEmpty(), "the unit takes a Ledger");
        h.assertTrue(!ec.insertIntoUnit(new ItemStack(LedgerModule.LEDGER.get())).isEmpty(), "but only one");

        // Same RPM, tiers 1x / 2x / 4x: after 4 ticks at 32 RPM andesite is 4% in, chromatic 16%.
        for (int i = 0; i < 4; i++) {
            ea.advance(32);
            eb.advance(32);
            ec.advance(32);
        }
        h.assertTrue(ec.progress() > eb.progress() && eb.progress() > ea.progress(), "higher tiers assay faster");
        for (int i = 0; i < 200; i++) {
            ec.advance(256);
        }
        h.assertTrue(ec.inventory.getStackInSlot(SEED_OUT).getCount() == 3, "chromatic finished its share");
        h.assertTrue(LedgerItem.data(ea.inventory.getStackInSlot(LEDGER)).knows(wheatKey), "the Ledger on another member recorded it");
        var taken = eb.takeFromUnit();
        h.assertTrue(taken.size() == 1 && taken.getFirst().getCount() == 3, "shift + empty hand takes finished seeds first");
        h.succeed();
    }

    /** With a Ledger, an assay can record both parents of a cross that makes the species. */
    @GameTest(template = "empty")
    public static void familyTreeRecordsParents(GameTestHelper h) {
        var mutations = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.MUTATION);
        var m = mutations.stream().findFirst().orElse(null);
        if (m == null) {
            h.succeed();
            return;
        }
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, AssayModule.ENGINE.get());
        AssayingEngineBlockEntity be = (AssayingEngineBlockEntity) h.getBlockEntity(pos);
        ItemStack ledger = new ItemStack(LedgerModule.LEDGER.get());
        var child = m.child().unwrapKey().orElseThrow();
        h.assertTrue(be.recordFamily(ledger, child), "an unknown cross gets recorded");
        var data = LedgerItem.data(ledger);
        boolean anyCross = mutations.stream().anyMatch(x -> x.child().is(child)
                && data.knows(x.parentA().unwrapKey().orElseThrow()) && data.knows(x.parentB().unwrapKey().orElseThrow()));
        h.assertTrue(anyCross, "both parents of a cross that makes it are now known");
        h.succeed();
    }
}
