package com.kragnoth.cultivar.content.cloche;

import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bell Cloche: a glass jar set on top of a trellis. Its effect is a {@code cultivar:crop_influence}
 * datapack entry with radius 0 (see {@code bell_cloche.json}); this block only nudges the crop below to
 * refresh its cached influence when the cloche is placed or removed.
 */
public class BellClocheBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 10, 13),
            Block.box(4, 10, 4, 12, 12, 12),
            Block.box(7, 12, 7, 9, 14, 9));

    public BellClocheBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshBelow(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        refreshBelow(level, pos);
    }

    private static void refreshBelow(Level level, BlockPos pos) {
        if (!level.isClientSide() && level.getBlockEntity(pos.below()) instanceof CropBlockEntity crop) {
            crop.invalidateInfluence();
        }
    }
}
