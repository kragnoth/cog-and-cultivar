package com.kragnoth.cultivar.mixin;

import com.kragnoth.cultivar.api.environment.CultivarEnvironment;
import com.kragnoth.cultivar.api.environment.MoistureSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets registered {@link MoistureSource}s keep farmland moist without a water block in reach. */
@Mixin(FarmBlock.class)
public abstract class FarmBlockMoistureMixin {
    @Inject(method = "isNearWater", at = @At("HEAD"), cancellable = true)
    private static void cultivar$moistureSources(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        for (MoistureSource source : CultivarEnvironment.moistureSources()) {
            if (source.keepsMoist(level, pos)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
