package com.kragnoth.cultivar.gametest;

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
}
