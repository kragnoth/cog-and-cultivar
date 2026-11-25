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

/**
 * Trial Bed. Right-click with seeds or a Ledger to load the plot; sneak with an empty hand takes the Ledger back. It sets
 * up its own trellises. Needs rotation (cogwheel beside it or shaft below); redstone pauses the plot.
 */
public class TrialBedBlock extends com.simibubi.create.content.kinetics.base.KineticBlock
        implements com.simibubi.create.foundation.block.IBE<TrialBedBlockEntity>, com.simibubi.create.content.kinetics.simpleRelays.ICogWheel,
        com.kragnoth.cultivar.api.soil.SoilHost {

    public TrialBedBlock(Properties properties) {
        super(properties);
    }

    /** Powered like a Mechanical Crafter: its cogwheel meshes with neighbouring beds and cogwheels; a shaft from below also works. */
    @Override
    public net.minecraft.core.Direction.Axis getRotationAxis(BlockState state) {
        return net.minecraft.core.Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, net.minecraft.core.Direction face) {
        return face == net.minecraft.core.Direction.DOWN;
    }

    @Override
    public Class<TrialBedBlockEntity> getBlockEntityClass() {
        return TrialBedBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TrialBedBlockEntity> getBlockEntityType() {
        return TrialModule.TRIAL_BED_BE.get();
    }

    /** Acts as farmland (so crops find a soil) and is ideal: no requirements, weeds or mutation habitats. */
    @Override
    public @Nullable BlockState hostedSoil(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState();
    }

    @Override
    public boolean isIdeal(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Into this bed first, then anywhere in the plot (seeds are shared plot stock; one Ledger serves the plot).
        boolean valid = false;
        for (int slot = 0; slot < TrialBedBlockEntity.SLOTS; slot++) {
            valid |= bed.inventory.isItemValid(slot, stack);
        }
        if (!valid) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            ItemStack rest = stack;
            java.util.List<TrialBedBlockEntity> order = new java.util.ArrayList<>(bed.plot());
            order.remove(bed);
            order.addFirst(bed);
            for (TrialBedBlockEntity member : order) {
                for (int slot = 0; slot < TrialBedBlockEntity.SLOTS && !rest.isEmpty(); slot++) {
                    if (member.inventory.isItemValid(slot, rest)) {
                        rest = member.inventory.insertItem(slot, rest, false);
                    }
                }
            }
            player.setItemInHand(hand, rest);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Machine rule: shift + empty hand takes items out: the Ledger (the plot's output) first, then this bed's seeds.
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed) {
            for (TrialBedBlockEntity member : bed.plot()) {
                ItemStack ledger = member.inventory.extractItem(TrialBedBlockEntity.LEDGER, 1, false);
                if (!ledger.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(ledger);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1f);
                    return InteractionResult.SUCCESS;
                }
            }
            for (int slot = 0; slot < TrialBedBlockEntity.SEED_SLOTS; slot++) {
                ItemStack seeds = bed.inventory.extractItem(slot, 64, false);
                if (!seeds.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(seeds);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed ? bed.comparatorSignal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed) {
            bed.invalidatePower();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TrialBedBlockEntity bed) {
            bed.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Beds don't rotate; sneak-wrench picks them up (contents drop). */
    @Override
    public InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        return InteractionResult.PASS;
    }
}
