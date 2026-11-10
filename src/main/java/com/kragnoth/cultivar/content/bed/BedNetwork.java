package com.kragnoth.cultivar.content.bed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Touching Planter Beds (sideways, same height) form one plot: every bed's capabilities see the whole plot's
 * seeds, fertilizer, outputs and water. Each bed still stores its own share (so breaking one only drops its own).
 * The member list is a flood fill, recomputed at most once per tick and capped at {@link #MAX_BEDS}.
 */
public final class BedNetwork {
    public static final int MAX_BEDS = 64;

    private BedNetwork() {}

    public static List<PlanterBedBlockEntity> members(PlanterBedBlockEntity origin) {
        Level level = origin.getLevel();
        if (level == null) {
            return List.of(origin);
        }
        List<PlanterBedBlockEntity> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.getBlockPos());
        seen.add(origin.getBlockPos());
        while (!queue.isEmpty() && out.size() < MAX_BEDS) {
            BlockPos p = queue.poll();
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof PlanterBedBlockEntity bed)) {
                continue;
            }
            out.add(bed);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return out;
    }

    /** Item view over the whole plot: slots are each bed's automation slots, end to end. */
    public static IItemHandler items(PlanterBedBlockEntity origin) {
        return new IItemHandler() {
            private List<PlanterBedBlockEntity> beds() {
                return origin.cachedNetwork();
            }

            private IItemHandler at(int slot, int[] local) {
                for (PlanterBedBlockEntity bed : beds()) {
                    int n = bed.automation.getSlots();
                    if (slot < n) {
                        local[0] = slot;
                        return bed.automation;
                    }
                    slot -= n;
                }
                return null;
            }

            @Override
            public int getSlots() {
                return beds().size() * PlanterBedBlockEntity.SLOTS;
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
        };
    }

    /** Water view over the whole plot: fills each bed's tank in turn; never drains. */
    public static IFluidHandler fluids(PlanterBedBlockEntity origin) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return origin.cachedNetwork().size();
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                var beds = origin.cachedNetwork();
                return tank < beds.size() ? beds.get(tank).tank.getFluid() : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return PlanterBedBlockEntity.CAPACITY;
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                return origin.tank.isFluidValid(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                int filled = 0;
                for (PlanterBedBlockEntity bed : origin.cachedNetwork()) {
                    if (filled >= resource.getAmount()) {
                        break;
                    }
                    filled += bed.tank.fill(resource.copyWithAmount(resource.getAmount() - filled), action);
                }
                return filled;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }
}
