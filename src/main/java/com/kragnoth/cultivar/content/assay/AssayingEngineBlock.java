package com.kragnoth.cultivar.content.assay;

import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Assaying Engine (spec §3.12, Phase 2c): a shaft from below spins the drum; seeds go in, come out assayed.
 * Paper adds an Assay Card; a ledger in its slot records every species that passes through.
 */
public class AssayingEngineBlock extends KineticBlock implements IBE<AssayingEngineBlockEntity> {
    public AssayingEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Class<AssayingEngineBlockEntity> getBlockEntityClass() {
        return AssayingEngineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AssayingEngineBlockEntity> getBlockEntityType() {
        return AssayModule.ENGINE_BE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        int slot = stack.has(CoreModule.GENOME.get()) ? AssayingEngineBlockEntity.SEED_IN
                : stack.is(Items.PAPER) ? AssayingEngineBlockEntity.PAPER
                : stack.getItem() instanceof LedgerItem ? AssayingEngineBlockEntity.LEDGER : -1;
        if (slot < 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return onBlockEntityUseItemOn(level, pos, be -> {
            ItemStack rest = be.inventory.insertItem(slot, stack, false);
            if (rest.getCount() == stack.getCount()) {
                return ItemInteractionResult.CONSUME_PARTIAL;
            }
            player.setItemInHand(hand, rest);
            return ItemInteractionResult.SUCCESS;
        });
    }

    /** Empty hand: take outputs first, then (sneaking) the ledger, then any unprocessed input. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, pos, be -> {
            int[] order = player.isShiftKeyDown()
                    ? new int[]{AssayingEngineBlockEntity.LEDGER}
                    : new int[]{AssayingEngineBlockEntity.SEED_OUT, AssayingEngineBlockEntity.CARD_OUT,
                    AssayingEngineBlockEntity.SEED_IN, AssayingEngineBlockEntity.PAPER};
            for (int slot : order) {
                ItemStack taken = be.inventory.extractItem(slot, 64, false);
                if (!taken.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(taken);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            withBlockEntityDo(level, pos, AssayingEngineBlockEntity::dropContents);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
