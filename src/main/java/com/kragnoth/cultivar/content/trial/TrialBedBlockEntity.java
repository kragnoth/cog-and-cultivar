package com.kragnoth.cultivar.content.trial;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.kragnoth.cultivar.core.plot.PlotTracker;

import java.util.*;

/**
 * Trial Bed: touching Trial Beds form a trial plot that grows and cross-tests the seeds it's fed, with every limiter
 * removed (the bed is an ideal soil host: requirements, weeds and mutation habitats are ignored).
 * <p>Each bed sets up its own free trellis in a checkerboard: <b>producer</b> squares plant random stocked species
 * (spending a seed), hold them mature for a while so they can parent, then rotate to another species;
 * <b>frame</b> squares are grafting frames. When a frame breeds a child of a new species from its neighbours, the
 * mutation is recorded in the plot's Ledger and the frame is cleared and re-armed.
 * <p>Bonus: every {@code intervalMinutes} the plot also rolls
 * {@code chance × min(1, beds / fullSize)}: on success it picks a random mutation whose two parents are both
 * stocked and that the plot's Ledger doesn't fully know yet, records parents and child in the Ledger (so the
 * recipe shows there), and spends one seed of each parent. Inputs: seeds and a Ledger. Output: the Ledger.
 * Only the plot's leader (lowest position) keeps the timer.
 */
