package com.kragnoth.cultivar.content.crop.plant;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.api.plant.Fertilizer;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Product;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.condition.Hydration;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.core.growth.GrowthMath;
import com.kragnoth.cultivar.content.core.lookup.SoilLookup;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.soil.SoilAmendments;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One planted Cultivar plant: genome, growth stage and assay flag, plus everything a plant does on its own (grow on
 * random ticks, read its soil and environment, harvest by Gain, take cuttings, fertilize, save/load).
 *
 * <p>Holder-agnostic: the trellis ({@code CropBlockEntity}) adds weeds, breeding and stacked segments on top; a field
 * crop (planned: decorative planting straight on soil, no mutation or spread) needs only this plus a {@link Host}.
 * Anything both should share goes here, not in a holder.
 */
public final class PlantCore {
    /** What a holder supplies to its plant. */
    public interface Host {
        @Nullable
        Level level();

        /** Where the plant is rooted. */
        BlockPos rootPos();

        /** The soil block feeding it. */
        default BlockPos soilPos() {
            return rootPos().below();
        }

        /** Blocks of room the plant has to grow into (trellis stacks; 1 for single-block holders). */
        default int blocksAvailable() {
            return 1;
        }

        /** Extra Strength the holder grants (trellis tier). */
        default int strengthBonus() {
            return 0;
        }

        /** Combined crop influence here (server only; NONE elsewhere). */
        default InfluenceEffects influence() {
            return InfluenceEffects.NONE;
        }

        /** Persist and sync after any change. */
        void changed();
    }

    private final Host host;
    @Nullable
    private Genome genome;
    private int stage;
    private boolean assayed;

    public PlantCore(Host host) {
        this.host = host;
    }

    // ---------------------------------------------------------------- state

    @Nullable
    public Genome genome() {
        return genome;
    }

    public boolean hasPlant() {
        return genome != null;
    }

    public int stage() {
        return stage;
    }

    @Nullable
    public Plant species() {
        return genome == null ? null : genome.species().value();
    }

    public boolean isMature() {
        return genome != null && stage >= species().matureStage();
    }

    public boolean isAssayed() {
        return assayed;
    }

    public void setAssayed(boolean known) {
        if (assayed != known) {
            assayed = known;
            host.changed();
        }
    }

    /** Stats may be shown: the genome is known, or the server doesn't require assaying. */
    public boolean statsKnown() {
        return assayed || CultivarServerConfig.SPEC.isLoaded() && !CultivarServerConfig.REQUIRE_ASSAY.get();
    }

    public int stat(ResourceKey<Stat> stat) {
        return genome == null ? Genetics.min(stat) : Genetics.expressed(genome, stat);
    }

    /** Strength as growth checks use it: the genome's plus the holder's bonus. */
    public int strength() {
        return stat(CoreModule.STRENGTH.getKey()) + host.strengthBonus();
    }

    /** Sets genome, stage and assay flag in one go (planting, breeding, display). No planting rules. */
    public void set(@Nullable Genome newGenome, int atStage, boolean known) {
        genome = newGenome;
        assayed = newGenome != null && known;
        stage = newGenome == null ? 0 : Math.max(0, Math.min(atStage, newGenome.species().value().matureStage()));
        host.changed();
    }

    public void setStage(int newStage) {
        if (genome != null && newStage != stage) {
            stage = Math.max(0, Math.min(newStage, species().matureStage()));
            host.changed();
        }
    }

    /** Lifts the living plant out (genome, stage, assay flag), leaving the holder empty. */
    public Optional<Uprooted> uproot() {
        if (genome == null) {
            return Optional.empty();
        }
        Uprooted out = new Uprooted(genome, stage, assayed);
        set(null, 0, false);
        return Optional.of(out);
    }

    public record Uprooted(Genome genome, int stage, boolean assayed) {}

    /** A seed of this plant: assayed when the plant's genome is known (it's the same genome). */
    public ItemStack seedStack() {
        return CultivarSeedItem.of(genome, assayed);
    }

    // ---------------------------------------------------------------- environment

