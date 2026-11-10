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
    public static final ModConfigSpec.IntValue WORK_BUDGET_PER_TICK;
    public static final ModConfigSpec.IntValue SPRINKLER_RADIUS;
    public static final ModConfigSpec.IntValue SPRINKLER_INTERVAL;
    public static final ModConfigSpec.IntValue SPRINKLER_ATTEMPTS;
    public static final ModConfigSpec.IntValue SPRINKLER_TICKS_PER_MB;
    public static final ModConfigSpec.DoubleValue SPRINKLER_BONEMEAL_CHANCE;
    public static final ModConfigSpec.EnumValue<Genetics.Expression> STAT_EXPRESSION;
    public static final ModConfigSpec.BooleanValue CLONE_MUTATIONS;
    public static final ModConfigSpec.BooleanValue REQUIRE_ASSAY;
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
        WATER_SHIFTS_HUMIDITY = b.comment("Water beside a soil block makes it one humidity step wetter; dry farmland reads one step drier.")
                .define("waterShiftsHumidity", true);
        b.pop();

        b.push("genetics");
        STAT_EXPRESSION = b.comment("How an allele pair is expressed as one stat value: MAX (dominant), MIN, or MEAN.")
                .defineEnum("statExpression", Genetics.Expression.MAX);
        CLONE_MUTATIONS = b.comment("Whether a single-parent clone can still drift stats.")
                .define("cloneMutations", false);
        REQUIRE_ASSAY = b.comment("Seed item tooltips hide stats until the seed is assayed (Assaying Engine).",
                        "The Loupe and goggles always read crops in the field.")
                .define("requireAssay", true);
        b.pop();

        b.push("influence");
        INFLUENCE_ENABLED = b.comment("Whether nearby sources (beehives, sprinklers, heaters...) modify crops.")
                .define("influenceEnabled", true);
        MAX_MUTATION_MULTIPLIER = b.comment("Cap on the combined mutation multiplier from all sources affecting one crop.")
                .defineInRange("maxMutationMultiplier", 1.5, 1.0, 4.0);
        MIN_WEED_MULTIPLIER = b.comment("Floor on the combined weed multiplier (lower = stronger suppression).")
                .defineInRange("minWeedMultiplier", 0.5, 0.0, 1.0);
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
