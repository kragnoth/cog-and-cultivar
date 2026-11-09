package com.kragnoth.cultivar.content.core.genetics;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.AllelePair;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.core.CoreModule;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Cross-breeding on a grafting frame (spec §2.5). Pure: no world access, all randomness from the
 * supplied {@link RandomSource}, so results are reproducible in tests.
 */
public final class BreedingEngine {
    private BreedingEngine() {}

    /**
     * @param influence      combined crop influence at the grafting frame (bees etc.), already capped
     * @param cloneMutations whether a single-parent clone may still drift stats
     */
    public record Settings(Genetics.Expression expression, InfluenceEffects influence, boolean cloneMutations) {}

    /**
     * Attempts one breeding tick.
     *
     * @param neighbours        genomes of the adjacent crops that are mature (caller filters validity)
     * @param mutations         all mutation entries
     * @param conditionsMet     tests a mutation's extra conditions at the frame's position
     * @return the offspring genome, or empty if nothing happened this tick
     */
    public static Optional<Genome> breed(List<Genome> neighbours, Collection<Mutation> mutations,
                                         Predicate<Mutation> conditionsMet, Settings settings, RandomSource random) {
        List<Genome> parents = selectParents(neighbours, settings, random);
        if (parents.isEmpty()) {
            return Optional.empty();
        }
        if (parents.size() == 1) {
            return cloneParent(parents.getFirst(), settings, random);
        }
        return Optional.of(combine(parents.get(0), parents.get(1), mutations, conditionsMet, settings, random));
    }

    /** Sorted by fertility (high first); each passes with chance (fertility + pollination bonus) / max. */
    static List<Genome> selectParents(List<Genome> neighbours, Settings settings, RandomSource random) {
        ResourceKey<Stat> fertility = CoreModule.FERTILITY.getKey();
        int max = Genetics.max(fertility);
        List<Genome> sorted = new ArrayList<>(neighbours);
        sorted.sort(Comparator.comparingInt((Genome g) -> Genetics.expressed(g, fertility, settings.expression())).reversed());
        List<Genome> passed = new ArrayList<>(2);
        for (Genome genome : sorted) {
            int effective = Math.min(max, Genetics.expressed(genome, fertility, settings.expression())
                    + settings.influence().fertilityBonus());
            if (random.nextInt(max) < effective) {
                passed.add(genome);
                if (passed.size() == 2) {
                    break;
                }
            }
        }
        return passed;
    }

    static Optional<Genome> cloneParent(Genome parent, Settings settings, RandomSource random) {
        Plant plant = parent.species().value();
        if (!plant.cloneable() || random.nextDouble() >= plant.spreadChance()) {
            return Optional.empty();
        }
        if (!settings.cloneMutations()) {
            return Optional.of(parent);
        }
        return Optional.of(combine(parent, parent, List.of(), m -> false, settings, random));
    }

    static Genome combine(Genome a, Genome b, Collection<Mutation> mutations, Predicate<Mutation> conditionsMet,
                          Settings settings, RandomSource random) {
        Holder<Plant> species = pickSpecies(a.species(), b.species(), mutations, conditionsMet, settings, random);
        Map<ResourceKey<Stat>, AllelePair> stats = new HashMap<>();
        CultivarRegistries.STAT.registryKeySet().forEach(stat -> {
            int fromA = drift(stat, pickAllele(a.get(stat), random), mutativity(a, settings), random);
            int fromB = drift(stat, pickAllele(b.get(stat), random), mutativity(b, settings), random);
            stats.put(stat, AllelePair.of(fromA, fromB));
        });
        return new Genome(species, stats);
    }

    /** Matching mutations in random order; first one that passes its conditions and its chance roll wins. */
    static Holder<Plant> pickSpecies(Holder<Plant> a, Holder<Plant> b, Collection<Mutation> mutations,
                                     Predicate<Mutation> conditionsMet, Settings settings, RandomSource random) {
        List<Mutation> matching = new ArrayList<>();
        for (Mutation mutation : mutations) {
            if (mutation.matches(a, b) && conditionsMet.test(mutation)) {
                matching.add(mutation);
            }
        }
        // Fisher-Yates with the supplied random so tests stay deterministic.
        for (int i = matching.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Mutation tmp = matching.get(i);
            matching.set(i, matching.get(j));
            matching.set(j, tmp);
        }
        for (Mutation mutation : matching) {
            double chance = Math.min(1.0, mutation.chance() * settings.influence().mutationMultiplier());
            if (random.nextDouble() < chance) {
                return mutation.child();
            }
        }
        return random.nextBoolean() ? a : b;
    }

    static int pickAllele(AllelePair pair, RandomSource random) {
        return random.nextBoolean() ? pair.dominant() : pair.recessive();
    }

    static int mutativity(Genome parent, Settings settings) {
        return Genetics.expressed(parent, CoreModule.MUTATIVITY.getKey(), settings.expression())
                + settings.influence().mutativityBonus();
    }

    /**
     * Stat drift: with chance {@code m/max} the allele moves one step; it moves up with chance
     * {@code floor((max + m) / 2) / max}, otherwise down. m=1 gives 10 % drift, 50/50; m=10 gives always up.
     */
    static int drift(ResourceKey<Stat> stat, int allele, int mutativity, RandomSource random) {
        int max = Genetics.max(stat);
        int m = Math.min(max, mutativity);
        if (random.nextInt(max) >= m) {
            return allele;
        }
        int delta = random.nextInt(max) < (max + m) / 2 ? 1 : -1;
        return Genetics.clamp(stat, allele + delta);
    }
}
