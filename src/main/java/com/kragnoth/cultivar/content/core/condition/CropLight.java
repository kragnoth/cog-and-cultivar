package com.kragnoth.cultivar.content.core.condition;

import com.kragnoth.cultivar.api.environment.CultivarEnvironment;
import com.kragnoth.cultivar.api.environment.LightModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The light a crop reads: the world's raw brightness at the crop, passed through every registered
 * {@link LightModifier} (the cloche module registers Shade and Lamp Cloches). World light is never changed, so
 * neighbours are unaffected: that is what lets a dark parent stand beside a bright one.
 */
public final class CropLight {
    private CropLight() {}

    public static int at(Level level, BlockPos cropPos) {
        return adjust(level, cropPos, level.getRawBrightness(cropPos, 0));
    }

    public static int adjust(Level level, BlockPos cropPos, int light) {
        for (LightModifier m : CultivarEnvironment.lightModifiers()) {
            light = m.adjust(level, cropPos, light);
        }
        return Math.clamp(light, 0, 15);
    }
}
