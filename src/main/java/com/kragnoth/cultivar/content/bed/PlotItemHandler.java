package com.kragnoth.cultivar.content.bed;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/** One item handler over many: the members' slots end to end. Used for bed plots so one pipe serves the plot. */
public final class PlotItemHandler implements IItemHandler {
    private final Supplier<List<IItemHandler>> members;

    public PlotItemHandler(Supplier<List<IItemHandler>> members) {
        this.members = members;
    }

    private IItemHandler at(int slot, int[] local) {
        for (IItemHandler h : members.get()) {
            if (slot < h.getSlots()) {
                local[0] = slot;
                return h;
            }
            slot -= h.getSlots();
        }
        return null;
    }

    @Override
    public int getSlots() {
        int n = 0;
        for (IItemHandler h : members.get()) {
            n += h.getSlots();
        }
        return n;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        int[] l = new int[1];
        IItemHandler h = at(slot, l);
        return h == null ? ItemStack.EMPTY : h.getStackInSlot(l[0]);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int[] l = new int[1];
        IItemHandler h = at(slot, l);
        return h == null ? stack : h.insertItem(l[0], stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        int[] l = new int[1];
        IItemHandler h = at(slot, l);
        return h == null ? ItemStack.EMPTY : h.extractItem(l[0], amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int[] l = new int[1];
        IItemHandler h = at(slot, l);
        return h == null ? 0 : h.getSlotLimit(l[0]);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int[] l = new int[1];
        IItemHandler h = at(slot, l);
        return h != null && h.isItemValid(l[0], stack);
    }
}
