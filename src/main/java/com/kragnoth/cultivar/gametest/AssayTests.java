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
}
