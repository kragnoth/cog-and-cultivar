package com.kragnoth.cultivar.content.assay;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Work is server-side only: progress advances by |RPM|/32 per tick (100 = one seed), so 32 RPM takes 5 s and
 * 256 RPM well under a second. Clients get the inventory and progress for goggles via Create's sendData.
 */
public class AssayingEngineBlockEntity extends KineticBlockEntity {
    public static final int SEED_IN = 0, LEDGER = 1, SEED_OUT = 2;
    public static final float WORK = 100f;

    public final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case SEED_IN -> stack.has(CoreModule.GENOME.get())
                        && (stack.getItem() instanceof CultivarSeedItem || stack.getItem() instanceof com.kragnoth.cultivar.content.tools.CuttingItem);
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
        }
    };

    /** Automation view: inputs can be inserted, only outputs extracted. */
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

    private float progress;

    public AssayingEngineBlockEntity(BlockPos pos, BlockState state) {
        this(AssayModule.ENGINE_BE.get(), pos, state);
    }

    public AssayingEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        float before = progress;
        advance(Math.abs(getSpeed()));
        // Sync roughly once a second while working, and on every start/finish, for goggles.
        if ((int) (before / 20) != (int) (progress / 20) || (before == 0) != (progress == 0)) {
            sendData();
        }
    }

    /** Advances work by {@code rpm}; public so game tests can drive it without a kinetic network. */
    public void advance(float rpm) {
        if (rpm <= 0 || !canWork()) {
            progress = 0;
            return;
        }
        progress += rpm / 32f;
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
        ItemStack ledger = inventory.getStackInSlot(LEDGER);
        if (!ledger.isEmpty() && genome != null) {
            genome.species().unwrapKey().ifPresent(key -> LedgerItem.record(ledger, key));
            inventory.setStackInSlot(LEDGER, ledger);
        }
        if (level != null) {
            com.simibubi.create.AllSoundEvents.CLIPBOARD_CHECKMARK.playOnServer(level, worldPosition, 0.5f, 1f);
        }
        sendData();
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
        return true;
    }
}
