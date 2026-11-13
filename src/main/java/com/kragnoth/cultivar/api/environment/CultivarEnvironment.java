package com.kragnoth.cultivar.api.environment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of environment hooks. Modules (and addons) register during mod construction; the core growth rules read
 * them, so a new humidity, light or moisture source never needs an edit to the core.
 */
public final class CultivarEnvironment {
    private static final List<HumidityModifier> HUMIDITY = new CopyOnWriteArrayList<>();
    private static final List<LightModifier> LIGHT = new CopyOnWriteArrayList<>();
    private static final List<MoistureSource> MOISTURE = new CopyOnWriteArrayList<>();

    private CultivarEnvironment() {}

    public static void addHumidityModifier(HumidityModifier modifier) {
        HUMIDITY.add(modifier);
    }

    public static void addLightModifier(LightModifier modifier) {
        LIGHT.add(modifier);
    }

    public static void addMoistureSource(MoistureSource source) {
        MOISTURE.add(source);
    }

    public static List<HumidityModifier> humidityModifiers() {
        return HUMIDITY;
    }

    public static List<LightModifier> lightModifiers() {
        return LIGHT;
    }

    public static List<MoistureSource> moistureSources() {
        return MOISTURE;
    }
}
