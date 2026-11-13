package com.kragnoth.cultivar.content.bed;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import com.kragnoth.cultivar.core.plot.PlotTracker;
import java.util.ArrayList;
import java.util.List;

/**
 * Touching Planter Beds (sideways, same height) form one plot: every bed's capabilities see the whole plot's
 * seeds, fertilizer, outputs and water. Each bed still stores its own share (so breaking one only drops its own).
 * The member list is a flood fill, recomputed at most once per tick and capped at {@link #MAX_BEDS}.
 */
public final class BedNetwork {
    public static final int MAX_BEDS = 64;

    private BedNetwork() {}

    public static List<PlanterBedBlockEntity> members(PlanterBedBlockEntity origin) {
        return PlotTracker.flood(origin, PlanterBedBlockEntity.class, MAX_BEDS);
    }

    /** Item view over the whole plot: slots are each bed's automation slots, end to end. */
    public static IItemHandler items(PlanterBedBlockEntity origin) {
        return new PlotItemHandler(() -> origin.cachedNetwork().stream().map(b -> (IItemHandler) b.automation).toList());
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
            /** Balanced ("water-filling"): raises the lowest tanks to a common level, so one pipe waters the plot evenly. */
            public int fill(FluidStack resource, FluidAction action) {
                if (!origin.tank.isFluidValid(resource)) {
                    return 0;
                }
                List<PlanterBedBlockEntity> beds = new ArrayList<>(origin.cachedNetwork());
                beds.sort(java.util.Comparator.comparingInt(b -> b.tank.getFluidAmount()));
                int n = beds.size(), cap = PlanterBedBlockEntity.CAPACITY, remaining = resource.getAmount();
                int[] lv = new int[n];
                for (int i = 0; i < n; i++) {
                    lv[i] = beds.get(i).tank.getFluidAmount();
                }
                // Find the common level t the lowest k tanks can be raised to.
                long sum = 0;
                int k = 0;
                double t = 0;
                for (k = 1; k <= n; k++) {
                    sum += lv[k - 1];
                    t = Math.min(cap, (remaining + sum) / (double) k);
                    if (k == n || t <= lv[k]) {
                        break;
                    }
                }
                k = Math.min(k, n);
                int filled = 0;
                int target = (int) Math.floor(t);
                for (int i = 0; i < k && filled < remaining; i++) {
                    int add = Math.min(remaining - filled, Math.max(0, target - lv[i]));
                    if (add > 0) {
                        filled += beds.get(i).tank.fill(resource.copyWithAmount(add), action);
                    }
                }
                // Rounding leftovers, 1 mB at a time, lowest first.
                for (int i = 0; i < n && filled < remaining; i++) {
                    filled += beds.get(i).tank.fill(resource.copyWithAmount(Math.min(remaining - filled, 1)), action);
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
