package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Mutation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The ore-bloom tree loads, every bloom is bred from plants that exist, and no bloom steals another mutation's pair. */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreBloomTests {
    private OreBloomTests() {}

    static final List<String> BLOOMS = List.of("coalroot", "ferrous_bloom", "verdigris_vine", "brassleaf",
            "aurum_thistle", "redstone_fern", "lazuli_bell", "quartz_lily", "emerald_fern", "diamond_briar");

    @GameTest(template = "empty")
    public static void oreBloomTreeLoads(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        var plants = access.registryOrThrow(CultivarRegistries.PLANT);
        var mutations = access.registryOrThrow(CultivarRegistries.MUTATION);
        for (String bloom : BLOOMS) {
            var plant = plants.get(Cultivar.id(bloom));
            h.assertTrue(plant != null, bloom + " should load");
            h.assertTrue(!plant.products().isEmpty() && plant.products().stream().noneMatch(p -> p.item().value() == Items.AIR),
                    bloom + " should drop a real item");
            Mutation m = mutations.get(Cultivar.id(bloom));
            h.assertTrue(m != null, bloom + " should have a mutation");
            h.assertTrue(m.child().is(Cultivar.id(bloom)), bloom + " mutation should breed " + bloom);
            Set<String> pair = pair(m);
            for (var other : mutations.entrySet()) {
                if (other.getValue() == m) continue;
                h.assertTrue(!pair(other.getValue()).equals(pair),
                        bloom + " shares its parents with " + other.getKey().location());
            }
        }
        h.succeed();
    }

    private static Set<String> pair(Mutation m) {
        Set<String> s = new HashSet<>();
        s.add(key(m.parentA()));
        s.add(key(m.parentB()));
        return s;
    }

    private static String key(Holder<?> holder) {
        return holder.unwrapKey().map(k -> k.location().toString()).orElse("?");
    }
}
