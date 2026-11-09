package com.kragnoth.cultivar.mixin;

import com.kragnoth.cultivar.compat.create.CultivarHarvesting;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create 6 binds the harvester's MovementBehaviour in AllBlocks and SimpleRegistry
 * refuses re-registration, so Cultivar crops are handled by injecting here instead.
 */
@Mixin(value = HarvesterMovementBehaviour.class, remap = false)
public abstract class HarvesterMovementBehaviourMixin {

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true)
    private void cultivar$harvestCultivarCrops(MovementContext context, BlockPos pos, CallbackInfo ci) {
        HarvesterMovementBehaviour self = (HarvesterMovementBehaviour) (Object) this;
        if (!context.world.isClientSide() && CultivarHarvesting.tryHarvest(context, pos, self::dropItem)) {
            ci.cancel();
        }
    }
}
