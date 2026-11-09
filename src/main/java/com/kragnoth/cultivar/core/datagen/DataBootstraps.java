package com.kragnoth.cultivar.core.datagen;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects datapack-registry entries from every module so each registry is bootstrapped exactly once
 * (RegistrySetBuilder rejects duplicate registry keys). Modules call {@link #add} from
 * {@code CultivarModule#bootstrapData}.
 */
public final class DataBootstraps {
    private final Map<ResourceKey<? extends Registry<?>>, List<RegistrySetBuilder.RegistryBootstrap<?>>> entries =
            new LinkedHashMap<>();

    public <T> void add(ResourceKey<? extends Registry<T>> registry, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        entries.computeIfAbsent(registry, k -> new ArrayList<>()).add(bootstrap);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public RegistrySetBuilder toBuilder() {
        RegistrySetBuilder builder = new RegistrySetBuilder();
        entries.forEach((key, list) -> addAll(builder, key, list));
        return builder;
    }

    @SuppressWarnings("unchecked")
    private static <T> void addAll(RegistrySetBuilder builder,
                                   ResourceKey<? extends Registry<?>> key,
                                   List<RegistrySetBuilder.RegistryBootstrap<?>> list) {
        builder.add((ResourceKey<? extends Registry<T>>) key, context -> {
            for (RegistrySetBuilder.RegistryBootstrap<?> bootstrap : list) {
                ((RegistrySetBuilder.RegistryBootstrap<T>) bootstrap).run(context);
            }
        });
    }
}
