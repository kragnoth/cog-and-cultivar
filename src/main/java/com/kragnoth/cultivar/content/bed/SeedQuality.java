package com.kragnoth.cultivar.content.bed;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;

/**
 * How the Planter Bed ranks seeds: a weighted sum of expressed stats, tuned for output by default
 * (Gain 3, Growth 2, Strength 1, Vigour 1; server config planter_bed.quality*). Any other registered stat counts 1.
 */
public final class SeedQuality {
    private SeedQuality() {}

    public static double score(Genome genome) {
        var expr = Genetics.current();
        double total = 0;
        for (var stat : CoreModule.STATS.getEntries()) {
            int value = Genetics.expressed(genome, stat.getKey(), expr);
            total += weight(stat.getId().getPath()) * value;
        }
        return total;
    }

    private static double weight(String stat) {
        boolean loaded = CultivarServerConfig.SPEC.isLoaded();
        return switch (stat) {
            case "gain" -> loaded ? CultivarServerConfig.BED_QUALITY_GAIN.get() : 3;
            case "growth" -> loaded ? CultivarServerConfig.BED_QUALITY_GROWTH.get() : 2;
            case "strength" -> loaded ? CultivarServerConfig.BED_QUALITY_STRENGTH.get() : 1;
            case "vigour" -> loaded ? CultivarServerConfig.BED_QUALITY_VIGOUR.get() : 1;
            default -> 1;
        };
    }
}
