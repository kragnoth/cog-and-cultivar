package com.kragnoth.cultivar.content.field;

import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A trellis used on a field crop takes the plant over (genome, growth, assay flag kept), turning a decorative
 * planting into one that can breed. A tall plant is trimmed to the single trellis until more are stacked on top.
 */
public final class FieldTakeover {
    private FieldTakeover() {}

    public static boolean isTrellis(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof TrellisBlock;
    }

    public static ItemInteractionResult intoTrellis(Level level, FieldCropBlockEntity field, ItemStack stack, Player player) {
        if (!field.plant().hasPlant()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockPos pos = field.getBlockPos();
        var genome = field.genome();
        int stage = field.stage();
        boolean assayed = field.plant().isAssayed();
        Block trellis = ((BlockItem) stack.getItem()).getBlock();
        level.setBlock(pos, trellis.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CropBlockEntity crop && crop.plant(genome, stage)) {
            crop.setAssayed(assayed);
            crop.clampToSpace();
        }
        stack.consume(1, player);
        level.playSound(null, pos, trellis.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
        return ItemInteractionResult.CONSUME;
    }
}
