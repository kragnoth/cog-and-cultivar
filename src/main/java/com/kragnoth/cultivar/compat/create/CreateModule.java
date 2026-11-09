package com.kragnoth.cultivar.compat.create;

import com.kragnoth.cultivar.core.module.CultivarModule;
import net.neoforged.fml.ModList;

/**
 * Create integration. Create is a hard dependency today, but this module follows the optional-compat
 * pattern so the same shape works for JEI, Jade, Serene Seasons and the rest:
 * no Create imports in this class; Create-touching code lives in sibling classes.
 * The harvester hook is a mixin ({@code mixin.HarvesterMovementBehaviourMixin}) that calls {@link CultivarHarvesting}.
 */
public final class CreateModule implements CultivarModule {
    @Override
    public String id() {
        return "create";
    }

    @Override
    public boolean shouldLoad() {
        return ModList.get().isLoaded("create");
    }
}
