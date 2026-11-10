package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.ledger.Knowledge;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Per-player knowledge: Ledgers merge with their holder both ways, and the knowledge commands work. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnowledgeTests {
    private KnowledgeTests() {}

    private static ResourceKey<Plant> key(String id) {
        return ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace(id));
    }

    /** A fake player, so no login events (or other mods' login packets) fire. */
    private static net.minecraft.server.level.ServerPlayer fake(GameTestHelper h, String suffix) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("cultivar-knowledge-" + suffix).getBytes()),
                        "cultivar_knowledge_" + suffix));
    }

    @GameTest(template = "empty")
    public static void ledgerAndPlayerShareKnowledge(GameTestHelper h) {
        var player = fake(h, "a");
        ItemStack ledger = new ItemStack(LedgerModule.LEDGER.get());
        LedgerItem.record(ledger, key("wheat"));
        Knowledge.exchange(player, ledger);
        h.assertTrue(Knowledge.knows(player, key("wheat")), "reading a Ledger teaches the player");

        Knowledge.learn(player, List.of(key("carrot")));
        Knowledge.exchange(player, ledger);
        h.assertTrue(LedgerItem.data(ledger).knows(key("carrot")), "the Ledger picks up what the player knows");

        ItemStack blank = new ItemStack(LedgerModule.LEDGER.get());
        Knowledge.exchange(player, blank);
        h.assertTrue(LedgerItem.data(blank).researched().size() == 2, "a new Ledger is filled from the player");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void knowledgeCommandsGrantAndRevoke(GameTestHelper h) {
        var player = fake(h, "b");
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput().withEntity(player);
        String name = "@s";
        int plants = h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT).size();

        server.getCommands().performPrefixedCommand(source, "cultivar knowledge grant " + name + " minecraft:wheat");
        h.assertTrue(Knowledge.get(player).researched().size() == 1, "grant one plant");
        server.getCommands().performPrefixedCommand(source, "cultivar knowledge grant " + name + " all");
        h.assertTrue(Knowledge.get(player).researched().size() == plants, "grant all plants");
        server.getCommands().performPrefixedCommand(source, "cultivar knowledge revoke " + name + " minecraft:wheat");
        h.assertTrue(!Knowledge.knows(player, key("wheat")), "revoke one plant");
        server.getCommands().performPrefixedCommand(source, "cultivar knowledge revoke " + name + " all");
        h.assertTrue(Knowledge.get(player).researched().isEmpty(), "revoke all");
        h.succeed();
    }
}
