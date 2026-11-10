package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.compat.create.CultivarAttributes;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreateFarmingTests {
    private CreateFarmingTests() {}

    @GameTest(template = "empty")
    public static void filterAttributesReadGenetics(GameTestHelper h) {
        var level = h.getLevel();
        var wheat = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace("wheat")));
        ItemStack raw = CultivarSeedItem.of(Genome.base(wheat));
        ItemStack assayed = CultivarSeedItem.of(Genome.base(wheat), true);

        var species = new CultivarAttributes.Species(ResourceLocation.withDefaultNamespace("wheat"));
        var carrot = new CultivarAttributes.Species(ResourceLocation.withDefaultNamespace("carrot"));
        h.assertTrue(species.appliesTo(raw, level) && !carrot.appliesTo(raw, level), "species filter");

        var growthOne = new CultivarAttributes.StatAtLeast(CoreModule.GROWTH.getId(), 1);
        h.assertTrue(growthOne.appliesTo(assayed, level), "stat filter reads assayed seeds");
        h.assertTrue(!growthOne.appliesTo(raw, level), "stat filter can't read unassayed seeds");
        h.assertTrue(!new CultivarAttributes.StatAtLeast(CoreModule.GROWTH.getId(), 2).appliesTo(assayed, level), "threshold respected");
        h.assertTrue(CultivarAttributes.Assayed.INSTANCE.appliesTo(assayed, level)
                && !CultivarAttributes.Assayed.INSTANCE.appliesTo(raw, level), "assayed filter");
        h.assertTrue(CultivarAttributes.STAT_AT_LEAST.get().getAllAttributes(raw, level).isEmpty()
                && CultivarAttributes.STAT_AT_LEAST.get().getAllAttributes(assayed, level).size() == 4,
                "stat options only offered for assayed items, one per stat");
        h.assertTrue(level.getRecipeManager().byKey(Cultivar.id("milling/seeds")).isPresent(), "millstone cull recipe loaded");
        h.succeed();
    }
}
