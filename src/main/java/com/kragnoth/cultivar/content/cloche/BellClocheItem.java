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

/**
 * Fits over a (non-grafting) trellis. Sneak + empty hand on the trellis lifts it off again. One class for every kind
 * ({@link ClocheType}); Planter Beds take any of them in their cloche slot.
 */
public class BellClocheItem extends Item {
    private final ClocheType type;

    public BellClocheItem(ClocheType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ClocheType type() {
        return type;
    }

    /** The trellis state with this cloche fitted. */
    public BlockState fit(BlockState trellis) {
        return trellis.setValue(TrellisBlock.CLOCHE, true).setValue(TrellisBlock.CLOCHE_TYPE, type);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockState state = level.getBlockState(ctx.getClickedPos());
        if (!(state.getBlock() instanceof TrellisBlock) || state.getValue(TrellisBlock.CLOCHE) || state.getValue(TrellisBlock.CROSS)
                || TrellisBlock.isSegment(level, ctx.getClickedPos()) || TrellisBlock.hasSegmentAbove(level, ctx.getClickedPos())) {
            return InteractionResult.PASS; // cloches fit single trellises only
        }
        if (!level.isClientSide()) {
            level.setBlock(ctx.getClickedPos(), fit(state), Block.UPDATE_ALL);
            level.playSound(null, ctx.getClickedPos(), SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
            if (level.getBlockEntity(ctx.getClickedPos()) instanceof CropBlockEntity crop) {
                crop.invalidateInfluence();
            }
            ctx.getItemInHand().consume(1, ctx.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
