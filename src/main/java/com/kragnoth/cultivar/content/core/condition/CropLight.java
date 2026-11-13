package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.content.cloche.ClocheType;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The light a crop reads: the world's raw brightness at the trellis, overridden by a fitted Shade Cloche (capped at
 * {@code cloche.shadeMaxLight}) or Lamp Cloche (raised to {@code cloche.lampMinLight}). The override is per trellis
 * and changes no world light, so neighbours are unaffected: that is what lets a dark parent stand beside a bright one.
 */
public final class CropLight {
    private CropLight() {}

    public static int at(Level level, BlockPos cropPos) {
        return adjust(level.getRawBrightness(cropPos, 0), ClocheType.fitted(level.getBlockState(cropPos)));
    }

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
