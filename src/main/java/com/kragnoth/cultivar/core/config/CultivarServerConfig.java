package com.kragnoth.cultivar.core.config;

import com.kragnoth.cultivar.content.core.genetics.Genetics;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-world gameplay config ({@code <world>/serverconfig/cultivar-server.toml}), synced to clients on join.
 * All gameplay toggles belong here, never in a startup config, so one server can tune its world
 * without clients needing matching files.
 */
public final class CultivarServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue GROWTH_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue WEEDS_ENABLED;
    public static final ModConfigSpec.BooleanValue WATER_SHIFTS_HUMIDITY;
    public static final ModConfigSpec.IntValue CONDITIONER_MAX_STEPS;
    public static final ModConfigSpec.IntValue SHADE_MAX_LIGHT;
    public static final ModConfigSpec.IntValue OLLA_RADIUS;
    public static final ModConfigSpec.IntValue LAMP_MIN_LIGHT;
    public static final ModConfigSpec.IntValue WORK_BUDGET_PER_TICK;
    public static final ModConfigSpec.IntValue BED_CYCLE;
    public static final ModConfigSpec.IntValue BED_TICKS_PER_MB;
    public static final ModConfigSpec.IntValue BED_GROWTH_ODDS;
    public static final ModConfigSpec.IntValue BED_FERTILIZER_ODDS;
    public static final ModConfigSpec.IntValue BED_BREED_HOLD;
    public static final ModConfigSpec.DoubleValue BED_WEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BED_QUALITY_GAIN;
    public static final ModConfigSpec.DoubleValue BED_QUALITY_GROWTH;
    public static final ModConfigSpec.DoubleValue BED_QUALITY_STRENGTH;
    public static final ModConfigSpec.DoubleValue BED_QUALITY_VIGOUR;
    public static final ModConfigSpec.DoubleValue ASSAY_STRESS;
    public static final ModConfigSpec.DoubleValue ASSAY_SPEED_ANDESITE;
    public static final ModConfigSpec.DoubleValue ASSAY_SPEED_BRASS;
    public static final ModConfigSpec.DoubleValue ASSAY_SPEED_CHROMATIC;
    public static final ModConfigSpec.DoubleValue ASSAY_FAMILY_CHANCE;
    public static final ModConfigSpec.IntValue ASSAY_MAX_UNIT;
    public static final ModConfigSpec.IntValue TRIAL_INTERVAL_MINUTES;
    public static final ModConfigSpec.DoubleValue TRIAL_STRESS;
    public static final ModConfigSpec.DoubleValue BED_STRESS;
    public static final ModConfigSpec.DoubleValue TRIAL_CHANCE;
    public static final ModConfigSpec.IntValue TRIAL_FULL_SIZE;
    public static final ModConfigSpec.BooleanValue TRIAL_CONSUMES_SEEDS;
    public static final ModConfigSpec.IntValue TRIAL_HOLD_CYCLES;
    public static final ModConfigSpec.IntValue SPRINKLER_RADIUS;
    public static final ModConfigSpec.IntValue SPRINKLER_INTERVAL;
    public static final ModConfigSpec.IntValue SPRINKLER_ATTEMPTS;
    public static final ModConfigSpec.IntValue SPRINKLER_TICKS_PER_MB;
    public static final ModConfigSpec.DoubleValue SPRINKLER_BONEMEAL_CHANCE;
    public static final ModConfigSpec.EnumValue<Genetics.Expression> STAT_EXPRESSION;
    public static final ModConfigSpec.BooleanValue CLONE_MUTATIONS;
    public static final ModConfigSpec.BooleanValue REQUIRE_ASSAY;
    public static final ModConfigSpec.BooleanValue REVEAL_ALL_MUTATIONS;
    public static final ModConfigSpec.DoubleValue SATCHEL_SEED_BASE;
    public static final ModConfigSpec.DoubleValue SATCHEL_SEED_PER_VIGOUR;
    public static final ModConfigSpec.BooleanValue INFLUENCE_ENABLED;
    public static final ModConfigSpec.DoubleValue MAX_MUTATION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MIN_WEED_MULTIPLIER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("growth");
        GROWTH_MULTIPLIER = b.comment("Global multiplier on the growth stat's contribution to growth chance.")
                .defineInRange("growthMultiplier", 1.0, 0.0, 10.0);
        WEEDS_ENABLED = b.comment("Whether blight/weeds can spawn on crops.")
                .define("weedsEnabled", true);
        WATER_SHIFTS_HUMIDITY = b.comment("Water beside a soil block makes it one humidity step wetter, and a lit heat source beside or under it",
                        "(#cultivar:soil_heaters) one step drier; a Bell Cloche adds one more wet step. Steps add up.",
                        "With no water or heat, dry farmland reads one step drier.")
                .define("waterShiftsHumidity", true);
        b.pop();

        b.push("genetics");
        STAT_EXPRESSION = b.comment("How an allele pair is expressed as one stat value: MAX (dominant), MIN, or MEAN.")
                .defineEnum("statExpression", Genetics.Expression.MAX);
        CLONE_MUTATIONS = b.comment("Whether a single-parent clone can still drift stats.")
                .define("cloneMutations", false);
        REQUIRE_ASSAY = b.comment("Seed item tooltips hide stats until the seed is assayed (Assayer).",
                        "Goggles always read crops in the field.")
                .define("requireAssay", true);
        SATCHEL_SEED_BASE = b.comment("Harvesting a mature plant with the Seed Satchel (hand or Deployer): base chance of a seed.",
                        "Chance = base + perVigour * Vigor. Harvesters, hands and shears never give seeds.")
                .defineInRange("satchelSeedBase", 0.25, 0.0, 1.0);
        SATCHEL_SEED_PER_VIGOUR = b.defineInRange("satchelSeedPerVigour", 0.05, 0.0, 1.0);
        REVEAL_ALL_MUTATIONS = b.comment("Show every cross in JEI and the Ledger, known or not (creative or guide-style packs).",
                        "Off: a cross appears once its child is in the player's knowledge (read from a Botanical Ledger).")
                .define("revealAllMutations", false);
        b.pop();

        b.push("influence");
        INFLUENCE_ENABLED = b.comment("Whether nearby sources (beehives, sprinklers, heaters...) modify crops.")
                .define("influenceEnabled", true);
        MAX_MUTATION_MULTIPLIER = b.comment("Cap on the combined mutation multiplier from all sources affecting one crop.")
                .defineInRange("maxMutationMultiplier", 1.5, 1.0, 4.0);
        MIN_WEED_MULTIPLIER = b.comment("Floor on the combined weed multiplier (lower = stronger suppression).")
                .defineInRange("minWeedMultiplier", 0.5, 0.0, 1.0);
        b.pop();

        b.push("assayer");
        ASSAY_STRESS = b.comment("Stress impact of each Assayer, per RPM (Create units; a Millstone is 4).")
                .defineInRange("stressImpact", 3.0, 0.0, 64.0);
        ASSAY_SPEED_ANDESITE = b.comment("Speed multipliers per tier. At 1.0, 32 RPM assays a seed in 5 s; speed scales with RPM.")
                .defineInRange("speedAndesite", 1.0, 0.01, 64.0);
        ASSAY_SPEED_BRASS = b.defineInRange("speedBrass", 2.0, 0.01, 64.0);
        ASSAY_SPEED_CHROMATIC = b.defineInRange("speedChromatic", 4.0, 0.01, 64.0);
        ASSAY_FAMILY_CHANCE = b.comment("With a Botanical Ledger in the unit: chance per assayed seed to also record one of the",
                        "crosses that produce its species (both parents), so the cross shows up in JEI.")
                .defineInRange("familyTreeChance", 0.2, 0.0, 1.0);
        ASSAY_MAX_UNIT = b.comment("Most Assayers that join into one unit (shared input/output).")
                .defineInRange("maxUnitSize", 81, 1, 256);
        b.pop();

        b.push("trial_bed");
        TRIAL_STRESS = b.comment("Stress impact of each Trial Bed, per RPM. Beds need rotation, like Mechanical Crafters; 32 RPM is normal speed.")
                .defineInRange("stressImpact", 3.0, 0.0, 64.0);
        TRIAL_INTERVAL_MINUTES = b.comment("Minutes between a trial plot's discovery rolls.")
                .defineInRange("intervalMinutes", 5, 1, 1440);
        TRIAL_CHANCE = b.comment("Bonus: chance per roll to discover an untested mutation outright, for a full-size plot.",
                        "Trial plots also discover mutations for real by growing and crossing their seeds.")
                .defineInRange("chance", 0.05, 0.0, 1.0);
        TRIAL_FULL_SIZE = b.comment("Beds for the full chance (81 = a 9x9). Smaller plots scale the chance down.")
                .defineInRange("fullSize", 81, 1, 128);
        TRIAL_CONSUMES_SEEDS = b.comment("Whether a bonus-roll discovery spends one seed of each parent.")
                .define("consumesSeeds", true);
        TRIAL_HOLD_CYCLES = b.comment("Half-second cycles a mature producer stays (to parent crosses) before rotating to another species.")
                .defineInRange("holdCycles", 120, 1, 100_000);
        b.pop();

        b.push("soil");
        CONDITIONER_MAX_STEPS = b.comment("How far soil conditioners (lime, sulfur dust, compost...) can shift each soil property, in steps either way.",
                        "0 disables conditioners.")
                .defineInRange("conditionerMaxSteps", 2, 0, 6);
        b.pop();

        b.push("cloche");
        SHADE_MAX_LIGHT = b.comment("A crop under a Shade Cloche reads light no brighter than this.")
                .defineInRange("shadeMaxLight", 3, 0, 15);
        LAMP_MIN_LIGHT = b.comment("A crop under a Lamp Cloche reads light at least this bright. Neither cloche changes real light.")
                .defineInRange("lampMinLight", 14, 0, 15);
        b.pop();

        b.push("olla");
        OLLA_RADIUS = b.comment("How far (horizontally) a filled Olla keeps farmland moist. Vanilla water reaches 4.")
                .defineInRange("radius", 4, 1, 8);
        b.pop();

        b.push("sprinkler");
        SPRINKLER_RADIUS = b.comment("Horizontal reach (blocks each way). 4 = a 9x9 area.")
                .defineInRange("radius", 4, 1, 8);
        SPRINKLER_INTERVAL = b.comment("Ticks between sprays.")
                .defineInRange("interval", 5, 1, 200);
        SPRINKLER_ATTEMPTS = b.comment("Random positions watered per spray. Defaults give crops in range roughly 4-5x vanilla growth.")
                .defineInRange("attempts", 4, 1, 64);
        SPRINKLER_TICKS_PER_MB = b.comment("Ticks a sprinkler runs per mB of water. It holds 1000 mB and only runs while it has water.",
                        "Default 4: a full tank lasts 4000 ticks (200 s); a slow Create pump keeps it topped up.")
                .defineInRange("ticksPerMillibucket", 4, 1, 1200);
        SPRINKLER_BONEMEAL_CHANCE = b.comment("Chance per watered position to also apply a bone-meal effect (moss spreads, crops jump).")
                .defineInRange("bonemealChance", 0.05, 0.0, 1.0);
        b.pop();

        b.push("planter_bed");
        BED_STRESS = b.comment("Stress impact of each Planter Bed, per RPM. Beds need rotation, like Mechanical Crafters.")
                .defineInRange("stressImpact", 3.0, 0.0, 64.0);
        BED_CYCLE = b.comment("Ticks between a Planter Bed's work cycles (plant, harvest, water, fertilize) at 32 RPM; faster spins work faster.")
                .defineInRange("cycle", 40, 1, 200);
        BED_TICKS_PER_MB = b.comment("Ticks a bed runs per mB of water (4000 mB tank). Water gives extra growth and +1 humidity.")
                .defineInRange("ticksPerMillibucket", 8, 1, 1200);
        BED_GROWTH_ODDS = b.comment("With water, each cycle gives the crop an extra growth tick with chance 1 in N (default ~4x vanilla).")
                .defineInRange("growthOdds", 20, 1, 10_000);
        BED_FERTILIZER_ODDS = b.comment("Each cycle, chance 1 in N to spend one fertilizer on a growing crop.")
                .defineInRange("fertilizerOdds", 6, 1, 10_000);
        BED_BREED_HOLD = b.comment("Cycles a mature crop next to a breeding bed waits before harvest, so the grafting frame can use it as a parent.")
                .defineInRange("breedHoldCycles", 10, 0, 10_000);
        BED_WEED_MULTIPLIER = b.comment("Weeds on crops in a Planter Bed, relative to plain soil: 0 = none (a tended bed; any blight",
                        "already there is cleared), 1 = as on soil. Trial Beds never grow weeds.")
                .defineInRange("weedMultiplier", 0.0, 0.0, 1.0);
        BED_QUALITY_GAIN = b.comment("Seed ranking weights: beds plant, keep and upgrade to the highest weighted stat total.",
                        "Defaults favor output: Gain 3, Growth 2, Strength 1, Vigor 1.")
                .defineInRange("qualityGain", 3.0, 0.0, 100.0);
        BED_QUALITY_GROWTH = b.defineInRange("qualityGrowth", 2.0, 0.0, 100.0);
        BED_QUALITY_STRENGTH = b.defineInRange("qualityStrength", 1.0, 0.0, 100.0);
        BED_QUALITY_VIGOUR = b.defineInRange("qualityVigour", 1.0, 0.0, 100.0);
        b.pop();

        b.push("performance");
        WORK_BUDGET_PER_TICK = b.comment(
                        "Max units of deferred work (structure scans, network rebuilds) per dimension per tick.",
                        "Lower it if the mod shows up in tick profiling; large builds just take longer to update.")
                .defineInRange("workBudgetPerTick", 256, 1, 100_000);
        b.pop();

        SPEC = b.build();
    }

    private CultivarServerConfig() {}
}
