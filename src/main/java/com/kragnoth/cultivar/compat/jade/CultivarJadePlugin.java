package com.kragnoth.cultivar.compat.jade;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
            if (!(accessor.getBlockEntity() instanceof CropBlockEntity crop)) {
                return;
            }
            Genome genome = crop.genome();
            if (genome != null) {
                int mature = genome.species().value().matureStage();
                int pct = mature == 0 ? 100 : 100 * crop.stage() / mature;
                tooltip.add(CultivarSeedItem.plantName(genome).copy().append(" " + pct + "%")
                        .withStyle(crop.isMature() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
                StringBuilder stats = new StringBuilder();
                CoreModule.STATS.getEntries().forEach(stat -> stats.append(stats.isEmpty() ? "" : "  ")
                        .append(stat.getId().getPath().substring(0, 3).toUpperCase())
                        .append(' ').append(Genetics.expressed(genome, stat.getKey(), Genetics.Expression.MAX)));
                tooltip.add(Component.literal(stats.toString()).withStyle(ChatFormatting.GRAY));
            }
            if (crop.weed() != null) {
                var id = crop.weed().unwrapKey().map(k -> k.location()).orElse(UID);
                tooltip.add(Component.translatableWithFallback("weed." + id.getNamespace() + "." + id.getPath(), id.getPath())
                        .append(" " + (crop.weedStage() + 1) + "/" + crop.weed().value().stages())
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
