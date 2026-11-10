package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldgenTests {
    private WorldgenTests() {}

    @GameTest(template = "empty")
    public static void derelictConservatoriesLoad(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var access = server.registryAccess();
        h.assertTrue(access.registryOrThrow(Registries.STRUCTURE).containsKey(Cultivar.id("derelict_conservatory")), "structure registered");
        h.assertTrue(access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(Cultivar.id("derelict_conservatories")), "structure set registered");
        h.assertTrue(access.registryOrThrow(Registries.PROCESSOR_LIST).containsKey(Cultivar.id("derelict_decay")), "decay processor registered");
        for (String piece : new String[]{"greenhouse", "lean_to"}) {
            StructureTemplate t = server.getStructureManager().get(Cultivar.id("derelict_conservatory/" + piece)).orElse(null);
            h.assertTrue(t != null && t.getSize().getX() > 5, piece + " template loads");
        }
        var loot = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Cultivar.id("chests/derelict_conservatory")));
        h.assertTrue(loot != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "chest loot table loads");
        h.succeed();
    }
}
