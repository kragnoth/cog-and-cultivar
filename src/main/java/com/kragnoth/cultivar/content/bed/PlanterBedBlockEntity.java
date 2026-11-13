package com.kragnoth.cultivar.content.bed;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Fertilizer;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Planter Bed: a self-contained growing station under one trellis. Holds a soil (acts as it), seeds, fertilizer,
 * 9 output slots and 4000 mB of water. Every {@code cycle} ticks it plants an empty trellis above from the plot's
 * seeds, harvests a mature crop into the plot's outputs (produce only; seeds come from the Seed Satchel), and,
 * with water, gives the crop extra growth ticks and +1 humidity; it occasionally spends a fertilizer.
 */
public class PlanterBedBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    /** Seed stock (4 slots, best kept), fertilizer, trellis and cloche supply, 9 outputs. */
    public static final int SEEDS = 0, SEED_SLOTS = 4, FERTILIZER = 4, TRELLIS = 5, CLOCHE = 6, OUT_START = 7, SLOTS = 16;
    public static final int CAPACITY = 4000;

    public final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (level == null) {
                return false;
            }
            if (slot < SEED_SLOTS) {
                return TrellisBlock.genomeFor(stack, level).isPresent();
            }
            return switch (slot) {
                case FERTILIZER -> CropBlockEntity.fertilizerFor(level, stack).isPresent();
                case TRELLIS -> stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof TrellisBlock;
                case CLOCHE -> stack.getItem() instanceof com.kragnoth.cultivar.content.cloche.BellClocheItem;
                default -> true;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            needsSync = true;
        }
    };

    /**
     * Automation: seeds, fertilizer, trellises and cloches go in. Normally only the crop outputs come out; while the
     * plot is powered (dump mode) every slot can be pulled, so the whole plot empties through its pipes.
     */
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
            return slot < OUT_START ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= OUT_START || isPlotPowered() ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot < OUT_START && inventory.isItemValid(slot, stack);
        }
    };

    public final FluidTank tank = new FluidTank(CAPACITY, fs -> fs.getFluid().is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            needsSync = true;
            syncWaterIfChanged();
        }
    };

    private @Nullable BlockState soil;
    /** Inventory/tank changed: send clients (goggles) an update, at most once a second. */
    private boolean needsSync;
    private boolean syncedWater;
    private int cooldown;
    private int ticksSinceDrain;
    private List<PlanterBedBlockEntity> network;
    private long networkTick = Long.MIN_VALUE;
    /** This bed's trellis is a grafting frame the bed keeps re-arming (set when it sees a cross above). */
    private boolean breeding;
    /** Cycles the crop above has been held mature for a neighbouring breeder. */
    private int heldCycles;

    public PlanterBedBlockEntity(BlockPos pos, BlockState state) {
        super(PlanterBedModule.BED_BE.get(), pos, state);
    }

    public @Nullable BlockState soil() {
        return soil;
    }

    public void setSoil(@Nullable BlockState soil) {
        if (level != null && soil != this.soil) {
            // New or emptied soil: conditioners applied to the old fill don't carry over.
            com.kragnoth.cultivar.content.soil.SoilAmendments.clear(level, worldPosition);
        }
        this.soil = soil;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public boolean hasWater() {
        return level != null && level.isClientSide() ? syncedWater : !tank.isEmpty();
    }

    private void syncWaterIfChanged() {
        if (level != null && !level.isClientSide() && syncedWater != !tank.isEmpty()) {
            syncedWater = !tank.isEmpty();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The plot this bed belongs to, recomputed at most once per game tick. */
    public List<PlanterBedBlockEntity> cachedNetwork() {
        long now = level == null ? 0 : level.getGameTime();
        if (network == null || networkTick != now) {
            // One flood fill per plot per tick: share the result with every member.
            List<PlanterBedBlockEntity> members = BedNetwork.members(this);
            for (PlanterBedBlockEntity bed : members) {
                bed.network = members;
                bed.networkTick = now;
            }
        }
        return network;
    }

    private boolean plotPowered;
    private long poweredTick = Long.MIN_VALUE;

    /** True if any bed in the plot has a redstone signal: the plot strips down and dumps everything. */
    public boolean isPlotPowered() {
        long now = level == null ? 0 : level.getGameTime();
        if (poweredTick != now) {
            boolean powered = false;
            List<PlanterBedBlockEntity> plot = cachedNetwork();
            for (PlanterBedBlockEntity bed : plot) {
                if (bed.level != null && bed.level.hasNeighborSignal(bed.worldPosition)) {
                    powered = true;
                    break;
                }
            }
            for (PlanterBedBlockEntity bed : plot) {
                bed.plotPowered = powered;
                bed.poweredTick = now;
            }
        }
        return plotPowered;
    }

    /** A neighbour changed (maybe redstone): re-check the plot's power on the next query. */
    void invalidatePower() {
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            bed.poweredTick = Long.MIN_VALUE;
        }
    }

    public boolean isBreeding() {
        return breeding;
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
        boolean watered = !tank.isEmpty();
        if (watered && ++ticksSinceDrain >= CultivarServerConfig.BED_TICKS_PER_MB.get()) {
            ticksSinceDrain = 0;
            tank.drain(1, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = CultivarServerConfig.BED_CYCLE.get();
        work(server, watered);
    }

    /**
     * One work cycle; public so game tests can drive it. The plot works as one breeding machine:
     * <ul>
     *   <li><b>Breeder beds</b> (a grafting frame above): extra breeding attempts with water, mutation fertilizer
     *       spent on the frame; a newborn child is taken as a seed into the plot's ranked stock and the frame is
     *       re-armed.</li>
     *   <li><b>Producer beds</b>: plant the best seed in stock; at maturity hold briefly if a breeder is next door
     *       (so it can parent), then harvest into the outputs, keep a seed if the stock has room, and swap the
     *       plant for a better seed of the same species if the stock has one.</li>
     * </ul>
     * Stock ranking uses {@link SeedQuality}; seeds pushed out of a full stock go to the outputs (cull them).
     */
    public void work(ServerLevel server, boolean watered) {
        BlockPos above = worldPosition.above();
        // Redstone on any bed: the plot strips down (cloche and trellis back into stock, plants banked as seeds)
        // and dumps everything through its pipes (see automation).
        if (isPlotPowered()) {
            strip(server, above);
            return;
        }
        // Build: set a stocked trellis on an empty spot, then cover it with a stocked cloche.
        if (server.getBlockState(above).canBeReplaced() && server.getFluidState(above).isEmpty()) {
            Slot t = equipment(TRELLIS);
            if (t != null) {
                server.setBlock(above, ((TrellisBlock) ((net.minecraft.world.item.BlockItem) t.stack().getItem()).getBlock()).defaultBlockState(), Block.UPDATE_ALL);
                t.bed().inventory.extractItem(t.index(), 1, false);
            }
            return;
        }
        if (!(server.getBlockEntity(above) instanceof CropBlockEntity crop) || crop.isSegment()) {
            breeding = false;
            return;
        }
        var random = server.random;
        BlockState trellis = server.getBlockState(above);
        if (!trellis.getValue(TrellisBlock.CLOCHE) && !trellis.getValue(TrellisBlock.CROSS) && !TrellisBlock.hasSegmentAbove(server, above)) {
            Slot c = equipment(CLOCHE);
            if (c != null && c.stack().getItem() instanceof com.kragnoth.cultivar.content.cloche.BellClocheItem cloche) {
                trellis = cloche.fit(trellis);
                server.setBlock(above, trellis, Block.UPDATE_ALL);
                c.bed().inventory.extractItem(c.index(), 1, false);
            }
        }

        if (crop.isCross()) {
            breeding = true;
            if (watered && random.nextInt(Math.max(1, CultivarServerConfig.BED_GROWTH_ODDS.get() / 5)) == 0) {
                trellis.randomTick(server, above, random); // breeding attempt
            }
            spendFertilizer(server, crop, random, above);
            return;
        }
        if (breeding) {
            if (crop.hasPlant()) {
                // A child was born on our frame: bank it as a seed and re-arm.
                offerSeed(crop.seedStack());
                crop.uproot();
                server.setBlock(above, server.getBlockState(above).setValue(TrellisBlock.CROSS, true), Block.UPDATE_ALL);
                return;
            }
            breeding = false; // the frame was taken down by hand
        }
        if (!crop.hasPlant()) {
            bestSeed(null).ifPresent(slot -> plantFrom(crop, slot));
            return;
        }
        if (crop.isMature()) {
            if (nextToBreeder(server) && heldCycles++ < CultivarServerConfig.BED_BREED_HOLD.get()) {
                return; // stay mature a little longer so the neighbouring frame can use us as a parent
            }
            heldCycles = 0;
            if (!hasOutputRoom()) {
                return;
            }
            Genome before = crop.genome();
            ItemStack beforeSeed = crop.seedStack();
            List<ItemStack> drops = new ArrayList<>();
            crop.harvest(drops::add);
            drops.forEach(d -> output(server, above, d));
            if (random.nextDouble() < com.kragnoth.cultivar.content.tools.SeedSatchelItem.seedChance(before) && stockHasRoom()) {
                offerSeed(beforeSeed);
            }
            // Upgrade: a better seed of the same species replaces this plant.
            bestSeed(before).ifPresent(slot -> {
                crop.uproot();
                plantFrom(crop, slot);
            });
            return;
        }
        if (watered && random.nextInt(CultivarServerConfig.BED_GROWTH_ODDS.get()) == 0) {
            trellis.randomTick(server, above, random);
        }
        spendFertilizer(server, crop, random, above);
    }

    private @Nullable Slot equipment(int slot) {
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            if (!bed.inventory.getStackInSlot(slot).isEmpty()) {
                return new Slot(bed, slot);
            }
        }
        return null;
    }

    /** Puts a trellis or cloche back into the plot's supply, or the outputs if the supply is full. */
    private void restock(ServerLevel server, BlockPos at, ItemStack stack, int slot) {
        ItemStack rest = stack;
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            rest = bed.inventory.insertItem(slot, rest, false);
            if (rest.isEmpty()) {
                return;
            }
        }
        output(server, at, rest);
    }

    private void strip(ServerLevel server, BlockPos above) {
        BlockState state = server.getBlockState(above);
        if (!(state.getBlock() instanceof TrellisBlock)) {
            breeding = false;
            return;
        }
        // Top of the stack first: segments come off before the base.
        BlockPos top = above;
        while (TrellisBlock.hasSegmentAbove(server, top) && top.getY() - above.getY() < CropBlockEntity.MAX_STACK - 1) {
            top = top.above();
        }
        BlockState topState = server.getBlockState(top);
        if (!top.equals(above)) {
            restock(server, above, new ItemStack(topState.getBlock()), TRELLIS);
            server.setBlock(top, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (state.getValue(TrellisBlock.CLOCHE)) {
            server.setBlock(above, state.setValue(TrellisBlock.CLOCHE, false), Block.UPDATE_ALL);
            restock(server, above, new ItemStack(state.getValue(TrellisBlock.CLOCHE_TYPE).item()), CLOCHE);
            return;
        }
        if (server.getBlockEntity(above) instanceof CropBlockEntity crop && crop.hasPlant()) {
            offerSeed(crop.seedStack());
            crop.uproot();
        }
        int trellises = state.getValue(TrellisBlock.CROSS) ? 2 : 1;
        server.setBlock(above, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        restock(server, above, new ItemStack(state.getBlock(), trellises), TRELLIS);
        breeding = false;
    }

    private void spendFertilizer(ServerLevel server, CropBlockEntity crop, net.minecraft.util.RandomSource random, BlockPos above) {
        if (random.nextInt(CultivarServerConfig.BED_FERTILIZER_ODDS.get()) != 0) {
            return;
        }
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            Optional<Fertilizer> fert = CropBlockEntity.fertilizerFor(server, bed.inventory.getStackInSlot(FERTILIZER));
            if (fert.isPresent()) {
                if (crop.fertilize(server, random, fert.get())) {
                    bed.inventory.extractItem(FERTILIZER, 1, false);
                    server.levelEvent(1505, above, 0);
                }
                return;
            }
        }
    }

    private boolean nextToBreeder(ServerLevel server) {
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (server.getBlockEntity(worldPosition.relative(d)) instanceof PlanterBedBlockEntity n && n.breeding) {
                return true;
            }
        }
        return false;
    }

    /** A seed slot in the plot: bed + slot index. */
    private record Slot(PlanterBedBlockEntity bed, int index) {
        ItemStack stack() {
            return bed.inventory.getStackInSlot(index);
        }
    }

    /**
     * Best seed in the plot's stock: overall when {@code than} is null, else of the same species and strictly
     * better than {@code than}.
     */
    private Optional<Slot> bestSeed(@Nullable Genome than) {
        Slot best = null;
        double bestScore = than == null ? Double.NEGATIVE_INFINITY : SeedQuality.score(than);
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = SEEDS; i < SEEDS + SEED_SLOTS; i++) {
                ItemStack s = bed.inventory.getStackInSlot(i);
                Optional<Genome> g = TrellisBlock.genomeFor(s, bed.level);
                if (g.isEmpty() || (than != null && !g.get().species().is(than.species()))) {
                    continue;
                }
                double score = SeedQuality.score(g.get());
                if (score > bestScore) {
                    bestScore = score;
                    best = new Slot(bed, i);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private void plantFrom(CropBlockEntity crop, Slot slot) {
        TrellisBlock.genomeFor(slot.stack(), slot.bed().level).ifPresent(g -> {
            if (crop.plant(g, slot.stack())) {
                slot.bed().inventory.extractItem(slot.index(), 1, false);
            }
        });
    }

    private boolean stockHasRoom() {
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = SEEDS; i < SEEDS + SEED_SLOTS; i++) {
                if (bed.inventory.getStackInSlot(i).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds a seed to the plot's stock: stacks with an identical genome, else takes a free slot, else replaces the
     * worst stocked seed if it's better (the loser goes to the outputs).
     */
    void offerSeed(ItemStack seed) {
        ItemStack rest = seed;
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = SEEDS; i < SEEDS + SEED_SLOTS && !rest.isEmpty(); i++) {
                if (!bed.inventory.getStackInSlot(i).isEmpty()) {
                    rest = bed.inventory.insertItem(i, rest, false);
                }
            }
        }
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = SEEDS; i < SEEDS + SEED_SLOTS && !rest.isEmpty(); i++) {
                rest = bed.inventory.insertItem(i, rest, false);
            }
        }
        if (rest.isEmpty() || level == null) {
            return;
        }
        Genome incoming = TrellisBlock.genomeFor(rest, level).orElse(null);
        Slot worst = null;
        double worstScore = incoming == null ? Double.NEGATIVE_INFINITY : SeedQuality.score(incoming);
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = SEEDS; i < SEEDS + SEED_SLOTS; i++) {
                Optional<Genome> g = TrellisBlock.genomeFor(bed.inventory.getStackInSlot(i), level);
                if (g.isPresent() && SeedQuality.score(g.get()) < worstScore) {
                    worstScore = SeedQuality.score(g.get());
                    worst = new Slot(bed, i);
                }
            }
        }
        if (worst != null) {
            ItemStack loser = worst.stack().copy();
            worst.bed().inventory.setStackInSlot(worst.index(), rest);
            rest = loser;
        }
        if (level instanceof ServerLevel server) {
            output(server, worldPosition.above(), rest);
        }
    }

    private void output(ServerLevel server, BlockPos at, ItemStack stack) {
        ItemStack rest = insertOutput(stack);
        if (!rest.isEmpty()) {
            Block.popResource(server, at, rest);
        }
    }

    private boolean hasOutputRoom() {
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = OUT_START; i < SLOTS; i++) {
                if (bed.inventory.getStackInSlot(i).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private ItemStack insertOutput(ItemStack stack) {
        ItemStack rest = stack;
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = OUT_START; i < SLOTS && !rest.isEmpty(); i++) {
                rest = bed.inventory.insertItem(i, rest, false);
            }
        }
        return rest;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 0.5, worldPosition.getZ(), inventory.getStackInSlot(i));
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        if (soil != null) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 0.5, worldPosition.getZ(), new ItemStack(soil.getBlock()));
        }
    }

    // ---------------------------------------------------------------- persistence / sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Breeding", breeding);
        if (soil != null) {
            tag.put("Soil", NbtUtils.writeBlockState(soil));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        breeding = tag.getBoolean("Breeding");
        readClient(tag, registries);
    }

    /** Returns whether anything that affects rendering (soil, water presence) changed. */
    private boolean readClient(CompoundTag tag, HolderLookup.Provider registries) {
        BlockState oldSoil = soil;
        boolean oldWater = syncedWater;
        soil = tag.contains("Soil") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Soil")) : null;
        if (tag.contains("Water")) {
            syncedWater = tag.getBoolean("Water");
        }
        if (level != null && level.isClientSide()) {
            if (tag.contains("Inventory")) {
                inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
            }
            if (tag.contains("Tank")) {
                tank.readFromNBT(registries, tag.getCompound("Tank"));
            }
            breeding = tag.getBoolean("Breeding");
        }
        return !java.util.Objects.equals(oldSoil, soil) || oldWater != syncedWater;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (soil != null) {
            tag.put("Soil", NbtUtils.writeBlockState(soil));
        }
        tag.putBoolean("Water", !tank.isEmpty());
        // For goggles: stock and water are shown client-side.
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Breeding", breeding);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (readClient(tag, registries)) {
            rerender();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (readClient(pkt.getTag(), registries)) {
            rerender(); // only soil/water change the look; stock updates don't rebuild the chunk
        }
    }

    private void rerender() {
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            // The crop above reads this bed's soil and water.
            level.sendBlockUpdated(worldPosition.above(), level.getBlockState(worldPosition.above()), level.getBlockState(worldPosition.above()), Block.UPDATE_IMMEDIATE);
        }
    }

    /** One line for Display Links: best stocked seed and output fullness. */
    public Component statusLine() {
        Genome best = null;
        int used = 0, outs = 0;
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = SEEDS; i < SEEDS + SEED_SLOTS; i++) {
                Genome g = level == null ? null : TrellisBlock.genomeFor(bed.inventory.getStackInSlot(i), level).orElse(null);
                if (g != null && (best == null || SeedQuality.score(g) > SeedQuality.score(best))) {
                    best = g;
                }
            }
            for (int i = OUT_START; i < SLOTS; i++) {
                outs++;
                used += bed.inventory.getStackInSlot(i).isEmpty() ? 0 : 1;
            }
        }
        return best == null ? Component.translatable("cultivar.display.plot.empty", used, outs)
                : Component.translatable("cultivar.display.plot", CultivarSeedItem.plantName(best),
                String.format("%.0f", SeedQuality.score(best)), used, outs);
    }

    /** Plot-wide comparator: how full the plot's outputs are. */
    public int plotComparator() {
        int used = 0, total = 0;
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            for (int i = OUT_START; i < SLOTS; i++) {
                ItemStack s = bed.inventory.getStackInSlot(i);
                total++;
                if (!s.isEmpty()) {
                    used++;
                }
            }
        }
        return total == 0 ? 0 : (int) Math.ceil(used * 15.0 / total);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        List<PlanterBedBlockEntity> plot = cachedNetwork();
        int water = 0, seeds = 0, fert = 0, used = 0, outs = 0, breeders = 0;
        Genome best = null;
        for (PlanterBedBlockEntity bed : plot) {
            water += bed.tank.getFluidAmount();
            breeders += bed.breeding ? 1 : 0;
            fert += bed.inventory.getStackInSlot(FERTILIZER).getCount();
            for (int i = SEEDS; i < SEEDS + SEED_SLOTS; i++) {
                ItemStack s = bed.inventory.getStackInSlot(i);
                seeds += s.getCount();
                Genome g = level == null ? null : TrellisBlock.genomeFor(s, level).orElse(null);
                if (g != null && (best == null || SeedQuality.score(g) > SeedQuality.score(best))) {
                    best = g;
                }
            }
            for (int i = OUT_START; i < SLOTS; i++) {
                outs++;
                used += bed.inventory.getStackInSlot(i).isEmpty() ? 0 : 1;
            }
        }
        tooltip.add(Component.translatable("cultivar.bed.plot", plot.size(), breeders).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("cultivar.bed.soil", soil == null
                ? Component.translatable("cultivar.bed.no_soil") : soil.getBlock().getName()).withStyle(ChatFormatting.GRAY));
        // Water is synced per bed only as "has water"; the exact plot total is server-side, shown via Jade.
        tooltip.add(Component.translatable("cultivar.bed.water_total", water, plot.size() * CAPACITY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("cultivar.bed.stock", seeds, fert).withStyle(ChatFormatting.GRAY));
        if (best != null) {
            tooltip.add(Component.translatable("cultivar.bed.best", CultivarSeedItem.plantName(best),
                    String.format("%.0f", SeedQuality.score(best))).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("cultivar.bed.outputs", used, outs).withStyle(ChatFormatting.GRAY));
        return true;
    }

}
