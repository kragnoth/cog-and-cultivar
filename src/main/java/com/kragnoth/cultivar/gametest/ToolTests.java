package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.AllelePair;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.tools.SeedSatchelItem;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToolTests {
    private ToolTests() {}

    private static Holder<Plant> wheat(GameTestHelper h) {
        return h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("wheat")));
    }

    @GameTest(template = "empty")
    public static void transplantKeepsStage(GameTestHelper h) {
        BlockPos a = new BlockPos(0, 1, 0), b = new BlockPos(2, 1, 0);
        h.setBlock(a.below(), Blocks.FARMLAND);
        h.setBlock(b.below(), Blocks.FARMLAND);
        h.setBlock(a, CropModule.TIMBER_TRELLIS.get());
        h.setBlock(b, CropModule.TIMBER_TRELLIS.get());
        CropBlockEntity from = (CropBlockEntity) h.getBlockEntity(a);
        CropBlockEntity to = (CropBlockEntity) h.getBlockEntity(b);
        from.plant(Genome.base(wheat(h)), 5);
        var lifted = from.uproot().orElseThrow();
        h.assertTrue(!from.hasPlant(), "source trellis is empty after uprooting");
        h.assertTrue(to.plant(lifted.genome(), lifted.stage()) && to.stage() == 5, "replanted at the same stage");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void satchelDialPicksBest(GameTestHelper h) {
        Holder<Plant> wheat = wheat(h);
        Genome weak = new Genome(wheat, Map.of(CoreModule.GROWTH.getKey(), AllelePair.of(2, 2)));
        Genome strong = new Genome(wheat, Map.of(CoreModule.GROWTH.getKey(), AllelePair.of(9, 9)));
        ItemStack satchel = new ItemStack(ToolsModule.SEED_SATCHEL.get());
        SeedSatchelItem.insert(satchel, CultivarSeedItem.of(weak).copyWithCount(3));
        SeedSatchelItem.insert(satchel, CultivarSeedItem.of(strong));
        satchel.set(ToolsModule.SATCHEL_DIAL.get(), new SeedSatchelItem.Dial(CoreModule.GROWTH.getKey()));
        Genome first = SeedSatchelItem.takeOne(satchel).orElseThrow();
        h.assertTrue(first.equals(strong), "dial set to growth takes the growth-9 seed first");
        h.assertTrue(satchel.get(ToolsModule.SATCHEL_CONTENTS.get()).total() == 3, "three seeds remain");
        // Put the strong seed back: growth 2 is more than 2 below growth 9, so all three weak seeds are culls.
        SeedSatchelItem.insert(satchel, CultivarSeedItem.of(strong));
        int culled = 0;
        while (SeedSatchelItem.cullOne(satchel).isPresent()) culled++;
        h.assertTrue(culled == 3, "all three growth-2 seeds culled, got " + culled);
        h.assertTrue(satchel.get(ToolsModule.SATCHEL_CONTENTS.get()).total() == 1, "the keeper remains");
        h.succeed();
    }
}
