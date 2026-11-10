package com.kragnoth.cultivar.content.crop;

import com.kragnoth.cultivar.Cultivar;
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
public class CropBlockEntity extends BlockEntity {
    @Nullable
    private Genome genome;
    private int stage;
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

    private boolean isCross() {
        return getBlockState().getValue(TrellisBlock.CROSS);
    }

    private int stat(ResourceKey<Stat> stat) {
        return genome == null ? Genetics.min(stat)
                : Genetics.expressed(genome, stat, CultivarServerConfig.STAT_EXPRESSION.get());
    }

    @Nullable
    private InfluenceEffects cachedInfluence;
    private long influenceExpiry;

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
            cachedInfluence = InfluenceLookup.compute(serverLevel, worldPosition);
            influenceExpiry = now + 1200 + serverLevel.random.nextInt(200);
        }
        return cachedInfluence;
    }

    /** Forces the next {@link #influence()} call to recompute (e.g. a cloche was placed or removed). */
    public void invalidateInfluence() {
        cachedInfluence = null;
    }

    private Optional<Soil> soil() {
        if (level == null) {
            return Optional.empty();
        }
        BlockState below = level.getBlockState(worldPosition.below());
        return level.registryAccess().registryOrThrow(CultivarRegistries.SOIL).stream()
                .filter(soil -> soil.blocks().contains(below.getBlockHolder()))
                .findFirst();
    }

    // ---------------------------------------------------------------- mutation

    public boolean plant(Genome newGenome) {
        if (genome != null || isCross()) {
            return false;
        }
        genome = newGenome;
        stage = 0;
        changed();
        return true;
    }

    /** Plants at a given stage (transplanting keeps growth). */
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
        Uprooted out = new Uprooted(genome, stage);
        genome = null;
        stage = 0;
        changed();
        return Optional.of(out);
    }

    public record Uprooted(Genome genome, int stage) {}

    /** Pruning: clip products from a mature plant, which then drops back to its after-harvest stage. */
    public boolean clip(Consumer<ItemStack> drops) {
        if (!isMature() || level == null || genome.species().value().clipProducts().isEmpty()) {
            return false;
        }
        rollList(genome.species().value().clipProducts(), drops, level.random);
        Plant plant = genome.species().value();
        stage = Math.min(plant.afterHarvestStage(), plant.matureStage());
        changed();
        return true;
    }

    /** Knocks the weed back one stage; a stage-0 weed is removed (with its rake products). */
    public boolean regressWeed(Consumer<ItemStack> drops) {
        if (weed == null) {
            return false;
        }
        if (weedStage > 0) {
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
        rollProducts(drops, level.random);
        Plant plant = genome.species().value();
        stage = Math.min(plant.afterHarvestStage(), plant.matureStage());
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

    /** Removes weeds, dropping their rake products. */
    public boolean clearWeeds(Consumer<ItemStack> drops) {
        if (weed == null || level == null) {
            return false;
        }
        for (Product product : weed.value().rakeProducts()) {
            if (level.random.nextFloat() < product.chance()) {
                drops.accept(new ItemStack(product.item(), Math.max(1, product.min())));
            }
        }
        weed = null;
        weedStage = 0;
        changed();
        return true;
    }

    /** Items returned when the block is broken (trellis items are added by the block). */
    public void collectBreakDrops(Consumer<ItemStack> drops) {
        if (genome != null && level != null) {
            drops.accept(CultivarSeedItem.of(genome));
            if (isMature()) {
                rollProducts(drops, level.random);
            }
        }
    }

    // ---------------------------------------------------------------- ticking

    public void randomTick(ServerLevel level, RandomSource random) {
        InfluenceEffects fx = influence();
        if (tickWeeds(level, random, fx)) {
            return;
        }
        if (isCross()) {
            tickBreeding(level, random, fx);
        } else if (genome != null) {
            tickGrowth(level, random, fx);
        }
    }

    private boolean tickWeeds(ServerLevel level, RandomSource random, InfluenceEffects fx) {
        if (!CultivarServerConfig.WEEDS_ENABLED.get()) {
            return false;
        }
        ResourceKey<Stat> resistance = CoreModule.RESISTANCE.getKey();
        double chance = GrowthMath.weedChance(genome != null, stat(resistance), Genetics.max(resistance), fx);
        if (random.nextDouble() >= chance) {
            return false;
        }
        if (weed == null) {
            List<Holder.Reference<Weed>> weeds = level.registryAccess().registryOrThrow(CultivarRegistries.WEED).holders().toList();
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
                if (level.getBlockEntity(worldPosition.relative(dir)) instanceof CropBlockEntity neighbour
                        && neighbour.weed == null && random.nextBoolean()) {
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
                : GrowthCondition.testAll(plant.requirements(), GrowthContext.of(level, worldPosition, soil), strength);
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
                if (stage < plant.matureStage()) {
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
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof CropBlockEntity n && n.isMature() && n.weed == null) {
                parents.add(n.genome);
            }
        }
        if (parents.isEmpty()) {
            return;
        }
        List<Mutation> mutations = level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION).stream().toList();
        GrowthContext ctx = GrowthContext.of(level, worldPosition, soil());
        var settings = new BreedingEngine.Settings(CultivarServerConfig.STAT_EXPRESSION.get(), fx,
                CultivarServerConfig.CLONE_MUTATIONS.get());
        BreedingEngine.breed(parents, mutations, m -> GrowthCondition.testAll(m.conditions(), ctx, 1).canGrow(), settings, random)
                .ifPresent(child -> {
                    level.setBlock(worldPosition, getBlockState().setValue(TrellisBlock.CROSS, false), Block.UPDATE_ALL);
                    genome = child;
                    stage = 0;
                    changed();
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
        if (genome != null) {
            Genome.CODEC.encodeStart(ops, genome).resultOrPartial(Cultivar.LOGGER::error).ifPresent(t -> tag.put("genome", t));
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
        weed = tag.contains("weed")
                ? RegistryFixedCodec.create(CultivarRegistries.WEED).parse(ops, tag.get("weed")).resultOrPartial(Cultivar.LOGGER::warn).orElse(null)
                : null;
        weedStage = weed == null ? 0 : tag.getInt("weed_stage");
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
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
