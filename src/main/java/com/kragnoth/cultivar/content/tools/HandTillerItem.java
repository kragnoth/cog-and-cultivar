package com.kragnoth.cultivar.content.tools;

import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Blight tool working a 3×3 patch. Timber knocks each weed back one stage; iron removes it outright.
 * One durability per use regardless of how many crops it touched.
 */
public class HandTillerItem extends Item {
    private final boolean clearsOutright;

    public HandTillerItem(boolean clearsOutright, Properties properties) {
        super(properties);
        this.clearsOutright = clearsOutright;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos centre = ctx.getClickedPos();
        if (!(level.getBlockEntity(centre) instanceof CropBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean any = false;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            if (level.getBlockEntity(pos) instanceof CropBlockEntity crop && crop.hasWeed()) {
                BlockPos dropAt = pos.immutable();
                any |= clearsOutright
                        ? crop.clearWeeds(drop -> Block.popResource(level, dropAt, drop))
                        : crop.regressWeed(drop -> Block.popResource(level, dropAt, drop));
            }
        }
        if (any) {
            level.playSound(null, centre, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1f, 1f);
            if (ctx.getPlayer() != null) {
                ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), LivingEntity.getSlotForHand(ctx.getHand()));
            }
        }
        return any ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
