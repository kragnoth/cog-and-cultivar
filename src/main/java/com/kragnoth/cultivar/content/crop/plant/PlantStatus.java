package com.kragnoth.cultivar.content.crop.plant;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Product;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.growth.GrowthMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Why a plant is (or isn't) growing, worked out from the same checks growth uses, so a readout can say exactly what is
 * wrong. Pure and read-only: built on demand on either side (goggles and Jade on the client, planting hints on the
 * server), never stored.
 *
 * @param kind         the headline
 * @param requirements every requirement that applies here, each with how this spot answers it
 */
public record PlantStatus(Kind kind, List<Check> requirements) {
    /** Random ticks a block gets per in-game day at the default {@code randomTickSpeed} of 3 (24000 × 3 / 4096). */
    public static final double RANDOM_TICKS_PER_DAY = 24000 * 3 / 4096.0;

    public enum Kind {
        /** Nothing planted. */
        EMPTY,
        /** No Cultivar soil under it: nothing grows. */
        NO_SOIL,
        /** Something here destroys it outright. */
        DYING,
        /** Something here costs it a stage now and then. */
        WITHERING,
        /** A requirement isn't met: it waits, unharmed. */
        STALLED,
        /** Conditions are fine but it needs more trellis (or air) above to grow taller. */
        NO_ROOM,
        GROWING,
        RIPE;

        public boolean isProblem() {
            return this == NO_SOIL || this == DYING || this == WITHERING || this == STALLED || this == NO_ROOM;
        }
    }

    /** One requirement and how this spot answers it. {@code current} is what the spot has, when the condition says. */
    public record Check(GrowthCondition condition, GrowthResponse response, Optional<net.minecraft.network.chat.Component> current) {
        public boolean met() {
            return response == GrowthResponse.FERTILE;
        }
    }

    public static PlantStatus of(PlantCore plant) {
        if (!plant.hasPlant()) {
            return new PlantStatus(Kind.EMPTY, List.of());
        }
        Optional<Soil> soil = plant.soil();
        if (soil.isEmpty()) {
            return new PlantStatus(Kind.NO_SOIL, List.of());
        }
        GrowthContext ctx = plant.context(soil);
        int strength = plant.strength();
        List<Check> checks = new ArrayList<>();
        GrowthResponse worst = GrowthResponse.FERTILE;
        for (GrowthCondition c : plant.requirements()) {
            GrowthResponse r;
            try {
                r = c.test(ctx, strength);
            } catch (RuntimeException e) { // an addon condition that can't run here (client side): don't break the readout
                continue;
            }
            worst = GrowthResponse.worst(worst, r);
            checks.add(new Check(c, r, r == GrowthResponse.FERTILE ? Optional.empty() : c.current(ctx)));
        }
        Kind kind = switch (worst) {
            case KILL -> Kind.DYING;
            case LETHAL -> Kind.WITHERING;
            case INFERTILE -> plant.isMature() ? Kind.RIPE : Kind.STALLED;
            case FERTILE -> plant.isMature() ? Kind.RIPE : plant.hasRoomToGrow() ? Kind.GROWING : Kind.NO_ROOM;
        };
        return new PlantStatus(kind, List.copyOf(checks));
    }

    /** Requirements this spot doesn't meet, worst first. */
    public List<Check> failing() {
        return requirements.stream().filter(c -> !c.met())
                .sorted((a, b) -> b.response().compareTo(a.response())).toList();
    }

    /** Expected stages per in-game day while growing (default random tick speed); 0 when not growing. */
    public static double stagesPerDay(PlantCore plant, PlantStatus status) {
        return status.kind() == Kind.GROWING ? plant.growthChance() * RANDOM_TICKS_PER_DAY : 0;
    }

    /**
     * Odds for one product on one harvest: the chance at least one drops, and the average count. Uses the plant's Gain
     * rolls (or one roll when {@code knownGain} is false) and tall plants' per-segment rolls.
     */
    public record Yield(Product product, double chance, double average) {}

    public static List<Yield> yields(PlantCore plant, boolean knownGain) {
        if (!plant.hasPlant()) {
            return List.of();
        }
        Plant p = plant.species();
        int after = Math.min(p.afterHarvestStage(), p.matureStage());
        int segments = Math.max(1, p.blocksAt(p.matureStage()) - p.blocksAt(after));
        int rolls = (knownGain ? GrowthMath.harvestRolls(plant.stat(CoreModule.GAIN.getKey())) : 1) * segments;
        List<Yield> out = new ArrayList<>();
        for (Product product : p.products()) {
            double c = Math.min(1, product.chance());
            double chance = 1 - Math.pow(1 - c, rolls);
            double average = rolls * c * (product.min() + product.max()) / 2.0;
            out.add(new Yield(product, chance, average));
        }
        return out;
    }
}
