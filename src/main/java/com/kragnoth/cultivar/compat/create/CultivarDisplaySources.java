package com.kragnoth.cultivar.compat.create;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Display Link sources (spec §3.13): a trellis reports its crop ("Carrot 75%" / "Carrot: ready"), and the
 * Assaying Engine reports ledger progress ("Ledger 12/33 species") or, without a ledger, what it's assaying.
 * Lang: {@code cultivar.display_source.<id>}.
 */
public final class CultivarDisplaySources {
    public static final DeferredRegister<DisplaySource> SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, Cultivar.MOD_ID);

    public static final DeferredHolder<DisplaySource, DisplaySource> CROP = SOURCES.register("crop_status", () -> new SingleLineDisplaySource() {
        @Override
        protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
            return CropBlockEntity.resolve(context.level(), context.getSourcePos())
                    .map(CultivarDisplaySources::cropLine).orElse(EMPTY_LINE);
        }

        @Override
        protected boolean allowsLabeling(DisplayLinkContext context) {
            return true;
        }
    });

    public static final DeferredHolder<DisplaySource, DisplaySource> LEDGER = SOURCES.register("ledger_progress", () -> new SingleLineDisplaySource() {
        @Override
        protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
            if (!(context.getSourceBlockEntity() instanceof AssayingEngineBlockEntity engine)) {
                return EMPTY_LINE;
            }
            ItemStack ledger = engine.inventory.getStackInSlot(AssayingEngineBlockEntity.LEDGER);
            if (ledger.isEmpty()) {
                ItemStack in = engine.inventory.getStackInSlot(AssayingEngineBlockEntity.SEED_IN);
                return in.isEmpty() ? Component.translatable("cultivar.assay.idle")
                        : Component.translatable("cultivar.assay.progress",
                        CultivarSeedItem.plantName(in.get(com.kragnoth.cultivar.content.core.CoreModule.GENOME.get())),
                        (int) (engine.progress() * 100 / AssayingEngineBlockEntity.WORK));
            }
            int known = LedgerItem.data(ledger).researched().size();
            int total = context.level().registryAccess().registryOrThrow(CultivarRegistries.PLANT).size();
            return Component.translatable("cultivar.display.ledger", known, total);
        }

        @Override
        protected boolean allowsLabeling(DisplayLinkContext context) {
            return true;
        }
    });

    private CultivarDisplaySources() {}

    static MutableComponent cropLine(CropBlockEntity crop) {
        if (crop.genome() == null) {
            return Component.translatable("cultivar.display.crop.empty");
        }
        Plant plant = crop.genome().species().value();
        Component name = CultivarSeedItem.plantName(crop.genome());
        if (crop.isMature()) {
            return Component.translatable("cultivar.display.crop.ready", name);
        }
        int pct = plant.matureStage() <= 0 ? 100 : crop.stage() * 100 / plant.matureStage();
        return Component.translatable("cultivar.display.crop.growing", name, pct);
    }

    /** Binds the sources to blocks; call during common setup (enqueueWork). */
    static void bind() {
        DisplaySource.BY_BLOCK.add(CropModule.TIMBER_TRELLIS.get(), CROP.get());
        DisplaySource.BY_BLOCK.add(CropModule.IRON_TRELLIS.get(), CROP.get());
        DisplaySource.BY_BLOCK.add(AssayModule.ENGINE.get(), LEDGER.get());
    }
}
