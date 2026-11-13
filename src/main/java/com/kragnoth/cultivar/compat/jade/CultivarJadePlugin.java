package com.kragnoth.cultivar.compat.jade;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropInfo;
import com.kragnoth.cultivar.content.soil.Amendment;
import com.kragnoth.cultivar.content.soil.SoilAmendments;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import java.util.List;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade integration. Jade discovers this class by annotation and only loads it when Jade is installed,
 * so no module entry is needed. Crop data is already synced to clients, so this is client-only.
 */
@WailaPlugin(Cultivar.MOD_ID)
public final class CultivarJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CropProvider.INSTANCE, TrellisBlock.class);
        registration.registerBlockComponent(CropProvider.INSTANCE, com.kragnoth.cultivar.content.field.FieldCropBlock.class);
        registration.registerBlockComponent(SoilProvider.INSTANCE, Block.class);
    }

    /** Any soil block (or Planter Bed) a conditioner has been used on. */
    enum SoilProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = Cultivar.id("soil_amendment");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlock() instanceof TrellisBlock || accessor.getBlock() instanceof com.kragnoth.cultivar.content.field.FieldCropBlock) {
                return;
            }
            Amendment amendment = SoilAmendments.get(accessor.getLevel(), accessor.getPosition());
            if (!amendment.isEmpty()) {
                tooltip.add(SoilAmendments.describe(amendment));
            }
        }
    }

    enum CropProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = Cultivar.id("crop");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof com.kragnoth.cultivar.content.field.FieldCropBlockEntity field && field.plant().hasPlant()) {
                tooltip.add(CropInfo.lines(CropInfo.Subject.simple(field.plant(), accessor.getLevel(), accessor.getPosition(),
                        accessor.getBlockState())).getFirst());
                Amendment amendment = SoilAmendments.get(accessor.getLevel(), accessor.getPosition().below());
                if (!amendment.isEmpty()) {
                    tooltip.add(SoilAmendments.describe(amendment));
                }
                return;
            }
            if (!(CropBlockEntity.resolve(accessor.getLevel(), accessor.getPosition()).orElse(null) instanceof CropBlockEntity crop)) {
                return;
            }
            // Compact on purpose: the full readout lives in Create goggles.
            List<Component> lines = CropInfo.lines(crop);
            tooltip.add(lines.getFirst());
            Amendment amendment = SoilAmendments.get(accessor.getLevel(), crop.getBlockPos().below());
            if (!amendment.isEmpty()) {
                tooltip.add(SoilAmendments.describe(amendment));
            }
            if (crop.weed() != null) {
                tooltip.add(lines.getLast());
            }
        }
    }
}
