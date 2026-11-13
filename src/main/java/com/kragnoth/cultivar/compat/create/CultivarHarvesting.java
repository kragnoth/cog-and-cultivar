package com.kragnoth.cultivar.compat.create;

import com.kragnoth.cultivar.api.crop.PlantHolder;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * Bridge between Create's Mechanical Harvester and every Cultivar {@link PlantHolder}.
 * Called from {@code HarvesterMovementBehaviourMixin} before Create's own crop logic.
 */
public final class CultivarHarvesting {
    private CultivarHarvesting() {}

    /**
     * @return true if the position is a Cultivar crop (handled here, so Create must not break it);
     *         mature crops are harvested in place and keep their genome. Produce only: seeds come from the
     *         Seed Satchel (by hand or on a Deployer), so harvester lines don't flood storage.
     */
    public static boolean tryHarvest(MovementContext context, BlockPos pos, BiConsumer<MovementContext, ItemStack> drop) {
        if (!(context.world.getBlockState(pos).getBlock() instanceof PlantHolder holder)) {
            return false;
        }
        // Any plant holder (trellis segments resolve to their root; field crops next) harvests in place.
        holder.harvestInPlace(context.world, pos, stack -> drop.accept(context, stack));
        return true;
    }
}
