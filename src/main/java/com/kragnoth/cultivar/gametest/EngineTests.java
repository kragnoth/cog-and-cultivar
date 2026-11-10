package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.genetics.AllelePair;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.condition.ToleranceWindow;
import com.kragnoth.cultivar.content.core.genetics.BreedingEngine;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.core.growth.GrowthMath;
import com.kragnoth.cultivar.content.core.influence.InfluenceMath;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Engine maths, checked against the numbers promised in docs/design-spec.md.
 * Seeded randoms keep the statistical tests deterministic.
 */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EngineTests {
    private static final InfluenceEffects NONE = InfluenceEffects.NONE;
    /** Full-strength beehive from spec §3.15. */
    private static final InfluenceEffects BEES = new InfluenceEffects(1.0, 1.25, 1, 1, 0.75);
    private static final int TRIALS = 20_000;

    private EngineTests() {}

    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    private static Plant plant(double growthChance, double growthBonus) {
        return new Plant(8, 0, growthChance, growthBonus, true, 0.1, List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), 1, Optional.empty());
    }

    private static Genome genome(Holder<Plant> species, int vigour) {
        return new Genome(species, Map.of(
                CoreModule.VIGOUR.getKey(), AllelePair.of(vigour, vigour),
                CoreModule.GROWTH.getKey(), AllelePair.of(5, 5)));
    }

    @GameTest(template = "empty")
    public static void harvestRollsFollowGain(GameTestHelper h) {
        int[] expected = {0, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4};
        for (int gain = 1; gain <= 10; gain++) {
            h.assertTrue(GrowthMath.harvestRolls(gain) == expected[gain], "harvest rolls at gain " + gain);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void weedChanceAndBeeSuppression(GameTestHelper h) {
        near(h, GrowthMath.weedChance(true, 1, 10, NONE), 0.5, 1e-9, "strength 1");
        near(h, GrowthMath.weedChance(true, 10, 10, NONE), 0.0, 1e-9, "strength 10");
        near(h, GrowthMath.weedChance(false, 1, 10, NONE), 0.5, 1e-9, "empty frame");
        near(h, GrowthMath.weedChance(true, 1, 10, BEES), 0.375, 1e-9, "strength 1 with bees");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void growthChanceFormula(GameTestHelper h) {
        Plant wheat = plant(0.75, 0.025);
        near(h, GrowthMath.growthChance(wheat, 1, 1.0, 1.0, NONE), 0.775, 1e-9, "growth 1");
        near(h, GrowthMath.growthChance(wheat, 10, 1.0, 1.0, NONE), 1.0, 1e-9, "growth 10");
        near(h, GrowthMath.growthChance(wheat, 1, 0.5, 1.0, NONE), 0.3875, 1e-9, "poor soil");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void influenceCapsPreventStacking(GameTestHelper h) {
        InfluenceMath.Caps caps = new InfluenceMath.Caps(4.0, 1.5, 0.5, 3, 3);
        InfluenceEffects two = InfluenceMath.combine(List.of(BEES, BEES), caps);
        near(h, two.mutationMultiplier(), 1.5, 1e-9, "mutation capped");
        near(h, two.weedMultiplier(), 0.5625, 1e-9, "weed product");
        InfluenceEffects three = InfluenceMath.combine(List.of(BEES, BEES, BEES), caps);
        near(h, three.weedMultiplier(), 0.5, 1e-9, "weed floored");
        h.assertTrue(three.fertilityBonus() == 3, "fertility bonus capped at 3");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void toleranceWidensWithStrength(GameTestHelper h) {
        int wet = SoilLevels.Humidity.WET.ordinal();
        int damp = SoilLevels.Humidity.DAMP.ordinal();
        int watery = SoilLevels.Humidity.WATERY.ordinal();
        var eq = ToleranceWindow.Comparison.EQUAL;
        h.assertTrue(ToleranceWindow.accepts(wet, wet, eq, 0.15, 1), "exact match");
        h.assertFalse(ToleranceWindow.accepts(damp, wet, eq, 0.15, 1), "strength 1 rejects neighbour");
        h.assertTrue(ToleranceWindow.accepts(damp, wet, eq, 0.15, 7), "strength 7 accepts DAMP");
        h.assertTrue(ToleranceWindow.accepts(watery, wet, eq, 0.15, 7), "strength 7 accepts WATERY");
        h.assertTrue(ToleranceWindow.accepts(SoilLevels.Humidity.FLOODED.ordinal(), wet,
                ToleranceWindow.Comparison.AT_LEAST, 0.15, 1), "AT_LEAST opens the top");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void statDriftMatchesSpec(GameTestHelper h) {
        RandomSource r = RandomSource.create(1234L);
        var growth = CoreModule.GROWTH.getKey();
        int up = 0, drifted = 0, topUp = 0;
        for (int i = 0; i < TRIALS; i++) {
            if (BreedingEngine.drift(growth, 5, 10, r) == 6) up++;
            if (BreedingEngine.drift(growth, 5, 1, r) != 5) drifted++;
            if (BreedingEngine.drift(growth, 9, 1, r) == 10) topUp++;
        }
        // Vigour 10 at allele 5: drifts 45 %, up 73 % of that. Vigour 1: drifts 27 %. The last step is the hard one:
        // allele 9 at Vigour 1 reaches 10 with 27 % x 35 %.
        near(h, up / (double) TRIALS, 0.45 * 0.73, 0.015, "vigour 10 step-up rate");
        near(h, drifted / (double) TRIALS, 0.27, 0.015, "vigour 1 drift rate");
        near(h, topUp / (double) TRIALS, 0.27 * 0.35, 0.012, "last step to max");
        Genome high = genome(Holder.direct(plant(0.75, 0.025)), 10);
        Genome low = genome(Holder.direct(plant(0.75, 0.025)), 1);
        var settings = new BreedingEngine.Settings(Genetics.Expression.MAX, NONE, false);
        double highSum = 0, lowSum = 0;
        for (int i = 0; i < 2000; i++) {
            highSum += BreedingEngine.breed(List.of(high, high), List.of(), m -> false, settings, r).orElseThrow().get(growth).dominant();
            var c = BreedingEngine.breed(List.of(low, low), List.of(), m -> false, settings, r);
            lowSum += c.isPresent() ? c.get().get(growth).dominant() : 5;
        }
        h.assertTrue(highSum > lowSum, "high-vigour parents breed better children on average");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mutationRollAndBeeBoost(GameTestHelper h) {
        Holder<Plant> a = Holder.direct(plant(0.75, 0.025));
        Holder<Plant> b = Holder.direct(plant(0.75, 0.025));
        Holder<Plant> child = Holder.direct(plant(0.75, 0.025));
        Mutation mutation = new Mutation(a, b, child, 0.25, List.of());
        Genome pa = genome(a, 10);
        Genome pb = genome(b, 10);
        RandomSource r = RandomSource.create(99L);
        int plain = 0, withBees = 0;
        var noBees = new BreedingEngine.Settings(Genetics.Expression.MAX, NONE, false);
        var bees = new BreedingEngine.Settings(Genetics.Expression.MAX, BEES, false);
        for (int i = 0; i < TRIALS; i++) {
            Optional<Genome> g1 = BreedingEngine.breed(List.of(pa, pb), List.of(mutation), m -> true, noBees, r);
            if (g1.isPresent() && g1.get().species() == child) plain++;
            Optional<Genome> g2 = BreedingEngine.breed(List.of(pa, pb), List.of(mutation), m -> true, bees, r);
            if (g2.isPresent() && g2.get().species() == child) withBees++;
        }
        near(h, plain / (double) TRIALS, 0.25, 0.015, "base mutation chance");
        near(h, withBees / (double) TRIALS, 0.3125, 0.015, "mutation chance with bees");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pollinationRaisesParentSelection(GameTestHelper h) {
        Holder<Plant> sp = Holder.direct(plant(0.75, 0.025));
        Genome parent = genome(sp, 3);
        RandomSource r = RandomSource.create(7L);
        int crossesPlain = 0, crossesBees = 0;
        var noBees = new BreedingEngine.Settings(Genetics.Expression.MAX, NONE, false);
        var bees = new BreedingEngine.Settings(Genetics.Expression.MAX, BEES, false);
        Plant noClone = new Plant(8, 0, 0.75, 0.025, false, 0, List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), 1, Optional.empty());
        Genome p = new Genome(Holder.direct(noClone), parent.stats());
        for (int i = 0; i < TRIALS; i++) {
            if (BreedingEngine.breed(List.of(p, p), List.of(), m -> false, noBees, r).isPresent()) crossesPlain++;
            if (BreedingEngine.breed(List.of(p, p), List.of(), m -> false, bees, r).isPresent()) crossesBees++;
        }
        // Each parent passes with 0.5 + 0.05 x Vigour: vigour 3 → 0.65² ≈ 42 %; +1 pollination → 0.7² = 49 %.
        near(h, crossesPlain / (double) TRIALS, 0.4225, 0.02, "two-parent cross rate, no bees");
        near(h, crossesBees / (double) TRIALS, 0.49, 0.02, "two-parent cross rate, bees");
        h.succeed();
    }
}
