package com.kragnoth.cultivar.content.crop;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import com.kragnoth.cultivar.content.crop.plant.PlantStatus;
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
import java.util.Optional;

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

    /**
     * How much to show. {@link #BRIEF} (Jade, no goggles): name, what's wrong and why. {@link #FULL} (Create goggles):
     * also every requirement, the harvest outlook, growth speed, stats, soil and light.
     */
    public enum Detail { BRIEF, FULL }

    public static List<Component> lines(CropBlockEntity crop) {
        return lines(crop, Detail.FULL);
    }

    public static List<Component> lines(CropBlockEntity crop, Detail detail) {
        if (!crop.hasPlant() && crop.isCross()) {
            List<Component> out = crossLines(crop);
            if (crop.weed() != null) {
                out.add(weedLine(crop.weed(), crop.weedStage()));
            }
            return out;
        }
        return lines(new Subject(crop.plant(), crop.getLevel(), crop.getBlockPos(), crop.getBlockState(), crop.segmentsMissing(),
                crop.activeInfluences(), crop.weed(), crop.weedStage()), detail);
    }

    public static List<Component> lines(Subject s) {
        return lines(s, Detail.FULL);
    }

    public static List<Component> lines(Subject s, Detail detail) {
        PlantCore plant = s.plant();
        boolean full = detail == Detail.FULL;
        List<Component> out = new ArrayList<>();
        Genome genome = plant.genome();
        if (genome != null) {
            out.add(CultivarSeedItem.plantName(genome).copy().append(" " + growthPercent(plant) + "%")
                    .withStyle(plant.isMature() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            PlantStatus status = PlantStatus.of(plant);
            out.add(statusLine(plant, status, s.segmentsMissing(), full));
            for (PlantStatus.Check check : full ? status.requirements() : status.failing()) {
                requirementLine(check).ifPresent(out::add);
            }
            if (plant.isHardy()) {
                out.add(Component.translatableWithFallback("cultivar.info.hardy", "Hardy: grows on any soil").withStyle(ChatFormatting.GOLD));
            }
            if (!full) {
                Amendment amendment = SoilAmendments.get(s.level(), s.pos().below());
                if (!amendment.isEmpty()) {
                    out.add(SoilAmendments.describe(amendment));
                }
                if (s.weed() != null) {
                    out.add(weedLine(s.weed(), s.weedStage()));
                }
                return out;
            }
            yieldLines(plant, out);
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
                () -> {
                    if (genome == null) { // with a plant, the status line already says so
                        out.add(Component.translatable("cultivar.info.no_soil").withStyle(ChatFormatting.RED));
                    }
                });
        if (s.level() != null) {
            out.add(lightLine(s.level(), s.pos(), s.state()));
        }
        for (InfluenceLookup.Active active : s.influences()) {
            ResourceLocation id = active.id().location();
            out.add(Component.translatableWithFallback("influence." + id.getNamespace() + "." + id.getPath(), id.getPath())
                    .append(" " + Math.round(active.strength() * 100) + "%").withStyle(ChatFormatting.GOLD));
        }
        if (s.weed() != null) {
            out.add(weedLine(s.weed(), s.weedStage()));
        }
        return out;
    }

    private static Component weedLine(Holder<Weed> weed, int stage) {
        ResourceLocation id = weed.unwrapKey().map(k -> k.location()).orElse(ResourceLocation.withDefaultNamespace("weed"));
        return Component.translatableWithFallback("weed." + id.getNamespace() + "." + id.getPath(), id.getPath())
                .append(" " + (stage + 1) + "/" + weed.value().stages()).withStyle(ChatFormatting.RED);
    }

    // ---------------------------------------------------------------- status

    /** "Growing (~1.4 stages a day)", "Stalled: a need isn't met", "Withering: losing growth"... */
    private static Component statusLine(PlantCore plant, PlantStatus status, int segmentsMissing, boolean full) {
        PlantStatus.Kind kind = status.kind();
        if (kind == PlantStatus.Kind.NO_ROOM && segmentsMissing > 0) {
            return Component.translatable("cultivar.info.needs_segments", segmentsMissing).withStyle(ChatFormatting.YELLOW);
        }
        String key = "cultivar.status." + kind.name().toLowerCase(Locale.ROOT);
        ChatFormatting colour = switch (kind) {
            case RIPE, GROWING -> ChatFormatting.GREEN;
            case STALLED, NO_ROOM -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
        MutableComponent line = Component.translatable(key).withStyle(colour);
        if (full && kind == PlantStatus.Kind.GROWING) {
            double perDay = PlantStatus.stagesPerDay(plant, status);
            line.append(Component.translatable("cultivar.status.rate", String.format(Locale.ROOT, "%.1f", perDay))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        return line;
    }

    /** "✔ Light 9+" or "✘ Light 9+ (here: 4)". */
    public static Optional<Component> requirementLine(PlantStatus.Check check) {
        return check.condition().describe().map(what -> {
            if (check.met()) {
                return Component.literal("  \u2714 ").append(what).withStyle(ChatFormatting.DARK_GRAY);
            }
            ChatFormatting colour = check.response() == GrowthResponse.INFERTILE ? ChatFormatting.GOLD : ChatFormatting.RED;
            MutableComponent line = Component.literal("  \u2718 ").append(what);
            check.current().ifPresent(now -> line.append(Component.translatable("cultivar.status.here", now)));
            return line.withStyle(colour);
        });
    }

    /**
     * After planting: if it won't grow where it stands, an action-bar hint naming the first problem ("Planted, but it
     * won't grow here yet: ✘ Light 9+ (here: 4)"). Server side.
     */
    public static void warnAfterPlanting(PlantCore plant, @Nullable net.minecraft.world.entity.player.Player player) {
        if (player == null || plant.genome() == null) {
            return;
        }
        PlantStatus status = PlantStatus.of(plant);
        Component why = switch (status.kind()) {
            case NO_SOIL -> Component.translatable("cultivar.info.no_soil");
            case STALLED, WITHERING, DYING -> status.failing().stream().findFirst()
                    .flatMap(CropInfo::requirementLine).map(c -> (Component) c).orElse(null);
            default -> null;
        };
        if (why != null) {
            player.displayClientMessage(Component.translatable("cultivar.planting.wont_grow", why.copy().getString().strip())
                    .withStyle(status.kind() == PlantStatus.Kind.STALLED ? ChatFormatting.YELLOW : ChatFormatting.RED), true);
        }
    }

    /** One line per product: a vague band until the seed is assayed, then the real odds per harvest. */
    private static void yieldLines(PlantCore plant, List<Component> out) {
        boolean known = plant.statsKnown();
        List<PlantStatus.Yield> yields = PlantStatus.yields(plant, known);
        if (yields.isEmpty()) {
            return;
        }
        out.add(Component.translatable("cultivar.yield.header").withStyle(ChatFormatting.GRAY));
        for (PlantStatus.Yield y : yields) {
            MutableComponent line = Component.literal("  ").append(y.product().item().value().getDescription()).append(": ");
            if (known) {
                line.append(Component.translatable("cultivar.yield.exact", Math.round(y.chance() * 100),
                        String.format(Locale.ROOT, "%.1f", y.average())));
            } else {
                line.append(Component.translatable("cultivar.yield.band." + band(y.chance())));
            }
            out.add(line.withStyle(ChatFormatting.GRAY));
        }
    }

    /** Vague odds for an unassayed plant: reliable, usually, sometimes, rarely, very_rarely. */
    public static String band(double chance) {
        return chance >= 0.95 ? "reliable" : chance >= 0.6 ? "usually" : chance >= 0.25 ? "sometimes" : chance >= 0.08 ? "rarely" : "very_rarely";
    }

    // ---------------------------------------------------------------- grafting frame

    /**
     * An empty grafting frame: what it's waiting on. Ripe, unblighted neighbours are parents; for each pair that has a
     * mutation, whether its habitat is met here, and if not, which requirement blocks it. The child isn't named, so the
     * readout never spoils a discovery.
     */
    static List<Component> crossLines(CropBlockEntity frame) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("cultivar.cross.header").withStyle(ChatFormatting.WHITE));
        Level level = frame.getLevel();
        if (level == null) {
            return out;
        }
        List<Holder<com.kragnoth.cultivar.api.plant.Plant>> ripe = new ArrayList<>();
        int growing = 0, blighted = 0;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(frame.getBlockPos().relative(d)) instanceof CropBlockEntity n && n.hasPlant()) {
                if (n.weed() != null) {
                    blighted++;
                } else if (n.isMature()) {
                    ripe.add(n.genome().species());
                } else {
                    growing++;
                }
            }
        }
        if (blighted > 0) {
            out.add(Component.translatable("cultivar.cross.blighted", blighted).withStyle(ChatFormatting.RED));
        }
        if (ripe.isEmpty()) {
            out.add(Component.translatable(growing > 0 ? "cultivar.cross.waiting_growing" : "cultivar.cross.no_parents", growing)
                    .withStyle(ChatFormatting.YELLOW));
            return out;
        }
        if (ripe.size() == 1) {
            out.add(Component.translatable(ripe.getFirst().value().cloneable() ? "cultivar.cross.one_parent" : "cultivar.cross.one_parent_no_clone")
                    .withStyle(ChatFormatting.YELLOW));
            return out;
        }
        if (frame.onIdealHost()) {
            out.add(Component.translatable("cultivar.cross.ideal").withStyle(ChatFormatting.GREEN));
            return out;
        }
        var ctx = frame.growthContext(frame.soil());
        boolean possible = false;
        java.util.LinkedHashMap<String, Component> blockers = new java.util.LinkedHashMap<>();
        for (Mutation m : level.registryAccess().registryOrThrow(com.kragnoth.cultivar.api.CultivarRegistries.MUTATION)) {
            if (!anyPair(ripe, m)) {
                continue;
            }
            boolean met = true;
            for (GrowthCondition c : m.conditions()) {
                GrowthResponse r;
                try {
                    r = c.test(ctx, 1);
                } catch (RuntimeException e) {
                    continue;
                }
                if (r != GrowthResponse.FERTILE) {
                    met = false;
                    requirementLine(new PlantStatus.Check(c, r, c.current(ctx)))
                            .ifPresent(line -> blockers.putIfAbsent(line.getString(), line));
                }
            }
            possible |= met;
        }
        if (possible) {
            out.add(Component.translatable("cultivar.cross.possible").withStyle(ChatFormatting.GREEN));
        } else if (!blockers.isEmpty()) {
            out.add(Component.translatable("cultivar.cross.blocked").withStyle(ChatFormatting.GOLD));
            out.addAll(blockers.values());
        } else {
            out.add(Component.translatable("cultivar.cross.same").withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static boolean anyPair(List<Holder<com.kragnoth.cultivar.api.plant.Plant>> ripe, Mutation m) {
        for (int i = 0; i < ripe.size(); i++) {
            for (int j = i + 1; j < ripe.size(); j++) {
                if (m.matches(ripe.get(i), ripe.get(j))) {
                    return true;
                }
            }
        }
        return false;
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
