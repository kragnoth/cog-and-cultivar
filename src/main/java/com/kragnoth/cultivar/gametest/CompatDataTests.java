package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional compat data loads only with its mods; CI runs this both with and without them. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatDataTests {
    private CompatDataTests() {}

    @GameTest(template = "empty")
    public static void delightDataFollowsModPresence(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        var plants = access.registryOrThrow(CultivarRegistries.PLANT);
        var soils = access.registryOrThrow(CultivarRegistries.SOIL);
        var mutations = access.registryOrThrow(CultivarRegistries.MUTATION);
        String[][] expected = {
                {"farmersdelight", "tomato"}, {"expandeddelight", "peanut"},
                {"rusticdelight", "coffee"}, {"mynethersdelight", "bullet_pepper"}};
        for (String[] e : expected) {
            boolean loaded = ModList.get().isLoaded(e[0]);
            boolean present = plants.containsKey(ResourceLocation.fromNamespaceAndPath(e[0], e[1]));
            h.assertTrue(present == loaded, e[0] + ":" + e[1] + (loaded ? " should load" : " should be skipped"));
            boolean mutation = mutations.containsKey(Cultivar.id(e[0] + "/" + e[1]));
            h.assertTrue(mutation == loaded, "mutation for " + e[0] + ":" + e[1] + " follows the mod");
        }
        boolean fd = ModList.get().isLoaded("farmersdelight");
        h.assertTrue(soils.containsKey(ResourceLocation.fromNamespaceAndPath("farmersdelight", "rich_soil")) == fd, "rich soil follows FD");
        h.succeed();
    }
}
