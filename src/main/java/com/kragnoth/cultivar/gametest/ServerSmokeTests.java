package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runs on a real dedicated server in CI ({@code ./gradlew runGameTestServer}). If any common code
 * touches a client-only class, the server crashes before these tests run, and the PR fails.
 * Only active in dev/CI: NeoForge ignores game tests in production.
 */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerSmokeTests {
    private ServerSmokeTests() {}

    @GameTest(template = "empty")
    public static void registriesPresent(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        access.registryOrThrow(CultivarRegistries.SOIL);
        access.registryOrThrow(CultivarRegistries.PLANT);
        access.registryOrThrow(CultivarRegistries.MUTATION);
        access.registryOrThrow(CultivarRegistries.WEED);
        access.registryOrThrow(CultivarRegistries.FERTILIZER);
        access.registryOrThrow(CultivarRegistries.CROP_INFLUENCE);
        helper.assertTrue(CultivarRegistries.STAT.size() >= 4, "Expected the four core stats to be registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modulesLoaded(GameTestHelper helper) {
        helper.assertTrue(Cultivar.modules().stream().anyMatch(m -> m.id().equals("core")), "Core module not loaded");
        helper.succeed();
    }
}
