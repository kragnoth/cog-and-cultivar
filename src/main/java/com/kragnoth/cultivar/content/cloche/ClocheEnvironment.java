package com.kragnoth.cultivar.content.cloche;

import com.kragnoth.cultivar.api.environment.CultivarEnvironment;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * What a fitted cloche does to its crop's environment: every cloche is one humidity step wetter for the soil beneath;
 * Shade and Lamp Cloches also cap or raise the light the crop reads ({@code cloche.shadeMaxLight} /
 * {@code cloche.lampMinLight}). Registered once by {@link ClocheModule}.
 */
public final class ClocheEnvironment {
    private ClocheEnvironment() {}

    static void register() {
        CultivarEnvironment.addHumidityModifier((level, soilPos) -> ClocheType.fitted(level.getBlockState(soilPos.above())) != null ? 1 : 0);
        CultivarEnvironment.addLightModifier(ClocheEnvironment::light);
    }

    static int light(BlockGetter level, BlockPos cropPos, int light) {
        return adjust(light, ClocheType.fitted(level.getBlockState(cropPos)));
    }

    /** The light a crop under {@code cloche} reads when the world gives it {@code light}. */
    public static int adjust(int light, ClocheType cloche) {
        if (cloche == ClocheType.SHADE) {
            return Math.min(light, shadeMax());
        }
        if (cloche == ClocheType.LAMP) {
            return Math.max(light, lampMin());
        }
        return light;
    }

    public static int shadeMax() {
        return CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.SHADE_MAX_LIGHT.get() : 3;
    }

    public static int lampMin() {
        return CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.LAMP_MIN_LIGHT.get() : 14;
    }
}
