package com.kragnoth.cultivar.api.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * Adds humidity steps to a soil block (positive is wetter). Registered with {@link CultivarEnvironment}; summed with
 * the core rules (water beside, farmland moisture, heat) and clamped by {@code Hydration}.
 *
 * <p>Must be a cheap, pure read of world state: it runs on the server for growth and on the client for readouts.
 */
@FunctionalInterface
public interface HumidityModifier {
    int steps(BlockGetter level, BlockPos soilPos);

    /**
     * Environmental modifiers (cloches, sprinklers, channels) switch off with the server config
     * {@code soil.waterShiftsHumidity}, like water does; deliberate soil amendments return false and always apply.
     */
    default boolean environmental() {
        return true;
    }

    static HumidityModifier amendment(HumidityModifier steps) {
        return new HumidityModifier() {
            @Override
            public int steps(BlockGetter level, BlockPos soilPos) {
                return steps.steps(level, soilPos);
            }

            @Override
            public boolean environmental() {
                return false;
            }
        };
    }
}