    public Optional<Soil> soil() {
        Level level = host.level();
        return level == null ? Optional.empty() : SoilLookup.at(level, host.soilPos());
    }

    /** Humidity after water, heat and every registered humidity modifier (see {@link Hydration}). */
    public Optional<SoilLevels.Humidity> humidity() {
        return soil().map(s -> Hydration.effective(host.level(), host.soilPos(), s.humidity()));
    }

    public Optional<SoilLevels.Acidity> acidity() {
        return soil().map(s -> SoilAmendments.acidity(host.level(), host.soilPos(), s.acidity()));
    }

    public Optional<SoilLevels.Nutrients> nutrients() {
        return soil().map(s -> SoilAmendments.nutrients(host.level(), host.soilPos(), s.nutrients()));
    }

    /** The context growth conditions, blight habitats and mutation habitats are tested against. */
    public GrowthContext context(Optional<Soil> soil) {
        Level level = host.level();
        BlockPos soilPos = host.soilPos();
        return new GrowthContext(level, host.rootPos(), soil,
                soil.map(s -> Hydration.effective(level, soilPos, s.humidity())),
                soil.map(s -> SoilAmendments.acidity(level, soilPos, s.acidity())),
                soil.map(s -> SoilAmendments.nutrients(level, soilPos, s.nutrients())));
    }

    /** Maximum Strength makes a plant Hardy: it grows on any Cultivar soil regardless of the soil's properties. */
    public boolean isHardy() {
        return genome != null && stat(CoreModule.STRENGTH.getKey()) >= Genetics.max(CoreModule.STRENGTH.getKey());
    }

    /** The plant's growth requirements as they apply here (soil properties dropped when Hardy). */
    public List<GrowthCondition> requirements() {
        if (genome == null) {
            return List.of();
        }
        List<GrowthCondition> all = species().requirements();
        return isHardy() ? all.stream().filter(c -> !c.isSoilProperty()).toList() : all;
    }

    /** How the plant responds to where it stands right now. {@code ideal} skips requirements (Trial Bed). */
    public GrowthResponse response(boolean ideal) {
        Optional<Soil> soil = soil();
        if (soil.isEmpty()) {
            return GrowthResponse.INFERTILE;
        }
        return ideal ? GrowthResponse.FERTILE : GrowthCondition.testAll(requirements(), context(soil), strength());
    }

    // ---------------------------------------------------------------- growth

    /** One random tick of growth: kill, wither a stage, grow a stage by chance, or nothing. */
    public void tickGrowth(RandomSource random, boolean ideal) {
        if (genome == null) {
            return;
        }
        Plant plant = species();
        switch (response(ideal)) {
            case KILL -> set(null, 0, false);
            case LETHAL -> setStage(stage - 1);
            case FERTILE -> {
                if (stage < plant.matureStage() && plant.blocksAt(stage + 1) <= host.blocksAvailable()) {
                    double chance = GrowthMath.growthChance(plant, stat(CoreModule.GROWTH.getKey()),
                            soil().map(Soil::growthModifier).orElse(1f), CultivarServerConfig.GROWTH_MULTIPLIER.get(), host.influence());
                    if (random.nextDouble() < chance) {
                        setStage(stage + 1);
                    }
                }
            }
            case INFERTILE -> { }
        }
    }

    /**
     * Advances a growing plant {@code fertilizer.potency()} stages, only where it could grow and only as far as the
     * holder has room. Returns whether anything happened (so the item is only spent when it does).
     */
    public boolean fertilize(Fertilizer fertilizer, boolean ideal) {
        if (genome == null || isMature() || response(ideal) != GrowthResponse.FERTILE) {
            return false;
        }
        Plant plant = species();
        int target = Math.min(plant.matureStage(), stage + fertilizer.potency());
        while (target > stage && plant.blocksAt(target) > host.blocksAvailable()) {
            target--;
        }
        if (target == stage) {
            return false;
        }
        setStage(target);
        return true;
    }

    /** Shrinks the plant to fit when its holder loses room (a trellis segment removed). */
    public void clampToSpace() {
        if (genome == null) {
            return;
        }
        int s = stage;
        while (s > 0 && species().blocksAt(s) > host.blocksAvailable()) {
            s--;
        }
        setStage(s);
    }

