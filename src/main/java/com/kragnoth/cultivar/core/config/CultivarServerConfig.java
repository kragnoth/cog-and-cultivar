package com.kragnoth.cultivar.core.config;

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
    public static final ModConfigSpec.IntValue WORK_BUDGET_PER_TICK;
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
        b.pop();

        b.push("influence");
        INFLUENCE_ENABLED = b.comment("Whether nearby sources (beehives, sprinklers, heaters...) modify crops.")
                .define("influenceEnabled", true);
        MAX_MUTATION_MULTIPLIER = b.comment("Cap on the combined mutation multiplier from all sources affecting one crop.")
                .defineInRange("maxMutationMultiplier", 1.5, 1.0, 4.0);
        MIN_WEED_MULTIPLIER = b.comment("Floor on the combined weed multiplier (lower = stronger suppression).")
                .defineInRange("minWeedMultiplier", 0.5, 0.0, 1.0);
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
