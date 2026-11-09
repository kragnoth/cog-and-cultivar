package com.kragnoth.cultivar.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player visual preferences ({@code config/cultivar-client.toml}). Nothing here affects gameplay. */
public final class CultivarClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue GENOME_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue PARTICLES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        GENOME_TOOLTIPS = b.comment("Show stat values on seed tooltips (hold Shift for the full genome).")
                .define("genomeTooltips", true);
        PARTICLES = b.comment("Show Cultivar particles (sprinkler spray, steam mist, pollen).")
                .define("particles", true);
        SPEC = b.build();
    }

    private CultivarClientConfig() {}
}
