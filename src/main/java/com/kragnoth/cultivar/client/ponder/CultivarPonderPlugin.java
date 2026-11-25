package com.kragnoth.cultivar.client.ponder;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registers the Ponder scenes (press W on an item's tooltip). Structures: assets/cultivar/ponder/*.nbt. */
public final class CultivarPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return Cultivar.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<DeferredHolder<?, ?>> h = helper.withKeyFunction(DeferredHolder::getId);
        h.forComponents(CropModule.TIMBER_TRELLIS, CropModule.IRON_TRELLIS, ToolsModule.PRUNING_SHEARS, ToolsModule.CUTTING)
                .addStoryBoard("spreading", CultivarScenes::spreading);
        h.forComponents(CropModule.SEED, CropModule.TIMBER_TRELLIS, CropModule.IRON_TRELLIS, ToolsModule.SEED_SATCHEL)
                .addStoryBoard("crop_stats", CultivarScenes::stats);
        h.forComponents(CropModule.TIMBER_TRELLIS, CropModule.IRON_TRELLIS, LedgerModule.LEDGER, CropModule.SEED)
                .addStoryBoard("crossbreeding", CultivarScenes::crossbreeding);
        h.forComponents(AssayModule.ENGINE_ITEM, AssayModule.BRASS_ENGINE_ITEM, AssayModule.CHROMATIC_ENGINE_ITEM)
                .addStoryBoard("assaying", CultivarScenes::assaying);
        h.forComponents(CropModule.SEED)
                .addStoryBoard("field_crops", CultivarScenes::fieldCrops);
        h.forComponents(com.kragnoth.cultivar.content.cloche.ClocheModule.BELL_CLOCHE, com.kragnoth.cultivar.content.cloche.ClocheModule.SHADE_CLOCHE,
                        com.kragnoth.cultivar.content.cloche.ClocheModule.LAMP_CLOCHE)
                .addStoryBoard("cloches", CultivarScenes::cloches);
        h.forComponents(com.kragnoth.cultivar.content.irrigation.IrrigationModule.OLLA_ITEM)
                .addStoryBoard("olla", CultivarScenes::olla);
        h.forComponents(com.kragnoth.cultivar.content.irrigation.IrrigationModule.SPRINKLER_ITEM)
                .addStoryBoard("sprinkler", CultivarScenes::sprinkler);
        h.forComponents(com.kragnoth.cultivar.content.bed.PlanterBedModule.BED_ITEM)
                .addStoryBoard("planter_beds", CultivarScenes::planterBeds);
        h.forComponents(com.kragnoth.cultivar.content.trial.TrialModule.TRIAL_BED_ITEM)
                .addStoryBoard("trial_beds", CultivarScenes::trialBeds);
    }
}
