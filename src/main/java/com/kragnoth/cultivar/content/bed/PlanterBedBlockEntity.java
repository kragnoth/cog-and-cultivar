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
    public static final int SEEDS = 0, FERTILIZER = 1, OUT_START = 2, SLOTS = 11;
    public static final int CAPACITY = 4000;

    public final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (level == null) {
                return false;
            }
            return switch (slot) {
                case SEEDS -> TrellisBlock.genomeFor(stack, level).isPresent();
                case FERTILIZER -> CropBlockEntity.fertilizerFor(level, stack).isPresent();
                default -> true;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Automation: seeds and fertilizer go in, only outputs come out. */
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
            return slot >= OUT_START ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
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
            syncWaterIfChanged();
        }
    };

    private @Nullable BlockState soil;
    private boolean syncedWater;
    private int cooldown;
    private int ticksSinceDrain;
    private List<PlanterBedBlockEntity> network;
    private long networkTick = Long.MIN_VALUE;

    public PlanterBedBlockEntity(BlockPos pos, BlockState state) {
        super(PlanterBedModule.BED_BE.get(), pos, state);
    }

    public @Nullable BlockState soil() {
        return soil;
    }

    public void setSoil(@Nullable BlockState soil) {
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
            network = BedNetwork.members(this);
            networkTick = now;
        }
        return network;
    }

    // ---------------------------------------------------------------- work

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
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

    /** One work cycle; public so game tests can drive it. */
    public void work(ServerLevel server, boolean watered) {
        BlockPos above = worldPosition.above();
        if (!(server.getBlockEntity(above) instanceof CropBlockEntity crop) || crop.isSegment()) {
            return;
        }
        var random = server.random;
        if (!crop.hasPlant() && !crop.isCross()) {
            takeFromPlot(SEEDS, stack -> TrellisBlock.genomeFor(stack, server))
                    .ifPresent(g -> crop.plant(g));
            return;
        }
        if (crop.isMature()) {
            if (hasOutputRoom()) {
                List<ItemStack> drops = new ArrayList<>();
                crop.harvest(drops::add);
                for (ItemStack d : drops) {
                    ItemStack rest = insertOutput(d);
                    if (!rest.isEmpty()) {
                        Block.popResource(server, above, rest);
                    }
                }
            }
            return;
        }
        // Water: extra growth ticks (default ~4x vanilla on top of the crop's own random ticks).
        if (watered && random.nextInt(CultivarServerConfig.BED_GROWTH_ODDS.get()) == 0) {
            server.getBlockState(above).randomTick(server, above, random);
        }
        // Fertilizer: spent now and then, only when it does something.
        if (random.nextInt(CultivarServerConfig.BED_FERTILIZER_ODDS.get()) == 0) {
            for (PlanterBedBlockEntity bed : cachedNetwork()) {
                ItemStack f = bed.inventory.getStackInSlot(FERTILIZER);
                Optional<Fertilizer> fert = CropBlockEntity.fertilizerFor(server, f);
                if (fert.isPresent()) {
                    if (crop.fertilize(server, random, fert.get())) {
                        bed.inventory.extractItem(FERTILIZER, 1, false);
                        server.levelEvent(1505, above, 0);
                    }
                    break;
                }
            }
        }
    }

    private Optional<Genome> takeFromPlot(int slot, java.util.function.Function<ItemStack, Optional<Genome>> genome) {
        for (PlanterBedBlockEntity bed : cachedNetwork()) {
            ItemStack s = bed.inventory.getStackInSlot(slot);
            Optional<Genome> g = genome.apply(s);
            if (g.isPresent()) {
                bed.inventory.extractItem(slot, 1, false);
                return g;
            }
        }
        return Optional.empty();
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
        if (soil != null) {
            tag.put("Soil", NbtUtils.writeBlockState(soil));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        readClient(tag, registries);
    }

    private void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        soil = tag.contains("Soil") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Soil")) : null;
        if (tag.contains("Water")) {
            syncedWater = tag.getBoolean("Water");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (soil != null) {
            tag.put("Soil", NbtUtils.writeBlockState(soil));
        }
        tag.putBoolean("Water", !tank.isEmpty());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readClient(tag, registries);
        rerender();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        readClient(pkt.getTag(), registries);
        rerender();
    }

    private void rerender() {
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            // The crop above reads this bed's soil and water.
            level.sendBlockUpdated(worldPosition.above(), level.getBlockState(worldPosition.above()), level.getBlockState(worldPosition.above()), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("block.cultivar.planter_bed").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("cultivar.bed.soil", soil == null
                ? Component.translatable("cultivar.bed.no_soil") : soil.getBlock().getName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("cultivar.bed.water", hasWater()
                ? Component.translatable("cultivar.bed.yes") : Component.translatable("cultivar.bed.no")).withStyle(ChatFormatting.GRAY));
        return true;
    }
}
