package com.kragnoth.cultivar.compat.jade;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropInfo;
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
            if (!(CropBlockEntity.resolve(accessor.getLevel(), accessor.getPosition()).orElse(null) instanceof CropBlockEntity crop)) {
                return;
            }
            // Compact on purpose: the full readout lives in the Botanist's Loupe and Create goggles.
            List<Component> lines = CropInfo.lines(crop);
            tooltip.add(lines.getFirst());
            if (crop.weed() != null) {
                tooltip.add(lines.getLast());
            }
        }
    }
}
