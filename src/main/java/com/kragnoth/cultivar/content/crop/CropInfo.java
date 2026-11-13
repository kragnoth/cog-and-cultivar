package com.kragnoth.cultivar.content.crop;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.core.influence.InfluenceLookup;
import com.kragnoth.cultivar.content.soil.Amendment;
import com.kragnoth.cultivar.content.cloche.ClocheType;
import com.kragnoth.cultivar.content.core.condition.CropLight;
import com.kragnoth.cultivar.content.soil.SoilAmendments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Human-readable crop readout shared by Create goggles and Jade, so they always
 * agree. Works on either side; reads only state that is synced to clients.
 */
public final class CropInfo {
    private CropInfo() {}

    public static List<Component> lines(CropBlockEntity crop) {
        List<Component> out = new ArrayList<>();
        Genome genome = crop.genome();
        if (genome != null) {
            out.add(CultivarSeedItem.plantName(genome).copy().append(" " + growthPercent(crop) + "%")
                    .withStyle(crop.isMature() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            if (crop.isHardy()) {
                out.add(Component.translatableWithFallback("cultivar.info.hardy", "Hardy: grows on any soil").withStyle(ChatFormatting.GOLD));
            }
            int missing = crop.segmentsMissing();
            if (missing > 0) {
                out.add(Component.translatableWithFallback("cultivar.info.needs_segments",
                        "Needs %s more trellis stacked above", missing).withStyle(ChatFormatting.YELLOW));
            }
            if (crop.statsKnown()) {
                CoreModule.STATS.getEntries().forEach(stat -> out.add(Component.literal("  ")
                        .append(Component.translatable("stat.cultivar." + stat.getId().getPath()))
                        .append(": " + Genetics.expressed(genome, stat.getKey()))
                        .withStyle(ChatFormatting.GRAY)));
            } else {
                out.add(Component.translatable("cultivar.info.unassayed").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } else {
            out.add(Component.translatable("cultivar.info.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        crop.soil().ifPresentOrElse(soil -> {
                    out.add(soilLine(crop.humidity().orElse(soil.humidity()), crop.acidity().orElse(soil.acidity()),
                            crop.nutrients().orElse(soil.nutrients())));
                    Amendment amendment = SoilAmendments.get(crop.getLevel(), crop.getBlockPos().below());
                    if (!amendment.isEmpty()) {
                        out.add(SoilAmendments.describe(amendment));
                    }
                },
                () -> out.add(Component.translatable("cultivar.info.no_soil").withStyle(ChatFormatting.RED)));
        if (crop.getLevel() != null) {
            out.add(lightLine(crop));
        }
        for (InfluenceLookup.Active active : crop.activeInfluences()) {
            ResourceLocation id = active.id().location();
            out.add(Component.translatableWithFallback("influence." + id.getNamespace() + "." + id.getPath(), id.getPath())
                    .append(" " + Math.round(active.strength() * 100) + "%").withStyle(ChatFormatting.GOLD));
        }
        if (crop.weed() != null) {
            ResourceLocation id = crop.weed().unwrapKey().map(k -> k.location()).orElse(ResourceLocation.withDefaultNamespace("weed"));
            out.add(Component.translatableWithFallback("weed." + id.getNamespace() + "." + id.getPath(), id.getPath())
                    .append(" " + (crop.weedStage() + 1) + "/" + crop.weed().value().stages())
                    .withStyle(ChatFormatting.RED));
        }
        return out;
    }

    /** "Light: 3 (Shade Cloche; 15 outside)": the light growth checks use. */
    private static Component lightLine(CropBlockEntity crop) {
        var level = crop.getLevel();
        int raw = level.getRawBrightness(crop.getBlockPos(), 0);
        var cloche = ClocheType.fitted(crop.getBlockState());
        int seen = CropLight.adjust(level, crop.getBlockPos(), raw);
        if (seen == raw) {
            return Component.translatable("cultivar.info.light", seen).withStyle(ChatFormatting.DARK_AQUA);
        }
        if (cloche == null) { // shifted by an addon's LightModifier
            return Component.translatable("cultivar.info.light.shifted", seen, raw).withStyle(ChatFormatting.DARK_AQUA);
        }
        return Component.translatable("cultivar.info.light." + cloche.getSerializedName(), seen, raw).withStyle(ChatFormatting.DARK_AQUA);
    }

    public static int growthPercent(CropBlockEntity crop) {
        Genome genome = crop.genome();
        if (genome == null) {
            return 0;
        }
        int mature = genome.species().value().matureStage();
        return mature == 0 ? 100 : 100 * crop.stage() / mature;
    }

    public static MutableComponent soilLine(SoilLevels.Humidity humidity, SoilLevels.Acidity acidity, SoilLevels.Nutrients nutrients) {
        return Component.translatable("cultivar.info.soil",
                level("humidity", humidity.getSerializedName()),
                level("acidity", acidity.getSerializedName()),
                level("nutrients", nutrients.getSerializedName())).withStyle(ChatFormatting.DARK_AQUA);
    }

    private static Component level(String kind, String value) {
        return Component.translatableWithFallback("cultivar.soil." + kind + "." + value,
                value.replace('_', ' ').toLowerCase(Locale.ROOT));
    }
}
