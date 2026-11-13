package com.kragnoth.cultivar.content.crop;

import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import com.kragnoth.cultivar.api.plant.Weed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
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

    /**
     * What a readout needs about one planted spot. The trellis fills everything; a holder without weeds, stacking or
     * influences (a field crop) leaves those empty.
     */
    public record Subject(PlantCore plant, Level level, BlockPos pos, BlockState state, int segmentsMissing,
                          List<InfluenceLookup.Active> influences, @Nullable Holder<Weed> weed, int weedStage) {
        public static Subject simple(PlantCore plant, Level level, BlockPos pos, BlockState state) {
            return new Subject(plant, level, pos, state, 0, List.of(), null, 0);
        }
    }

    public static List<Component> lines(CropBlockEntity crop) {
        return lines(new Subject(crop.plant(), crop.getLevel(), crop.getBlockPos(), crop.getBlockState(), crop.segmentsMissing(),
                crop.activeInfluences(), crop.weed(), crop.weedStage()));
    }

    public static List<Component> lines(Subject s) {
        PlantCore plant = s.plant();
        List<Component> out = new ArrayList<>();
        Genome genome = plant.genome();
        if (genome != null) {
            out.add(CultivarSeedItem.plantName(genome).copy().append(" " + growthPercent(plant) + "%")
                    .withStyle(plant.isMature() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            if (plant.isHardy()) {
                out.add(Component.translatableWithFallback("cultivar.info.hardy", "Hardy: grows on any soil").withStyle(ChatFormatting.GOLD));
            }
            if (s.segmentsMissing() > 0) {
                out.add(Component.translatableWithFallback("cultivar.info.needs_segments",
                        "Needs %s more trellis stacked above", s.segmentsMissing()).withStyle(ChatFormatting.YELLOW));
            }
            if (plant.statsKnown()) {
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
        plant.soil().ifPresentOrElse(soil -> {
                    out.add(soilLine(plant.humidity().orElse(soil.humidity()), plant.acidity().orElse(soil.acidity()),
                            plant.nutrients().orElse(soil.nutrients())));
                    Amendment amendment = SoilAmendments.get(s.level(), s.pos().below());
                    if (!amendment.isEmpty()) {
                        out.add(SoilAmendments.describe(amendment));
                    }
                },
                () -> out.add(Component.translatable("cultivar.info.no_soil").withStyle(ChatFormatting.RED)));
        if (s.level() != null) {
            out.add(lightLine(s.level(), s.pos(), s.state()));
        }
        for (InfluenceLookup.Active active : s.influences()) {
            ResourceLocation id = active.id().location();
            out.add(Component.translatableWithFallback("influence." + id.getNamespace() + "." + id.getPath(), id.getPath())
                    .append(" " + Math.round(active.strength() * 100) + "%").withStyle(ChatFormatting.GOLD));
        }
        if (s.weed() != null) {
            ResourceLocation id = s.weed().unwrapKey().map(k -> k.location()).orElse(ResourceLocation.withDefaultNamespace("weed"));
            out.add(Component.translatableWithFallback("weed." + id.getNamespace() + "." + id.getPath(), id.getPath())
                    .append(" " + (s.weedStage() + 1) + "/" + s.weed().value().stages())
                    .withStyle(ChatFormatting.RED));
        }
        return out;
    }

    /** "Light: 3 (Shade Cloche; 15 outside)": the light growth checks use. */
    private static Component lightLine(Level level, BlockPos pos, BlockState state) {
        int raw = level.getRawBrightness(pos, 0);
        var cloche = ClocheType.fitted(state);
        int seen = CropLight.adjust(level, pos, raw);
        if (seen == raw) {
            return Component.translatable("cultivar.info.light", seen).withStyle(ChatFormatting.DARK_AQUA);
        }
        if (cloche == null) { // shifted by an addon's LightModifier
            return Component.translatable("cultivar.info.light.shifted", seen, raw).withStyle(ChatFormatting.DARK_AQUA);
        }
        return Component.translatable("cultivar.info.light." + cloche.getSerializedName(), seen, raw).withStyle(ChatFormatting.DARK_AQUA);
    }

    public static int growthPercent(CropBlockEntity crop) {
        return growthPercent(crop.plant());
    }

    public static int growthPercent(PlantCore plant) {
        Genome genome = plant.genome();
        if (genome == null) {
            return 0;
        }
        int mature = genome.species().value().matureStage();
        return mature == 0 ? 100 : 100 * plant.stage() / mature;
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
