package com.kragnoth.cultivar.content.assay;

import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Assayer tiers. Each is the same machine in a better casing: the top shows bare farmland (andesite), a trellis
 * (brass) or a cloched trellis (chromatic compound), and assays faster. Speed = RPM × the tier's multiplier (config).
 */
public enum AssayTier implements StringRepresentable {
    ANDESITE(1.0, () -> CultivarServerConfig.ASSAY_SPEED_ANDESITE),
    BRASS(2.0, () -> CultivarServerConfig.ASSAY_SPEED_BRASS),
    CHROMATIC(4.0, () -> CultivarServerConfig.ASSAY_SPEED_CHROMATIC);

    private final double fallback;
    private final Supplier<ModConfigSpec.DoubleValue> speed;

    AssayTier(double fallback, Supplier<ModConfigSpec.DoubleValue> speed) {
        this.fallback = fallback;
        this.speed = speed;
    }

    /** The configured speed multiplier (the default before server config loads, e.g. in client tooltips). */
    public double speed() {
        return CultivarServerConfig.SPEC.isLoaded() ? speed.get().get() : fallback;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
