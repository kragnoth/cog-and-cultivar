package com.kragnoth.cultivar.content.crop;

import net.minecraft.world.level.BlockGetter;
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
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import com.kragnoth.cultivar.content.crop.tick.CropTickContext;
import com.kragnoth.cultivar.content.crop.tick.CropTickPhases;
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
 * One trellis: a {@link PlantCore} (the plant: genome, stage, growth, harvest) plus what only a trellis does: weeds,
 * cross-breeding on a grafting frame, stacked segments for tall plants, influence caching and client sync.
 * Logic runs only from {@link #randomTick} and player interaction; there is no ticker.
 */
public class CropBlockEntity extends BlockEntity implements IHaveGoggleInformation, com.kragnoth.cultivar.api.crop.CropView {
    /** The plant itself (genome, stage, growth, harvest); the trellis adds weeds, breeding and stacking. */
    private final PlantCore plant = new PlantCore(new PlantCore.Host() {
        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos rootPos() {
            return worldPosition;
        }

        @Override
        public int blocksAvailable() {
            return segmentsAvailable();
        }

        @Override
        public int strengthBonus() {
            return tier().strengthBonus();
        }

        @Override
        public InfluenceEffects influence() {
            return CropBlockEntity.this.influence();
        }

        @Override
        public void changed() {
            CropBlockEntity.this.changed();
        }
    });
    @Nullable
    private Holder<Weed> weed;
    private int weedStage;

    public CropBlockEntity(BlockPos pos, BlockState state) {
        super(CropModule.CROP_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- queries

    @Override
    @Nullable
    public Genome genome() {
        return plant.genome();
    }

    /** The plant this trellis holds (shared plant logic; see {@link PlantCore}). */
    public PlantCore plant() {
        return plant;
    }

    public boolean hasPlant() {
        return plant.hasPlant();
    }

    public boolean hasWeed() {
        return weed != null;
    }

    @Override
    public int stage() {
        return plant.stage();
    }

    public int weedStage() {
        return weedStage;
    }

    @Nullable
    public Holder<Weed> weed() {
        return weed;
    }

    @Override
    public BlockPos rootPos() {
        return worldPosition;
    }

    @Override
    public boolean isMature() {
        return plant.isMature();
    }

    public boolean isCross() {
        return getBlockState().getValue(TrellisBlock.CROSS);
    }

    private int stat(ResourceKey<Stat> stat) {
        return plant.stat(stat);
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

    /** Soil values as conditions see them (water, heat, cloches, conditioners: see {@link PlantCore#humidity}). */
    public Optional<SoilLevels.Humidity> humidity() {
        return plant.humidity();
    }

    public Optional<SoilLevels.Acidity> acidity() {
        return plant.acidity();
    }

    public Optional<SoilLevels.Nutrients> nutrients() {
        return plant.nutrients();
    }

    private GrowthContext growthContext(Optional<Soil> soil) {
        return plant.context(soil);
    }

    // ---------------------------------------------------------------- hardiness

    /** Maximum Strength makes a plant Hardy: it grows on any Cultivar soil regardless of the soil's properties. */
    public boolean isHardy() {
        return plant.isHardy();
    }

    /** The plant's growth requirements as they apply to this crop (soil properties dropped when Hardy). */
    public List<GrowthCondition> requirements() {
        return plant.requirements();
    }

    // ---------------------------------------------------------------- cuttings

    public boolean canTakeCutting() {
        return plant.canTakeCutting();
    }

    /** Snips the plant (see {@link PlantCore#clip}). */
    public boolean clip(Consumer<ItemStack> drops) {
        return plant.clip(drops);
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
        Plant p = plant.species();
        return p == null ? 0 : Math.max(0, p.blocksAt(p.matureStage()) - segmentsAvailable());
    }

    /** Shrinks the plant when a segment above it is removed. Server-side. */
    public void clampToSpace() {
        plant.clampToSpace();
    }

    public Optional<Soil> soil() {
        return plant.soil();
    }

    /** @deprecated use {@link com.kragnoth.cultivar.content.core.lookup.SoilLookup#at}. */
    @Deprecated
    public static Optional<Soil> soilAt(Level level, BlockPos pos) {
        return com.kragnoth.cultivar.content.core.lookup.SoilLookup.at(level, pos);
    }

    // ---------------------------------------------------------------- fertilizer

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
            if (!fertilizer.triggersMutation() || plant.hasPlant()) {
                return false;
            }
            tickBreeding(level, random, influence());
            return true;
        }
        return plant.fertilize(fertilizer, false);
    }

    // ---------------------------------------------------------------- mutation

    public boolean plant(Genome newGenome) {
        if (plant.hasPlant() || isCross() || isSegment()) {
            return false;
        }
        plant.set(newGenome, 0, false);
        if (level instanceof ServerLevel) {
            invalidateInfluence(); // compute now so goggles show influences right away
        }
        return true;
    }

    /** Plants from an item: seeds, cuttings and satchel entries carry whether their genome has been assayed. */
    public boolean plant(Genome newGenome, ItemStack from) {
        if (!plant(newGenome)) {
            return false;
        }
        setAssayed(from.has(CoreModule.ASSAYED.get()));
        return true;
    }

    /** Plants at a given stage (transplanting keeps growth). */
    public boolean plant(Genome newGenome, int atStage) {
        if (!plant(newGenome)) {
            return false;
        }
        plant.setStage(atStage);
        return true;
    }

    public boolean isAssayed() {
        return plant.isAssayed();
    }

    public void setAssayed(boolean known) {
        plant.setAssayed(known);
    }

    /** Stats may be shown: the genome is known, or the server doesn't require assaying. */
    @Override
    public boolean statsKnown() {
        return plant.statsKnown();
    }

    /** A seed of this plant: assayed when the plant's genome is known (it's the same genome). */
    public ItemStack seedStack() {
        return plant.seedStack();
    }

    /**
     * Display only (Ponder scenes): shows {@code shown} at {@code atStage}, or an empty trellis when null. Clears blight,
     * no planting rules or side effects beyond the usual sync.
     */
    public void setDisplay(@Nullable Genome shown, int atStage) {
        weed = null;
        weedStage = 0;
        plant.set(shown, atStage, shown != null);
    }

    /** Lifts the living plant out (genome + stage), leaving an empty trellis. Weeds stay behind. */
    public Optional<Uprooted> uproot() {
        return plant.uproot().map(u -> new Uprooted(u.genome(), u.stage(), u.assayed()));
    }

    public record Uprooted(Genome genome, int stage, boolean assayed) {}

    /** Knocks the weed back one stage (a mature one drops its rake products first); a stage-0 weed is removed. */
    public boolean regressWeed(Consumer<ItemStack> drops) {
        if (weed == null) {
            return false;
        }
        if (weedStage > 0) {
            if (level != null && weedStage >= weed.value().stages() - 1) {
                PlantCore.roll(weed.value().rakeProducts(), drops, level.random);
            }
            weedStage--;
            changed();
            return true;
        }
        return clearWeeds(drops);
    }

    /** Harvests a mature plant: product rolls by gain, then back to the after-harvest stage. */
    public boolean harvest(Consumer<ItemStack> drops) {
        return plant.harvest(drops);
    }

    public void rollProducts(Consumer<ItemStack> drops, RandomSource random) {
        plant.rollProducts(drops, random);
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
            PlantCore.roll(weed.value().rakeProducts(), drops, level.random);
        }
        weed = null;
        weedStage = 0;
        changed();
        return true;
    }

    /** Items returned when the block is broken (trellis items are added by the block). */
    public void collectBreakDrops(Consumer<ItemStack> drops) {
        plant.collectBreakDrops(drops);
    }

    // ---------------------------------------------------------------- ticking

    /** On an ideal soil host (Trial Bed): requirements, weeds and mutation habitats are ignored. */
    public boolean onIdealHost() {
        if (level == null) {
            return false;
        }
        return com.kragnoth.cultivar.content.core.lookup.SoilLookup.isIdealHost(level, worldPosition.below());
    }

    public void randomTick(ServerLevel level, RandomSource random) {
        if (isSegment()) {
            return; // upper segments are scaffolding for the plant rooted below
        }
        CropTickPhases.run(new CropTickContext(this, plant, level, random, influence()));
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

    /** Weed spawn/growth/spread for this tick; true if a weed event consumed it. Called by the weeds phase. */
    public boolean tickWeeds(ServerLevel level, RandomSource random, InfluenceEffects fx) {
        if (!CultivarServerConfig.WEEDS_ENABLED.get()) {
            return false;
        }
        ResourceKey<Stat> resistance = CoreModule.STRENGTH.getKey(); // Strength also resists weeds
        double chance = GrowthMath.weedChance(plant.hasPlant(), stat(resistance), Genetics.max(resistance), fx);
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
        if (w.lethal() && plant.hasPlant() && plant.stage() > 0) {
            plant.setStage(plant.stage() - 1);
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

    /** One breeding attempt on a grafting frame. Called by the breeding phase and by fertilizer. */
    public void tickBreeding(ServerLevel level, RandomSource random, InfluenceEffects fx) {
        List<Genome> parents = new ArrayList<>(4);
        List<Genome> known = new ArrayList<>(4);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos np = worldPosition.relative(dir);
            if (level.isLoaded(np) && level.getBlockEntity(np) instanceof CropBlockEntity n && n.isMature() && n.weed == null) {
                parents.add(n.genome());
                if (n.isAssayed()) {
                    known.add(n.genome());
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
                    plant.set(child, 0, known.contains(child)); // an exact clone of a known parent is known too
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
        plant.save(tag, registries);
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
        plant.load(tag, registries);
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
        if (!plant.hasPlant() && weed == null && activeInfluences.isEmpty()) {
            return false;
        }
        CropInfo.lines(this).forEach(line -> tooltip.add(Component.literal("    ").append(line)));
        return true;
    }

    /** For comparators: 0 empty, 1–14 growth progress, 15 mature. */
    public int comparatorSignal() {
        return plant.comparatorSignal();
    }

    @Nullable
    public Level levelOrNull() {
        return level;
    }
}
