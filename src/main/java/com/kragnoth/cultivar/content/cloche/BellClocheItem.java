package com.kragnoth.cultivar.content.cloche;

import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Fits over a (non-grafting) trellis. Sneak + empty hand on the trellis lifts it off again. */
public class BellClocheItem extends Item {
    public BellClocheItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockState state = level.getBlockState(ctx.getClickedPos());
        if (!(state.getBlock() instanceof TrellisBlock) || state.getValue(TrellisBlock.CLOCHE) || state.getValue(TrellisBlock.CROSS)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(ctx.getClickedPos(), state.setValue(TrellisBlock.CLOCHE, true), Block.UPDATE_ALL);
            level.playSound(null, ctx.getClickedPos(), SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
            if (level.getBlockEntity(ctx.getClickedPos()) instanceof CropBlockEntity crop) {
                crop.invalidateInfluence();
            }
            ctx.getItemInHand().consume(1, ctx.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
