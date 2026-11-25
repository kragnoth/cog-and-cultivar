package com.kragnoth.cultivar.content.assay;

import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Assayer (spec §3.12): a horizontal Mechanical Crafter whose top holds farmland. Its cogwheel meshes with neighbouring
 * Assayers and cogwheels like a crafter's, so a row shares one drive; a shaft from below also works. Touching Assayers
 * form one unit with shared input and output ({@link AssayingEngineBlockEntity#unit()}).
 *
 * <p>Hands, as on every Cultivar machine: right-click with a seed, cutting or Ledger to put it in (seeds go to the
 * least-loaded member); shift + right-click with an empty hand to take finished seeds out, then queued seeds, then the
 * Ledger.
 */
public class AssayingEngineBlock extends KineticBlock implements IBE<AssayingEngineBlockEntity>, ICogWheel {
    private final AssayTier tier;

    public AssayingEngineBlock(Properties properties, AssayTier tier) {
        super(properties);
        this.tier = tier;
    }

    public AssayTier tier() {
        return tier;
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
        if (!AssayingEngineBlockEntity.isAssayable(stack) && !(stack.getItem() instanceof LedgerItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return onBlockEntityUseItemOn(level, pos, be -> {
            ItemStack rest = be.insertIntoUnit(stack.copy());
            if (rest.getCount() == stack.getCount()) {
                return ItemInteractionResult.CONSUME_PARTIAL;
            }
            player.setItemInHand(hand, rest);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
            return ItemInteractionResult.SUCCESS;
        });
    }

    /** Shift + empty hand: finished seeds from the whole unit, then queued seeds, then the Ledger. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, pos, be -> {
            var taken = be.takeFromUnit();
            taken.forEach(s -> player.getInventory().placeItemBackInInventory(s));
            if (!taken.isEmpty()) {
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1f);
            }
            return taken.isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        });
    }

    /** Comparator: how full the unit's finished-seed outputs are (0-15). */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AssayingEngineBlockEntity be ? be.comparatorSignal() : 0;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            withBlockEntityDo(level, pos, AssayingEngineBlockEntity::dropContents);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
