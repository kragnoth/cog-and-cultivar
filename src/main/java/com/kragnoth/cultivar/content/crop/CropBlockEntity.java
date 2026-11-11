package com.kragnoth.cultivar.content.crop;

import net.minecraft.world.level.BlockGetter;
import com.kragnoth.cultivar.content.core.condition.Hydration;
import com.kragnoth.cultivar.api.soil.SoilLevels;

import com.kragnoth.cultivar.Cultivar;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.network.chat.Component;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthContext;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Product;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.BreedingEngine;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.core.growth.GrowthMath;
import com.kragnoth.cultivar.content.core.influence.InfluenceLookup;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * State and rules for one trellis: the planted genome, its growth stage and any weed.
 * Logic runs only from {@link #randomTick} and player interaction; there is no ticker.
 */
public class CropBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    @Nullable
    private Genome genome;
    private int stage;
    /** Whether this plant's genome is known (planted from an assayed seed/cutting, or a clone of a known parent). */
    private boolean assayed;
    @Nullable
    private Holder<Weed> weed;
    private int weedStage;

    public CropBlockEntity(BlockPos pos, BlockState state) {
        super(CropModule.CROP_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- queries

    @Nullable
    public Genome genome() {
        return genome;
    }

    public boolean hasPlant() {
        return genome != null;
    }

    public boolean hasWeed() {
        return weed != null;
    }

    public int stage() {
        return stage;
    }

    public int weedStage() {
        return weedStage;
    }

    @Nullable
    public Holder<Weed> weed() {
        return weed;
    }

    public boolean isMature() {
        return genome != null && stage >= genome.species().value().matureStage();
    }

    public boolean isCross() {
        return getBlockState().getValue(TrellisBlock.CROSS);
    }

    private int stat(ResourceKey<Stat> stat) {
        return genome == null ? Genetics.min(stat)
                : Genetics.expressed(genome, stat);
    }

    @Nullable
    private InfluenceEffects cachedInfluence;
    private long influenceExpiry;
    /** Which influences are active here; synced so goggles and Jade can show them. */
    private List<InfluenceLookup.Active> activeInfluences = List.of();

    /**
     * Combined crop influence here (beehives, cloches...). Recomputed at most about once a minute per crop,
     * with jitter so neighbouring crops don't refresh on the same tick. Server-side only.
     */
    public InfluenceEffects influence() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InfluenceEffects.NONE;
        }
        long now = serverLevel.getGameTime();
        if (cachedInfluence == null || now >= influenceExpiry) {
            InfluenceLookup.Result result = InfluenceLookup.computeDetailed(serverLevel, worldPosition);
            cachedInfluence = result.effects();
            influenceExpiry = now + 1200 + serverLevel.random.nextInt(200);
            if (!result.active().equals(activeInfluences)) {
                activeInfluences = result.active();
                changed();
            }
        }
        return cachedInfluence;
    }

    /** Recomputes influence now (e.g. a cloche was fitted or removed) so displays update immediately. */
    public void invalidateInfluence() {
        cachedInfluence = null;
        influence();
    }

    public List<InfluenceLookup.Active> activeInfluences() {
        return activeInfluences;
    }

    /** Soil values as conditions see them: humidity shifted by nearby water (see {@link Hydration}). */
    public Optional<SoilLevels.Humidity> humidity() {
        return soil().map(s -> Hydration.effective(level, worldPosition.below(), s.humidity()));
    }

    private GrowthContext growthContext(Optional<Soil> soil) {
        return new GrowthContext(level, worldPosition, soil,
                soil.map(s -> Hydration.effective(level, worldPosition.below(), s.humidity())),
                soil.map(Soil::acidity), soil.map(Soil::nutrients));
    }

    // ---------------------------------------------------------------- hardiness

    /** Maximum Strength makes a plant Hardy: it grows on any Cultivar soil regardless of the soil's properties. */
    public boolean isHardy() {
        return genome != null && stat(CoreModule.STRENGTH.getKey()) >= Genetics.max(CoreModule.STRENGTH.getKey());
    }

    /** The plant's growth requirements as they apply to this crop (soil properties dropped when Hardy). */
    public List<GrowthCondition> requirements() {
        if (genome == null) {
            return List.of();
        }
        List<GrowthCondition> all = genome.species().value().requirements();
        return isHardy() ? all.stream().filter(c -> !c.isSoilProperty()).toList() : all;
    }

    // ---------------------------------------------------------------- cuttings

    /** Snipping needs a plant past its second stage. */
    public boolean canTakeCutting() {
        return genome != null && stage >= 2;
    }

    /**
     * Snips the plant: it drops back one stage and gives a Cutting with its exact genome. A mature plant also
     * yields its clip products. Returns false if the plant is too young.
     */
    public boolean clip(Consumer<ItemStack> drops) {
        if (!canTakeCutting() || level == null) {
            return false;
        }
        Plant plant = genome.species().value();
        if (isMature()) {
            rollList(plant.clipProducts(), drops, level.random);
        }
        ItemStack cutting = com.kragnoth.cultivar.content.tools.CuttingItem.of(genome);
        if (assayed) {
            cutting.set(CoreModule.ASSAYED.get(), net.minecraft.util.Unit.INSTANCE);
        }
        drops.accept(cutting);
        stage--;
        changed();
        return true;
    }

    // ---------------------------------------------------------------- tall crops (stacked trellis)

    /** Tallest stack: a base plus three segments. */
    public static final int MAX_STACK = 4;

    /** An upper segment: a trellis standing on another trellis. It holds no plant of its own. */
    public boolean isSegment() {
        return level != null && level.getBlockState(worldPosition.below()).getBlock() instanceof TrellisBlock;
    }

    /** The crop rooted at the bottom of the stack {@code pos} belongs to (itself for a single trellis). */
    public static Optional<CropBlockEntity> resolve(BlockGetter level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 1; i < MAX_STACK && level.getBlockState(p.below()).getBlock() instanceof TrellisBlock; i++) {
            p = p.below();
        }
        return level.getBlockEntity(p) instanceof CropBlockEntity crop ? Optional.of(crop) : Optional.empty();
    }

    /** This trellis plus the plain trellis segments stacked on it, up to {@link #MAX_STACK}. */
    public int segmentsAvailable() {
        if (level == null) {
            return 1;
        }
        int n = 1;
        BlockPos p = worldPosition.above();
        while (n < MAX_STACK && level.getBlockState(p).getBlock() instanceof TrellisBlock) {
            n++;
            p = p.above();
        }
        return n;
    }

    /** Segments the plant still needs to reach maturity, or 0 if it has room. */
    public int segmentsMissing() {
        if (genome == null) {
            return 0;
        }
        Plant plant = genome.species().value();
        return Math.max(0, plant.blocksAt(plant.matureStage()) - segmentsAvailable());
    }

    /** Shrinks the plant when a segment above it is removed. Server-side. */
    public void clampToSpace() {
        if (genome == null) {
            return;
        }
        Plant plant = genome.species().value();
        int s = stage;
        int room = segmentsAvailable();
        while (s > 0 && plant.blocksAt(s) > room) {
            s--;
        }
        if (s != stage) {
            stage = s;
            changed();
        }
    }

    public Optional<Soil> soil() {
        if (level == null) {
            return Optional.empty();
        }
        BlockState under = level.getBlockState(worldPosition.below());
        BlockState below = under.getBlock() instanceof com.kragnoth.cultivar.api.soil.SoilHost host
                ? host.hostedSoil(level, worldPosition.below()) : under;
        if (below == null) {
            return Optional.empty();
        }
        return level.registryAccess().registryOrThrow(CultivarRegistries.SOIL).stream()
                .filter(soil -> soil.blocks().contains(below.getBlockHolder()))
                .findFirst();
    }

    // ---------------------------------------------------------------- fertilizer

    public static Optional<com.kragnoth.cultivar.api.plant.Fertilizer> fertilizerFor(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return level.registryAccess().registryOrThrow(CultivarRegistries.FERTILIZER).stream()
                .filter(f -> f.items().contains(stack.getItemHolder())).findFirst();
    }

    /**
     * Applies a fertilizer: a growing plant advances {@code potency} stages (only where it could grow, and only as far
     * as the trellis stack allows); a grafting frame gets an immediate breeding attempt if the fertilizer
     * {@code triggers_mutation}. Returns whether anything happened (so the item is only spent when it does).
     */
    public boolean fertilize(ServerLevel level, RandomSource random, com.kragnoth.cultivar.api.plant.Fertilizer fertilizer) {
        if (isSegment()) {
            return false;
        }
        if (isCross()) {
            if (!fertilizer.triggersMutation() || genome != null) {
                return false;
            }
            tickBreeding(level, random, influence());
            return true;
        }
        if (genome == null || isMature()) {
            return false;
        }
        Optional<Soil> soil = soil();
        int strength = stat(CoreModule.STRENGTH.getKey()) + tier().strengthBonus();
        if (soil.isEmpty() || GrowthCondition.testAll(requirements(), growthContext(soil), strength) != GrowthResponse.FERTILE) {
            return false;
        }
        Plant plant = genome.species().value();
        int target = Math.min(plant.matureStage(), stage + fertilizer.potency());
        while (target > stage && plant.blocksAt(target) > segmentsAvailable()) {
            target--;
        }
        if (target == stage) {
            return false;
        }
        stage = target;
        changed();
        return true;
    }

    // ---------------------------------------------------------------- mutation

    public boolean plant(Genome newGenome) {
        if (genome != null || isCross() || isSegment()) {
            return false;
        }
        genome = newGenome;
        assayed = false;
        stage = 0;
        changed();
        if (level instanceof ServerLevel) {
            invalidateInfluence(); // compute now so goggles show influences right away
        }
        return true;
    }

    /** Plants at a given stage (transplanting keeps growth). */
    /** Plants from an item: seeds, cuttings and satchel entries carry whether their genome has been assayed. */
    public boolean plant(Genome newGenome, ItemStack from) {
        if (!plant(newGenome)) {
            return false;
        }
        setAssayed(from.has(CoreModule.ASSAYED.get()));
        return true;
    }

    public boolean isAssayed() {
        return assayed;
    }

    public void setAssayed(boolean known) {
        if (assayed != known) {
            assayed = known;
            changed();
        }
    }

    /** Stats may be shown: the genome is known, or the server doesn't require assaying. */
    public boolean statsKnown() {
        return assayed || com.kragnoth.cultivar.core.config.CultivarServerConfig.SPEC.isLoaded()
                && !com.kragnoth.cultivar.core.config.CultivarServerConfig.REQUIRE_ASSAY.get();
    }

    /** A seed of this plant: assayed when the plant's genome is known (it's the same genome). */
    public ItemStack seedStack() {
        return CultivarSeedItem.of(genome, assayed);
    }

    /**
     * Display only (Ponder scenes): shows {@code shown} at {@code atStage}, or an empty trellis when null. Clears blight,
     * no planting rules or side effects beyond the usual sync.
     */
    public void setDisplay(@Nullable Genome shown, int atStage) {
        genome = shown;
        assayed = shown != null;
        weed = null;
        weedStage = 0;
        stage = shown == null ? 0 : Math.max(0, Math.min(atStage, shown.species().value().matureStage()));
        changed();
    }

    public boolean plant(Genome newGenome, int atStage) {
        if (!plant(newGenome)) {
            return false;
        }
        stage = Math.max(0, Math.min(atStage, newGenome.species().value().matureStage()));
        changed();
        return true;
    }

    /** Lifts the living plant out (genome + stage), leaving an empty trellis. Weeds stay behind. */
    public Optional<Uprooted> uproot() {
        if (genome == null) {
            return Optional.empty();
        }
        Uprooted out = new Uprooted(genome, stage, assayed);
        assayed = false;
        genome = null;
        stage = 0;
        changed();
        return Optional.of(out);
    }

    public record Uprooted(Genome genome, int stage, boolean assayed) {}

    /** Knocks the weed back one stage (a mature one drops its rake products first); a stage-0 weed is removed. */
    public boolean regressWeed(Consumer<ItemStack> drops) {
        if (weed == null) {
            return false;
        }
        if (weedStage > 0) {
            if (level != null && weedStage >= weed.value().stages() - 1) {
                rollList(weed.value().rakeProducts(), drops, level.random);
            }
            weedStage--;
            changed();
            return true;
        }
        return clearWeeds(drops);
    }

    private static void rollList(List<Product> products, Consumer<ItemStack> drops, RandomSource random) {
        for (Product product : products) {
            if (random.nextFloat() < product.chance()) {
                int count = product.min() + random.nextInt(Math.max(1, product.max() - product.min() + 1));
                if (count > 0) {
                    drops.accept(new ItemStack(product.item(), count));
                }
            }
        }
    }

    /** Harvests a mature plant: product rolls by gain, then back to the after-harvest stage. */
    public boolean harvest(Consumer<ItemStack> drops) {
        if (!isMature() || level == null) {
            return false;
        }
        Plant plant = genome.species().value();
        int after = Math.min(plant.afterHarvestStage(), plant.matureStage());
        // Tall plants yield per segment cut (3-tall cane back to 1 = 2 harvests), like cutting vanilla cane.
        int segmentsCut = Math.max(1, plant.blocksAt(stage) - plant.blocksAt(after));
        for (int i = 0; i < segmentsCut; i++) {
            rollProducts(drops, level.random);
        }
        stage = after;
        changed();
        return true;
    }

    public void rollProducts(Consumer<ItemStack> drops, RandomSource random) {
        if (genome == null) {
            return;
        }
        int rolls = GrowthMath.harvestRolls(stat(CoreModule.GAIN.getKey()));
        for (int i = 0; i < rolls; i++) {
            for (Product product : genome.species().value().products()) {
                if (random.nextFloat() < product.chance()) {
                    int count = product.min() + random.nextInt(Math.max(1, product.max() - product.min() + 1));
                    if (count > 0) {
                        drops.accept(new ItemStack(product.item(), count));
                    }
                }
            }
        }
    }

    /** Sets a weed directly (commands, tests, scene setup). */
    public void setWeed(@Nullable Holder<Weed> newWeed, int newStage) {
        weed = newWeed;
        weedStage = newWeed == null ? 0 : Math.max(0, Math.min(newStage, newWeed.value().stages() - 1));
        changed();
    }

    /** Removes weeds; a mature weed drops its rake products. */
    public boolean clearWeeds(Consumer<ItemStack> drops) {
        if (weed == null || level == null) {
            return false;
        }
        if (weedStage >= weed.value().stages() - 1) {
            rollList(weed.value().rakeProducts(), drops, level.random);
        }
        weed = null;
        weedStage = 0;
        changed();
        return true;
    }

    /** Items returned when the block is broken (trellis items are added by the block). */
    public void collectBreakDrops(Consumer<ItemStack> drops) {
        if (genome != null && level != null) {
            drops.accept(seedStack());
            if (isMature()) {
                rollProducts(drops, level.random);
            }
        }
    }

    // ---------------------------------------------------------------- ticking

    /** On an ideal soil host (Trial Bed): requirements, weeds and mutation habitats are ignored. */
    public boolean onIdealHost() {
        if (level == null) {
            return false;
        }
        BlockPos below = worldPosition.below();
        return level.getBlockState(below).getBlock() instanceof com.kragnoth.cultivar.api.soil.SoilHost host && host.isIdeal(level, below);
    }

    public void randomTick(ServerLevel level, RandomSource random) {
        if (isSegment()) {
            return; // upper segments are scaffolding for the plant rooted below
        }
        InfluenceEffects fx = influence();
        if (!onIdealHost() && tickWeeds(level, random, fx)) {
            return;
        }
        if (isCross()) {
            tickBreeding(level, random, fx);
        } else if (genome != null) {
            tickGrowth(level, random, fx);
        }
    }

    /** Blights whose habitat this trellis meets: the soil (with its water/cloche/heat shifts) and the light here. */
    public List<Holder<Weed>> eligibleWeeds() {
        if (level == null) {
            return List.of();
        }
        Optional<Soil> soil = soil();
        if (soil.isEmpty()) {
            return List.of();
        }
        GrowthContext ctx = growthContext(soil);
        List<Holder<Weed>> out = new java.util.ArrayList<>();
        level.registryAccess().registryOrThrow(CultivarRegistries.WEED).holders().forEach(w -> {
            if (GrowthCondition.testAll(w.value().habitat(), ctx, 1) == GrowthResponse.FERTILE) {
                out.add(w);
            }
        });
        return out;
    }

    private boolean tickWeeds(ServerLevel level, RandomSource random, InfluenceEffects fx) {
        if (!CultivarServerConfig.WEEDS_ENABLED.get()) {
            return false;
        }
        ResourceKey<Stat> resistance = CoreModule.STRENGTH.getKey(); // Strength also resists weeds
        double chance = GrowthMath.weedChance(genome != null, stat(resistance), Genetics.max(resistance), fx);
        if (random.nextDouble() >= chance) {
            return false;
        }
        if (weed == null) {
            List<Holder<Weed>> weeds = eligibleWeeds();
            if (!weeds.isEmpty()) {
                Holder<Weed> candidate = weeds.get(random.nextInt(weeds.size()));
                if (random.nextDouble() < candidate.value().spawnChance()) {
                    weed = candidate;
                    weedStage = 0;
                    changed();
                }
            }
            return true;
        }
        Weed w = weed.value();
        if (weedStage < w.stages() - 1) {
            double soilModifier = soil().map(Soil::growthModifier).orElse(1f);
            if (random.nextDouble() < w.growthChance() * soilModifier) {
                weedStage++;
                changed();
            }
            return true;
        }
        if (w.lethal() && genome != null && stage > 0) {
            stage--;
            changed();
        }
        if (w.aggressive()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos np = worldPosition.relative(dir);
                if (level.isLoaded(np) && level.getBlockEntity(np) instanceof CropBlockEntity neighbour
                        && neighbour.weed == null && !neighbour.isSegment() && random.nextBoolean()) {
                    neighbour.weed = weed;
                    neighbour.weedStage = 0;
                    neighbour.changed();
                }
            }
        }
        return true;
    }

    private void tickGrowth(ServerLevel level, RandomSource random, InfluenceEffects fx) {
        Plant plant = genome.species().value();
        Optional<Soil> soil = soil();
        int strength = stat(CoreModule.STRENGTH.getKey()) + tier().strengthBonus();
        GrowthResponse response = soil.isEmpty() ? GrowthResponse.INFERTILE
                : onIdealHost() ? GrowthResponse.FERTILE
                : GrowthCondition.testAll(requirements(), growthContext(soil), strength);
        switch (response) {
            case KILL -> {
                genome = null;
                stage = 0;
                changed();
            }
            case LETHAL -> {
                if (stage > 0) {
                    stage--;
                    changed();
                }
            }
            case FERTILE -> {
                if (stage < plant.matureStage() && plant.blocksAt(stage + 1) <= segmentsAvailable()) {
                    double chance = GrowthMath.growthChance(plant, stat(CoreModule.GROWTH.getKey()),
                            soil.get().growthModifier(), CultivarServerConfig.GROWTH_MULTIPLIER.get(), fx);
                    if (random.nextDouble() < chance) {
                        stage++;
                        changed();
                    }
                }
            }
            case INFERTILE -> { }
        }
    }

    private void tickBreeding(ServerLevel level, RandomSource random, InfluenceEffects fx) {
        List<Genome> parents = new ArrayList<>(4);
        List<Genome> known = new ArrayList<>(4);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos np = worldPosition.relative(dir);
            if (level.isLoaded(np) && level.getBlockEntity(np) instanceof CropBlockEntity n && n.isMature() && n.weed == null) {
                parents.add(n.genome);
                if (n.assayed) {
                    known.add(n.genome);
                }
            }
        }
        if (parents.isEmpty()) {
            return;
        }
        List<Mutation> mutations = level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION).stream().toList();
        GrowthContext ctx = growthContext(soil());
        var settings = new BreedingEngine.Settings(CultivarServerConfig.STAT_EXPRESSION.get(), fx,
                CultivarServerConfig.CLONE_MUTATIONS.get());
        boolean ideal = onIdealHost();
        BreedingEngine.breed(parents, mutations, m -> ideal || GrowthCondition.testAll(m.conditions(), ctx, 1).canGrow(), settings, random)
                .ifPresent(child -> {
                    level.setBlock(worldPosition, getBlockState().setValue(TrellisBlock.CROSS, false), Block.UPDATE_ALL);
                    genome = child;
                    assayed = known.contains(child); // an exact clone of a known parent is known too
                    stage = 0;
                    changed();
                    boolean novel = parents.stream().noneMatch(p -> p.species().equals(child.species()));
                    if (novel) { // a new species: chime and sparkle so a player notices the find
                        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.3f);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
                    } else {
                        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.CROP_PLANTED,
                                net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1f);
                    }
                });
    }

    private TrellisTier tier() {
        return getBlockState().getBlock() instanceof TrellisBlock t ? t.tier() : TrellisTier.TIMBER;
    }

    // ---------------------------------------------------------------- persistence & sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        // Always write stage: an empty tag is skipped by the client, which would leave a ghost plant after uprooting.
        tag.putInt("stage", stage);
        if (assayed) {
            tag.putBoolean("assayed", true);
        }
        if (genome != null) {
            Genome.CODEC.encodeStart(ops, genome).resultOrPartial(Cultivar.LOGGER::error).ifPresent(t -> tag.put("genome", t));
        }
        if (!activeInfluences.isEmpty()) {
            InfluenceLookup.Active.CODEC.listOf().encodeStart(ops, activeInfluences)
                    .resultOrPartial(Cultivar.LOGGER::error).ifPresent(t -> tag.put("influences", t));
        }
        if (weed != null) {
            RegistryFixedCodec.create(CultivarRegistries.WEED).encodeStart(ops, weed)
                    .resultOrPartial(Cultivar.LOGGER::error).ifPresent(t -> tag.put("weed", t));
            tag.putInt("weed_stage", weedStage);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        // Entries removed from datapacks fail to decode: the plant or weed is dropped instead of crashing.
        genome = tag.contains("genome", Tag.TAG_COMPOUND)
                ? Genome.CODEC.parse(ops, tag.get("genome")).resultOrPartial(Cultivar.LOGGER::warn).orElse(null)
                : null;
        stage = genome == null ? 0 : Math.min(tag.getInt("stage"), genome.species().value().matureStage());
        assayed = genome != null && tag.getBoolean("assayed");
        weed = tag.contains("weed")
                ? RegistryFixedCodec.create(CultivarRegistries.WEED).parse(ops, tag.get("weed")).resultOrPartial(Cultivar.LOGGER::warn).orElse(null)
                : null;
        weedStage = weed == null ? 0 : tag.getInt("weed_stage");
        activeInfluences = tag.contains("influences")
                ? InfluenceLookup.Active.CODEC.listOf().parse(ops, tag.get("influences")).resultOrPartial(Cultivar.LOGGER::warn).orElse(List.of())
                : List.of();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        rerenderOnClient();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        rerenderOnClient();
    }

    /** Rebuilds the chunk section so the crop model picks up the new plant/stage. No-op on the server. */
    private void rerenderOnClient() {
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            // Tall plants are drawn by the segments above too.
            BlockPos p = worldPosition.above();
            for (int i = 1; i < MAX_STACK && level.getBlockState(p).getBlock() instanceof TrellisBlock; i++, p = p.above()) {
                level.sendBlockUpdated(p, level.getBlockState(p), level.getBlockState(p), Block.UPDATE_IMMEDIATE);
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Create Engineer's Goggles readout (the crop readout; the Loupe was folded into goggles). Client-side, synced state only. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (genome == null && weed == null && activeInfluences.isEmpty()) {
            return false;
        }
        CropInfo.lines(this).forEach(line -> tooltip.add(Component.literal("    ").append(line)));
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
        return 1 + (13 * stage) / Math.max(1, genome.species().value().matureStage());
    }

    @Nullable
    public Level levelOrNull() {
        return level;
    }
}
