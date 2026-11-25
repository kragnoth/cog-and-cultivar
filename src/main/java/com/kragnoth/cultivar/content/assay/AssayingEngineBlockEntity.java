package com.kragnoth.cultivar.content.assay;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.kragnoth.cultivar.core.plot.PlotItemHandler;
import com.kragnoth.cultivar.core.plot.PlotTracker;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One Assayer. Touching Assayers (any tier) form a <b>unit</b>, like a Planter Bed plot: one pipe, funnel or hand
 * serves all of them ({@link #unitHandler()}), seeds spread to idle members, and a Botanical Ledger anywhere in the unit
 * records for every member. Each member assays one seed at a time; more members, more seeds in parallel.
 *
 * <p>Work is server side: progress advances by |RPM| / 32 × the tier's speed per tick (100 = one seed), so an andesite
 * Assayer at 32 RPM takes 5 s. With a Ledger, each assayed seed records its species and has a chance
 * ({@code assayer.familyTreeChance}) to also record one cross that produces it, so that cross appears in JEI.
 */
public class AssayingEngineBlockEntity extends KineticBlockEntity {
    public static final int SEED_IN = 0, LEDGER = 1, SEED_OUT = 2;
    public static final float WORK = 100f;
    /** Ticks between spreading queued seeds to idle members. */
    private static final int SPREAD_INTERVAL = 10;

    public final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case SEED_IN -> isAssayable(stack);
                case LEDGER -> stack.getItem() instanceof LedgerItem;
                default -> true;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == LEDGER ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncSoon = true;
        }
    };

    /** Automation view of this member: inputs can be inserted, only outputs extracted. */
    public final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return 3;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot <= LEDGER ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= SEED_OUT ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot <= LEDGER && inventory.isItemValid(slot, stack);
        }
    };

    private final PlotTracker<AssayingEngineBlockEntity> unit = new PlotTracker<>(this, AssayingEngineBlockEntity.class,
            CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.ASSAY_MAX_UNIT.get() : 81, be -> be.unit);
    private float progress;
    private boolean syncSoon;

    public AssayingEngineBlockEntity(BlockPos pos, BlockState state) {
        this(AssayModule.ENGINE_BE.get(), pos, state);
    }

    public AssayingEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static boolean isAssayable(ItemStack stack) {
        return stack.has(CoreModule.GENOME.get())
                && (stack.getItem() instanceof CultivarSeedItem || stack.getItem() instanceof com.kragnoth.cultivar.content.tools.CuttingItem);
    }

    public AssayTier tier() {
        return getBlockState().getBlock() instanceof AssayingEngineBlock block ? block.tier() : AssayTier.ANDESITE;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ---------------------------------------------------------------- unit

    /** Every Assayer in this one's unit (touching sideways, any tier), this one included. */
    public List<AssayingEngineBlockEntity> unit() {
        return unit.members();
    }

    /** One handler for the whole unit: each member's input, ledger and output slots end to end. */
    public IItemHandler unitHandler() {
        return new PlotItemHandler(() -> unit().stream().map(b -> b.automation).toList());
    }

    /** Puts seeds into the least-loaded members first, a ledger into the unit if it has none. Returns what didn't fit. */
    public ItemStack insertIntoUnit(ItemStack stack) {
        if (stack.getItem() instanceof LedgerItem) {
            if (unitLedger() != null) {
                return stack;
            }
            return inventory.insertItem(LEDGER, stack, false);
        }
        if (!isAssayable(stack)) {
            return stack;
        }
        ItemStack rest = stack.copy();
        List<AssayingEngineBlockEntity> byLoad = new ArrayList<>(unit());
        byLoad.sort(Comparator.comparingInt(b -> b.inventory.getStackInSlot(SEED_IN).getCount()));
        // An even share each first (least loaded first), then whatever is left wherever it fits.
        int share = Math.max(1, (rest.getCount() + byLoad.size() - 1) / byLoad.size());
        for (AssayingEngineBlockEntity member : byLoad) {
            if (rest.isEmpty()) {
                break;
            }
            ItemStack part = rest.split(Math.min(share, rest.getCount()));
            rest.grow(member.inventory.insertItem(SEED_IN, part, false).getCount());
        }
        for (AssayingEngineBlockEntity member : byLoad) {
            if (rest.isEmpty()) {
                break;
            }
            rest = member.inventory.insertItem(SEED_IN, rest, false);
        }
        return rest;
    }

    /** Shift + empty hand: every finished seed in the unit; if none, the queued seeds; if none, the ledger. */
    public List<ItemStack> takeFromUnit() {
        for (int slot : new int[]{SEED_OUT, SEED_IN, LEDGER}) {
            List<ItemStack> taken = new ArrayList<>();
            for (AssayingEngineBlockEntity member : unit()) {
                ItemStack s = member.inventory.extractItem(slot, 64, false);
                if (!s.isEmpty()) {
                    taken.add(s);
                }
            }
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return List.of();
    }

    @Nullable
    private ItemStack unitLedger() {
        for (AssayingEngineBlockEntity member : unit()) {
            ItemStack l = member.inventory.getStackInSlot(LEDGER);
            if (!l.isEmpty()) {
                return l;
            }
        }
        return null;
    }

    /** An idle member with nothing queued takes half the biggest queue in the unit. */
    private void spread() {
        if (!inventory.getStackInSlot(SEED_IN).isEmpty()) {
            return;
        }
        AssayingEngineBlockEntity busiest = null;
        int most = 1;
        for (AssayingEngineBlockEntity member : unit()) {
            int n = member.inventory.getStackInSlot(SEED_IN).getCount();
            if (n > most) {
                most = n;
                busiest = member;
            }
        }
        if (busiest != null) {
            ItemStack half = busiest.inventory.extractItem(SEED_IN, most / 2, false);
            busiest.inventory.insertItem(SEED_IN, inventory.insertItem(SEED_IN, half, false), false);
        }
    }

    // ---------------------------------------------------------------- work

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if ((level.getGameTime() + worldPosition.asLong()) % SPREAD_INTERVAL == 0) {
            spread();
        }
        float before = progress;
        advance(Math.abs(getSpeed()));
        // Sync roughly once a second while working, on every start/finish, and after inventory changes (goggles, render).
        if (syncSoon || (int) (before / 20) != (int) (progress / 20) || (before == 0) != (progress == 0)) {
            syncSoon = false;
            sendData();
        }
    }

    /** Advances work by {@code rpm} × the tier's speed; public so game tests can drive it without a kinetic network. */
    public void advance(float rpm) {
        if (rpm <= 0 || !canWork()) {
            progress = 0;
            return;
        }
        progress += (float) (rpm / 32f * tier().speed());
        if (progress >= WORK) {
            progress = 0;
            finish();
        }
    }

    private ItemStack result() {
        ItemStack out = inventory.getStackInSlot(SEED_IN).copyWithCount(1);
        out.set(CoreModule.ASSAYED.get(), Unit.INSTANCE);
        return out;
    }

    private boolean canWork() {
        return !inventory.getStackInSlot(SEED_IN).isEmpty()
                && inventory.insertItem(SEED_OUT, result(), true).isEmpty();
    }

    private void finish() {
        ItemStack out = result();
        Genome genome = out.get(CoreModule.GENOME.get());
        inventory.extractItem(SEED_IN, 1, false);
        inventory.insertItem(SEED_OUT, out, false);
        ItemStack ledger = unitLedger();
        if (ledger != null && genome != null) {
            genome.species().unwrapKey().ifPresent(key -> {
                LedgerItem.record(ledger, key);
                if (level != null && level.random.nextDouble() < familyChance()) {
                    recordFamily(ledger, key);
                }
            });
            for (AssayingEngineBlockEntity member : unit()) {
                member.inventory.setStackInSlot(LEDGER, member.inventory.getStackInSlot(LEDGER)); // mark changed
            }
        }
        if (level != null) {
            com.simibubi.create.AllSoundEvents.CLIPBOARD_CHECKMARK.playOnServer(level, worldPosition, 0.5f, 1f);
        }
        sendData();
    }

    private static double familyChance() {
        return CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.ASSAY_FAMILY_CHANCE.get() : 0.2;
    }

    /**
     * Family tree, from one assayed {@code species}:
     * <ul>
     *   <li><b>Ancestry</b>: one random cross from anywhere in its ancestry (its parents' crosses, theirs, and so on)
     *       that the Ledger doesn't fully know yet gets recorded: both parents and the child, so it shows in JEI.
     *       Repeated assays fill in the whole tree.</li>
     *   <li><b>Descendants</b>: every cross it is a parent of, and every cross those children are parents of (two
     *       generations), becomes a {@link LedgerItem.Hint}: JEI shows the child and the known parent, the other
     *       parent as "?".</li>
     * </ul>
     * Returns true if the Ledger learned anything.
     */
    public boolean recordFamily(ItemStack ledger, ResourceKey<Plant> species) {
        if (level == null) {
            return false;
        }
        var crosses = level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION);
        boolean learned = false;

        // Ancestry, breadth first up the tree.
        List<Mutation> ancestry = new ArrayList<>();
        java.util.Set<ResourceKey<Plant>> seen = new java.util.HashSet<>(java.util.List.of(species));
        java.util.ArrayDeque<ResourceKey<Plant>> queue = new java.util.ArrayDeque<>(seen);
        while (!queue.isEmpty()) {
            ResourceKey<Plant> plant = queue.poll();
            for (Mutation m : crosses) {
                if (m.child().is(plant) && !ancestry.contains(m)) {
                    ancestry.add(m);
                    for (var parent : List.of(m.parentA(), m.parentB())) {
                        parent.unwrapKey().filter(seen::add).ifPresent(queue::add);
                    }
                }
            }
        }
        LedgerItem.Data known = LedgerItem.data(ledger);
        List<Mutation> open = ancestry.stream().filter(m -> !fullyKnown(known, m)).toList();
        if (!open.isEmpty()) {
            Mutation m = open.get(level.random.nextInt(open.size()));
            for (var plant : List.of(m.parentA(), m.parentB(), m.child())) {
                plant.unwrapKey().ifPresent(k -> LedgerItem.record(ledger, k));
            }
            learned = true;
        }

        // Descendants, two generations down, as half-known hints.
        LedgerItem.Data now = LedgerItem.data(ledger);
        List<LedgerItem.Hint> hints = new ArrayList<>();
        java.util.Set<ResourceKey<Plant>> generation = java.util.Set.of(species);
        for (int depth = 0; depth < 2 && !generation.isEmpty(); depth++) {
            java.util.Set<ResourceKey<Plant>> next = new java.util.HashSet<>();
            for (Mutation m : crosses) {
                var key = crosses.getResourceKey(m);
                for (ResourceKey<Plant> parent : generation) {
                    if (key.isPresent() && (m.parentA().is(parent) || m.parentB().is(parent))) {
                        if (!fullyKnown(now, m)) {
                            hints.add(new LedgerItem.Hint(key.get(), parent));
                        }
                        m.child().unwrapKey().ifPresent(next::add);
                        break;
                    }
                }
            }
            generation = next;
        }
        learned |= LedgerItem.hint(ledger, hints) > 0;

        if (learned && level instanceof ServerLevel server) {
            BlockPos p = worldPosition;
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 8, 0.25, 0.1, 0.25, 0);
            server.playSound(null, p, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        return learned;
    }

    private static boolean fullyKnown(LedgerItem.Data known, Mutation m) {
        return java.util.stream.Stream.of(m.parentA(), m.parentB(), m.child())
                .allMatch(h -> h.unwrapKey().map(known::knows).orElse(false));
    }

    public float progress() {
        return progress;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), inventory.getStackInSlot(i));
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /** Comparator: how full the unit's finished-seed outputs are (0-15), so a contraption can wait for a batch. */
    public int comparatorSignal() {
        float fill = 0;
        List<AssayingEngineBlockEntity> members = unit();
        for (AssayingEngineBlockEntity member : members) {
            fill += member.inventory.getStackInSlot(SEED_OUT).getCount() / 64f;
        }
        return fill <= 0 ? 0 : 1 + (int) (fill / members.size() * 14);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putFloat("Progress", progress);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        progress = tag.getFloat("Progress");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        ItemStack in = inventory.getStackInSlot(SEED_IN);
        Component status = in.isEmpty() ? Component.translatable("cultivar.assay.idle")
                : Component.translatable("cultivar.assay.progress", CultivarSeedItem.plantName(in.get(CoreModule.GENOME.get())),
                (int) (progress * 100 / WORK));
        tooltip.add(Component.literal("    ").append(status.copy().withStyle(ChatFormatting.GRAY)));
        List<AssayingEngineBlockEntity> members = unit();
        int queued = 0, done = 0;
        boolean ledger = false;
        for (AssayingEngineBlockEntity m : members) {
            queued += m.inventory.getStackInSlot(SEED_IN).getCount();
            done += m.inventory.getStackInSlot(SEED_OUT).getCount();
            ledger |= !m.inventory.getStackInSlot(LEDGER).isEmpty();
        }
        tooltip.add(Component.literal("    ").append(Component.translatable("cultivar.assay.unit", members.size(), queued, done)
                .withStyle(ChatFormatting.DARK_GRAY)));
        tooltip.add(Component.literal("    ").append(Component.translatable(ledger ? "cultivar.assay.ledger" : "cultivar.assay.no_ledger")
                .withStyle(ledger ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
        return true;
    }
}
