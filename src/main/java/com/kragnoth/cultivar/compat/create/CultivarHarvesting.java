package com.kragnoth.cultivar.compat.create;

import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * Bridge between Create's Mechanical Harvester and Cultivar crops.
 * Called from {@code HarvesterMovementBehaviourMixin} before Create's own crop logic.
 */
public final class CultivarHarvesting {
    private CultivarHarvesting() {}

    /**
     * @return true if the position is a Cultivar crop (handled here, so Create must not break it);
     *         mature crops are harvested in place and keep their genome.
     */
    public static boolean tryHarvest(MovementContext context, BlockPos pos, BiConsumer<MovementContext, ItemStack> drop) {
        if (!(context.world.getBlockEntity(pos) instanceof CropBlockEntity crop)) {
            return false;
        }
        crop.harvest(stack -> drop.accept(context, stack));
        return true;
    }
}
