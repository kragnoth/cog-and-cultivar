package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.api.plant.Plant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A player's botanical knowledge: every species they have learned. It belongs to the player (kept through death)
 * and is what JEI and the Ledger screen show. Botanical Ledgers are how knowledge moves: recording into a Ledger,
 * or carrying/reading one, merges it with its holder's knowledge both ways, so a shared Ledger teaches its reader.
 */
public final class Knowledge {
    private Knowledge() {}

    public static LedgerItem.Data get(Player player) {
        return player.getData(LedgerModule.KNOWLEDGE);
    }

    public static boolean knows(Player player, ResourceKey<Plant> plant) {
        return get(player).knows(plant);
    }

    /** Adds species to a player's knowledge and syncs if anything was new. Returns the number added. */
    public static int learn(ServerPlayer player, Collection<ResourceKey<Plant>> plants) {
        LedgerItem.Data current = get(player);
        Set<ResourceKey<Plant>> merged = new HashSet<>(current.researched());
        int before = merged.size();
        merged.addAll(plants);
        if (merged.size() == before) {
            return 0;
        }
        set(player, new LedgerItem.Data(merged));
        Set<ResourceKey<Plant>> fresh = new HashSet<>(merged);
        fresh.removeAll(current.researched());
        award(player, fresh);
        return merged.size() - before;
    }

    /** Fires {@code cultivar:learned_plant} for newly learned species. */
    public static void award(ServerPlayer player, Set<ResourceKey<Plant>> learned) {
        var access = player.serverLevel().registryAccess();
        var plants = access.registryOrThrow(com.kragnoth.cultivar.api.CultivarRegistries.PLANT);
        var mutations = access.registryOrThrow(com.kragnoth.cultivar.api.CultivarRegistries.MUTATION);
        boolean bred = mutations.stream().anyMatch(m -> m.child().unwrapKey().map(learned::contains).orElse(false));
        long known = get(player).researched().stream().filter(plants::containsKey).count();
        LedgerModule.LEARNED_PLANT.get().trigger(player, learned, bred, plants.size() > 0 && known >= plants.size());
    }

    /** Removes species (or everything when {@code plants} is null) and syncs. Returns the number removed. */
    public static int forget(ServerPlayer player, Collection<ResourceKey<Plant>> plants) {
        Set<ResourceKey<Plant>> kept = new HashSet<>(get(player).researched());
        int before = kept.size();
        if (plants == null) {
            kept.clear();
        } else {
            kept.removeAll(plants);
        }
        set(player, new LedgerItem.Data(kept));
        return before - kept.size();
    }

    /** Merges a Ledger and its holder both ways. Returns true if either side changed. */
    public static boolean exchange(ServerPlayer player, ItemStack ledger) {
        LedgerItem.Data book = LedgerItem.data(ledger);
        boolean learned = learn(player, book.researched()) > 0;
        LedgerItem.Data known = get(player);
        if (known.researched().size() != book.researched().size() || !book.researched().containsAll(known.researched())) {
            ledger.set(LedgerModule.LEDGER_DATA.get(), known);
            return true;
        }
        return learned;
    }

    public static void sync(ServerPlayer player) {
        if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer) && player.connection != null
                && player.connection.hasChannel(KnowledgePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new KnowledgePayload(get(player)));
        }
    }

    private static void set(ServerPlayer player, LedgerItem.Data data) {
        player.setData(LedgerModule.KNOWLEDGE, data);
        sync(player);
    }
}