public class TrialBedBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int SEED_SLOTS = 2, LEDGER = 2, SLOTS = 3;
    public static final int MAX_BEDS = 128;

    public final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == LEDGER) {
                return stack.getItem() instanceof LedgerItem;
            }
            return level != null && TrellisBlock.genomeFor(stack, level).isPresent();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == LEDGER ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            needsSync = true;
        }
    };

    /** Automation: seeds and a Ledger go in; the Ledger comes out, and with redstone (dump mode) the seeds too. */
    public final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == LEDGER || isPlotPowered() ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    };

    private int timer;
    private int cooldown;
    private int heldCycles;
    private boolean needsSync;
    private final PlotTracker<TrialBedBlockEntity> tracker =
            new PlotTracker<>(this, TrialBedBlockEntity.class, MAX_BEDS, b -> b.tracker);

    public TrialBedBlockEntity(BlockPos pos, BlockState state) {
        super(TrialModule.TRIAL_BED_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- plot

    public List<TrialBedBlockEntity> plot() {
        return tracker.members();
    }

    /** Redstone on any trial bed pauses the plot and opens its seed stock to pipes. */
    public boolean isPlotPowered() {
        return tracker.isPowered();
    }

    void invalidatePower() {
        tracker.invalidatePower();
    }

    /** Comparator: how much of the plant registry the plot's Ledger has recorded. */
    public int comparatorSignal() {
        ItemStack ledger = ledger();
        if (ledger == null || level == null) {
            return 0;
        }
        int total = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT).size();
        return total == 0 ? 0 : (int) Math.ceil(LedgerItem.data(ledger).researched().size() * 15.0 / total);
    }

    /** One line for Display Links: Ledger progress and crosses left to test. */
    public Component statusLine() {
        ItemStack ledger = ledger();
        if (ledger == null || level == null) {
            return Component.translatable("cultivar.trial.no_ledger");
        }
        int total = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT).size();
        return Component.translatable("cultivar.display.trial", LedgerItem.data(ledger).researched().size(), total, untested().size());
    }

    private boolean isLeader() {
        long mine = worldPosition.asLong();
        for (TrialBedBlockEntity bed : plot()) {
            if (bed.worldPosition.asLong() < mine) {
                return false;
            }
        }
        return true;
    }

    /** Chance per roll for this plot's size. */
    public double rollChance() {
        double size = Math.min(1.0, plot().size() / (double) CultivarServerConfig.TRIAL_FULL_SIZE.get());
        return CultivarServerConfig.TRIAL_CHANCE.get() * size;
    }

    private @Nullable ItemStack ledger() {
        for (TrialBedBlockEntity bed : plot()) {
            ItemStack l = bed.inventory.getStackInSlot(LEDGER);
            if (!l.isEmpty()) {
                return l;
            }
        }
        return null;
    }

    /** Species stocked anywhere in the plot. */
    private Set<ResourceKey<Plant>> stocked() {
        Set<ResourceKey<Plant>> out = new HashSet<>();
        for (TrialBedBlockEntity bed : plot()) {
            for (int i = 0; i < SEED_SLOTS; i++) {
                TrellisBlock.genomeFor(bed.inventory.getStackInSlot(i), level)
                        .flatMap(g -> g.species().unwrapKey()).ifPresent(out::add);
            }
        }
        return out;
    }

    /** Mutations the plot could still discover: both parents stocked, not yet fully in the Ledger. */
    public List<Mutation> untested() {
        ItemStack ledger = ledger();
        if (ledger == null || level == null) {
            return List.of();
        }
        var data = LedgerItem.data(ledger);
        Set<ResourceKey<Plant>> stock = stocked();
        List<Mutation> out = new ArrayList<>();
        for (Mutation m : level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION)) {
            var a = m.parentA().unwrapKey();
            var b = m.parentB().unwrapKey();
            var c = m.child().unwrapKey();
            if (a.isEmpty() || b.isEmpty() || c.isEmpty() || !stock.contains(a.get()) || !stock.contains(b.get())) {
                continue;
            }
            if (!(data.knows(a.get()) && data.knows(b.get()) && data.knows(c.get()))) {
                out.add(m);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- work

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (needsSync && server.getGameTime() % 20 == (worldPosition.asLong() & 15)) {
            needsSync = false;
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (isPlotPowered()) {
            return; // redstone: the plot pauses and its seeds can be pulled out
        }
        if (--cooldown <= 0) {
            cooldown = 10;
            work(server, server.random);
        }
        if (server.getGameTime() % 20 != 0 || !isLeader()) {
            return;
        }
        timer += 20;
        if (timer >= CultivarServerConfig.TRIAL_INTERVAL_MINUTES.get() * 1200) {
            timer = 0;
            roll(server, server.random, rollChance());
        }
    }

    /** Producer squares on even (x + z), grafting frames on odd. */
    public boolean isFrameSquare() {
        return ((worldPosition.getX() + worldPosition.getZ()) & 1) == 1;
    }

    /** One growing cycle for this bed's square; public so game tests can drive it. */
    public void work(ServerLevel server, RandomSource random) {
        BlockPos above = worldPosition.above();
        BlockState there = server.getBlockState(above);
        if (there.canBeReplaced() && server.getFluidState(above).isEmpty()) {
            server.setBlock(above, com.kragnoth.cultivar.content.crop.CropModule.TIMBER_TRELLIS.get().defaultBlockState()
                    .setValue(TrellisBlock.CROSS, isFrameSquare()), Block.UPDATE_ALL);
            return;
        }
        if (!(server.getBlockEntity(above) instanceof com.kragnoth.cultivar.content.crop.CropBlockEntity crop) || crop.isSegment()) {
            return;
        }
        if (isFrameSquare()) {
            if (crop.isCross()) {
                if (random.nextInt(4) == 0) {
                    there.randomTick(server, above, random); // extra breeding attempts
                }
                return;
            }
            if (crop.hasPlant()) {
                // A child was born: was it a mutation? Record it, then clear and re-arm the frame.
                recordIfMutation(server, crop);
                crop.uproot();
            }
            server.setBlock(above, server.getBlockState(above).setValue(TrellisBlock.CROSS, true), Block.UPDATE_ALL);
            return;
        }
        if (!crop.hasPlant()) {
            plantRandom(crop, random);
            return;
        }
        if (crop.isMature()) {
            if (heldCycles++ >= CultivarServerConfig.TRIAL_HOLD_CYCLES.get()) {
                heldCycles = 0;
                crop.uproot();
                plantRandom(crop, random); // rotate to test new pairs
            }
            return;
        }
        if (random.nextInt(8) == 0) {
            there.randomTick(server, above, random); // research rigs grow briskly
        }
    }

    private void plantRandom(com.kragnoth.cultivar.content.crop.CropBlockEntity crop, RandomSource random) {
        List<int[]> options = new ArrayList<>();
        List<TrialBedBlockEntity> beds = plot();
        for (int b = 0; b < beds.size(); b++) {
            for (int i = 0; i < SEED_SLOTS; i++) {
                if (!beds.get(b).inventory.getStackInSlot(i).isEmpty()) {
                    options.add(new int[]{b, i});
                }
            }
        }
        if (options.isEmpty()) {
            return;
        }
        int[] pick = options.get(random.nextInt(options.size()));
        TrialBedBlockEntity bed = beds.get(pick[0]);
        ItemStack from = bed.inventory.getStackInSlot(pick[1]);
        TrellisBlock.genomeFor(from, level).ifPresent(g -> {
            if (crop.plant(g, from)) {
                bed.inventory.extractItem(pick[1], 1, false);
            }
        });
    }

    private void recordIfMutation(ServerLevel server, com.kragnoth.cultivar.content.crop.CropBlockEntity child) {
        ItemStack ledger = ledger();
        if (ledger == null || child.genome() == null) {
            return;
        }
        Set<ResourceKey<Plant>> neighbours = new HashSet<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (server.getBlockEntity(child.getBlockPos().relative(d)) instanceof com.kragnoth.cultivar.content.crop.CropBlockEntity n
                    && n.genome() != null) {
                n.genome().species().unwrapKey().ifPresent(neighbours::add);
            }
        }
        var childKey = child.genome().species().unwrapKey();
        if (childKey.isEmpty() || neighbours.contains(childKey.get())) {
            return; // an ordinary cross, not a mutation
        }
        for (Mutation m : server.registryAccess().registryOrThrow(CultivarRegistries.MUTATION)) {
            var a = m.parentA().unwrapKey();
            var b = m.parentB().unwrapKey();
            if (m.child().is(childKey.get()) && a.isPresent() && b.isPresent()
                    && neighbours.contains(a.get()) && neighbours.contains(b.get())) {
                LedgerItem.record(ledger, a.get());
                LedgerItem.record(ledger, b.get());
                LedgerItem.record(ledger, childKey.get());
                for (TrialBedBlockEntity bed : plot()) {
                    bed.needsSync = true;
                }
                BlockPos p = child.getBlockPos();
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0);
                server.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.4f);
                return;
            }
        }
    }

    /** Bonus discovery roll; public so game tests can drive it. Returns the mutation found, if any. */
    public Optional<Mutation> roll(ServerLevel server, RandomSource random, double chance) {
        if (random.nextDouble() >= chance) {
            return Optional.empty();
        }
        List<Mutation> candidates = untested();
        ItemStack ledger = ledger();
        if (candidates.isEmpty() || ledger == null) {
            return Optional.empty();
        }
        Mutation found = candidates.get(random.nextInt(candidates.size()));
        found.parentA().unwrapKey().ifPresent(k -> LedgerItem.record(ledger, k));
        found.parentB().unwrapKey().ifPresent(k -> LedgerItem.record(ledger, k));
        found.child().unwrapKey().ifPresent(k -> LedgerItem.record(ledger, k));
        if (CultivarServerConfig.TRIAL_CONSUMES_SEEDS.get()) {
            spend(found.parentA().unwrapKey().orElseThrow());
            spend(found.parentB().unwrapKey().orElseThrow());
        }
        for (TrialBedBlockEntity bed : plot()) {
            bed.needsSync = true;
            if (random.nextInt(3) == 0) {
                BlockPos p = bed.worldPosition.above();
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0);
            }
        }
        server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
        return Optional.of(found);
    }

    private void spend(ResourceKey<Plant> species) {
        for (TrialBedBlockEntity bed : plot()) {
            for (int i = 0; i < SEED_SLOTS; i++) {
                Optional<Genome> g = TrellisBlock.genomeFor(bed.inventory.getStackInSlot(i), level);
                if (g.isPresent() && g.get().species().is(species)) {
                    bed.inventory.extractItem(i, 1, false);
                    return;
                }
            }
        }
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 0.5, worldPosition.getZ(), inventory.getStackInSlot(i));
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ---------------------------------------------------------------- persistence / sync / goggles

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Timer", timer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        timer = tag.getInt("Timer");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Timer", timer);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int full = CultivarServerConfig.TRIAL_FULL_SIZE.get();
        ItemStack ledger = ledger();
        tooltip.add(Component.translatable("cultivar.trial.plot", plot().size(), full).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("cultivar.trial.chance", String.format("%.1f", rollChance() * 100),
                CultivarServerConfig.TRIAL_INTERVAL_MINUTES.get()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("cultivar.trial.stock", stocked().size()).withStyle(ChatFormatting.GRAY));
        if (ledger == null) {
            tooltip.add(Component.translatable("cultivar.trial.no_ledger").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("cultivar.trial.ledger", LedgerItem.data(ledger).researched().size(),
                    untested().size()).withStyle(ChatFormatting.GREEN));
        }
        return true;
    }
}
