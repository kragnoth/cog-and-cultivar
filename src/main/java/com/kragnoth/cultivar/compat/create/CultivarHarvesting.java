package com.kragnoth.cultivar.compat.create;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;

/**
 * Bridge between Create's Mechanical Harvester and Cultivar crops.
 * Called from {@code HarvesterMovementBehaviourMixin} before Create's own crop logic.
 */
public final class CultivarHarvesting {
    private CultivarHarvesting() {}

    /**
     * @return true if the position held a Cultivar crop and was handled here
     *         (Create's default harvesting must then be skipped).
     */
    public static boolean tryHarvest(MovementContext context, BlockPos pos) {
        // Phase 3: if the block is a Cultivar crop and mature, harvest through the crop API,
        // route drops via the behaviour's dropItem(context, stack), keep the genome,
        // and reset to the plant's after-harvest stage.
        return false;
    }
}
