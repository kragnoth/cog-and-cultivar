package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cultivar knowledge grant|revoke <players> all|<plant>} and {@code /cultivar knowledge list <player>}.
 * Op level 2: for testing, and for packs that hand out knowledge as quest rewards.
 */
final class KnowledgeCommand {
    private static final DynamicCommandExceptionType UNKNOWN =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.cultivar.knowledge.unknown", id));

    private KnowledgeCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cultivar").then(Commands.literal("knowledge").requires(s -> s.hasPermission(2))
                .then(Commands.literal("grant").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.literal("all").executes(c -> change(c, true, all(c))))
                        .then(Commands.argument("plant", ResourceLocationArgument.id()).suggests(KnowledgeCommand::suggest)
                                .executes(c -> change(c, true, List.of(plant(c)))))))
                .then(Commands.literal("revoke").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.literal("all").executes(c -> change(c, false, null)))
                        .then(Commands.argument("plant", ResourceLocationArgument.id()).suggests(KnowledgeCommand::suggest)
                                .executes(c -> change(c, false, List.of(plant(c)))))))
                .then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player())
                        .executes(KnowledgeCommand::list))));
    }

    private static int change(CommandContext<CommandSourceStack> c, boolean grant, Collection<ResourceKey<Plant>> plants)
            throws CommandSyntaxException {
        int total = 0;
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer player : players) {
            total += grant ? Knowledge.learn(player, plants) : Knowledge.forget(player, plants);
        }
        int changed = total;
        c.getSource().sendSuccess(() -> Component.translatable(grant ? "commands.cultivar.knowledge.granted"
                : "commands.cultivar.knowledge.revoked", changed, players.size()), true);
        return total;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        var known = Knowledge.get(player).researched().stream().map(ResourceKey::location).sorted().toList();
        int total = c.getSource().registryAccess().registryOrThrow(CultivarRegistries.PLANT).size();
        Component names = Component.literal(String.join(", ", known.stream().map(ResourceLocation::toString).toList()));
        c.getSource().sendSuccess(() -> Component.translatable("commands.cultivar.knowledge.list",
                player.getDisplayName(), known.size(), total).append(" ").append(names), false);
        return known.size();
    }

    private static List<ResourceKey<Plant>> all(CommandContext<CommandSourceStack> c) {
        return c.getSource().registryAccess().registryOrThrow(CultivarRegistries.PLANT).registryKeySet().stream().toList();
    }

    private static ResourceKey<Plant> plant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(c, "plant");
        if (!c.getSource().registryAccess().registryOrThrow(CultivarRegistries.PLANT).containsKey(id)) {
            throw UNKNOWN.create(id);
        }
        return ResourceKey.create(CultivarRegistries.PLANT, id);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggestResource(
                c.getSource().registryAccess().registryOrThrow(CultivarRegistries.PLANT).keySet(), b);
    }
}
