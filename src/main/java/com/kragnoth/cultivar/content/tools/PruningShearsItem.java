package com.kragnoth.cultivar.content.tools;

import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.context.UseOnContext;

/** Takes a mature plant's clip products (e.g. the flower rather than its dye) instead of a normal harvest. */
public class PruningShearsItem extends Item {
    public PruningShearsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(CropBlockEntity.resolve(level, ctx.getClickedPos()).orElse(null) instanceof CropBlockEntity crop)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean clipped = crop.clip(drop -> Block.popResource(level, ctx.getClickedPos(), drop));
        if (clipped) {
            level.playSound(null, ctx.getClickedPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1f, 1.2f);
            if (ctx.getPlayer() != null) {
                ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), LivingEntity.getSlotForHand(ctx.getHand()));
            }
        }
        return clipped ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