    // ---------------------------------------------------------------- harvest & drops

    /** Harvests a mature plant: product rolls by Gain, then back to the after-harvest stage. */
    public boolean harvest(Consumer<ItemStack> drops) {
        Level level = host.level();
        if (!isMature() || level == null) {
            return false;
        }
        Plant plant = species();
        int after = Math.min(plant.afterHarvestStage(), plant.matureStage());
        // Tall plants yield per segment cut (3-tall cane back to 1 = 2 harvests), like cutting vanilla cane.
        int segmentsCut = Math.max(1, plant.blocksAt(stage) - plant.blocksAt(after));
        for (int i = 0; i < segmentsCut; i++) {
            rollProducts(drops, level.random);
        }
        setStage(after);
        return true;
    }

    /** One harvest's products: one roll of the product list per Gain-derived roll. */
    public void rollProducts(Consumer<ItemStack> drops, RandomSource random) {
        if (genome == null) {
            return;
        }
        int rolls = GrowthMath.harvestRolls(stat(CoreModule.GAIN.getKey()));
        for (int i = 0; i < rolls; i++) {
            roll(species().products(), drops, random);
        }
    }

    /** Rolls a product list once (clip and rake products use this too). */
    public static void roll(List<Product> products, Consumer<ItemStack> drops, RandomSource random) {
        for (Product product : products) {
            if (random.nextFloat() < product.chance()) {
                int count = product.min() + random.nextInt(Math.max(1, product.max() - product.min() + 1));
                if (count > 0) {
                    drops.accept(new ItemStack(product.item(), count));
                }
            }
        }
    }

    /** What breaking the holder gives back: the seed, plus a harvest if mature (like breaking ripe wheat). */
    public void collectBreakDrops(Consumer<ItemStack> drops) {
        Level level = host.level();
        if (genome != null && level != null) {
            drops.accept(seedStack());
            if (isMature()) {
                rollProducts(drops, level.random);
            }
        }
    }

    /** Snipping needs a plant past its second stage. */
    public boolean canTakeCutting() {
        return genome != null && stage >= 2;
    }

    /**
     * Snips the plant: it drops back one stage and gives a Cutting with its exact genome. A mature plant also yields
     * its clip products. Returns false if the plant is too young.
     */
    public boolean clip(Consumer<ItemStack> drops) {
        Level level = host.level();
        if (!canTakeCutting() || level == null) {
            return false;
        }
        if (isMature()) {
            roll(species().clipProducts(), drops, level.random);
        }
        ItemStack cutting = com.kragnoth.cultivar.content.tools.CuttingItem.of(genome);
        if (assayed) {
            cutting.set(CoreModule.ASSAYED.get(), Unit.INSTANCE);
        }
        drops.accept(cutting);
        setStage(stage - 1);
        return true;
    }

    /** For comparators: 0 empty, 1–14 growth progress, 15 mature. */
    public int comparatorSignal() {
        if (genome == null) {
            return 0;
        }
        if (isMature()) {
            return 15;
        }
        return 1 + (13 * stage) / Math.max(1, species().matureStage());
    }

    // ---------------------------------------------------------------- persistence

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        // Always write stage: an empty tag is skipped by the client, which would leave a ghost plant after uprooting.
        tag.putInt("stage", stage);
        if (assayed) {
            tag.putBoolean("assayed", true);
        }
        if (genome != null) {
            Genome.CODEC.encodeStart(ops, genome).resultOrPartial(Cultivar.LOGGER::error).ifPresent(t -> tag.put("genome", t));
        }
    }

    /** Entries removed from datapacks fail to decode: the plant is dropped instead of crashing. */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        genome = tag.contains("genome", Tag.TAG_COMPOUND)
                ? Genome.CODEC.parse(ops, tag.get("genome")).resultOrPartial(Cultivar.LOGGER::warn).orElse(null)
                : null;
        stage = genome == null ? 0 : Math.min(tag.getInt("stage"), genome.species().value().matureStage());
        assayed = genome != null && tag.getBoolean("assayed");
    }
}
