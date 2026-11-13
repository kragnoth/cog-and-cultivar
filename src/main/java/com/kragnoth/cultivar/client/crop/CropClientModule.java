package com.kragnoth.cultivar.client.crop;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.client.ClientModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GrassColor;

/** Wraps every trellis and field-crop blockstate model with {@link CropBakedModel}. */
public final class CropClientModule implements ClientModule {
    @Override
    public String id() {
        return "crop";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(CropClientModule::wrapModels);
        modBus.addListener(CropClientModule::registerCropModels);
        modBus.addListener(CropClientModule::registerColors);
        modBus.addListener((RegisterColorHandlersEvent.Item event) ->
                event.register((stack, tint) -> tint == 0 ? SeedColors.of(stack) : -1, CropModule.SEED.get()));
        modBus.addListener((net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent event) -> SeedColors.clear());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) -> SeedColors.clear());
    }

    /** Every model under {@code models/crop/} in any namespace, so datapack plants can name them. */
    private static void registerCropModels(ModelEvent.RegisterAdditional event) {
        Minecraft.getInstance().getResourceManager().listResources("models/crop", rl -> rl.getPath().endsWith(".json"))
                .keySet().forEach(file -> {
                    String path = file.getPath();
                    ResourceLocation id = file.withPath(path.substring("models/".length(), path.length() - ".json".length()));
                    event.register(CropBakedModel.modelLocation(id));
                });
    }

    /** Tint 0: vanilla stem colour by growth; tint 1: biome grass colour (sugar cane). */
    private static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (tint == 1) {
                return level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor();
            }
            if (tint != 0) {
                return -1;
            }
            int age = 7;
            com.kragnoth.cultivar.api.crop.CropView crop = level == null || pos == null ? null
                    : state.getBlock() instanceof com.kragnoth.cultivar.content.field.FieldCropBlock
                            ? com.kragnoth.cultivar.content.field.FieldCropBlock.baseEntity(level, pos).orElse(null)
                    : CropBlockEntity.resolve(level, pos).orElse(null);
            if (crop != null && crop.genome() != null) {
                int stages = crop.genome().species().value().stages();
                age = stages <= 1 ? 7 : crop.stage() * 7 / (stages - 1);
            }
            return (age * 32) << 16 | (255 - age * 8) << 8 | age * 4;
        }, CropModule.TIMBER_TRELLIS.get(), CropModule.IRON_TRELLIS.get(), com.kragnoth.cultivar.content.field.FieldCropModule.FIELD_CROP.get());
    }

    private static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            var id = location.id();
            boolean trellisBlockState = id.getNamespace().equals(Cultivar.MOD_ID)
                    && (id.getPath().endsWith("_trellis") || id.getPath().equals("field_crop"))
                    && !"inventory".equals(location.variant());
            return trellisBlockState ? new CropBakedModel(model) : model;
        });
    }
}
