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
        h.forComponents(AssayModule.ENGINE_ITEM)
                .addStoryBoard("assaying", CultivarScenes::assaying);
    }
}
