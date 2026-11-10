package com.kragnoth.cultivar.content.trial;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Trial Bed. Right-click with seeds or a Ledger to load it; empty hand takes the Ledger back. */
public class TrialBedBlock extends BaseEntityBlock {
    public static final MapCodec<TrialBedBlock> CODEC = simpleCodec(TrialBedBlock::new);

    public TrialBedBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrialBedBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, TrialModule.TRIAL_BED_BE.get(), (l, p, s, be) -> be.serverTick());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        for (int slot = 0; slot < TrialBedBlockEntity.SLOTS; slot++) {
            if (bed.inventory.isItemValid(slot, stack)) {
                if (!level.isClientSide()) {
                    ItemStack rest = bed.inventory.insertItem(slot, stack, false);
                    if (rest.getCount() == stack.getCount()) {
                        continue;
                    }
                    player.setItemInHand(hand, rest);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed) {
            ItemStack ledger = bed.inventory.extractItem(TrialBedBlockEntity.LEDGER, 1, false);
            if (!ledger.isEmpty()) {
                player.getInventory().placeItemBackInInventory(ledger);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed) {
            bed.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
