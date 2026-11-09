package com.kragnoth.cultivar.core.datagen;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.data.DataProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Set;

/**
 * Datagen entry ({@code ./gradlew runData}). All JSON under {@code src/generated} comes from here;
 * never hand-edit generated files. CI fails a PR if generated output is stale.
 */
public final class CultivarDataGen {
    /**
     * Namespaces whose datapack entries are written. Add a compat namespace here when a module
     * generates entries for another mod's plants (e.g. "farmersdelight").
     */
    private static final Set<String> NAMESPACES = Set.of(Cultivar.MOD_ID, "minecraft");

    private CultivarDataGen() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CultivarDataGen::onGatherData);
    }

    private static void onGatherData(GatherDataEvent event) {
        DataBootstraps data = new DataBootstraps();
        for (CultivarModule module : Cultivar.modules()) {
            module.bootstrapData(data);
        }
        if (!data.isEmpty()) {
            event.getGenerator().addProvider(event.includeServer(),
                    (DataProvider.Factory<DatapackBuiltinEntriesProvider>) output -> new DatapackBuiltinEntriesProvider(
                            output, event.getLookupProvider(), data.toBuilder(), NAMESPACES));
        }
    }
}
