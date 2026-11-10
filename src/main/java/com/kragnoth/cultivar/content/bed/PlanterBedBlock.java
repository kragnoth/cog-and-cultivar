package com.kragnoth.cultivar.content.bed;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.soil.SoilHost;
import com.kragnoth.cultivar.content.cloche.BellClocheItem;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Planter Bed. Right-click with: a soil block to fill it; a trellis to set one on top; a Bell Cloche to cover that
 * trellis; seeds or fertilizer to load it; a water bucket to fill it. Empty hand takes the outputs; sneak with an
 * empty hand to empty the soil. Pipes can also feed trellises and cloches, which the bed places itself; a redstone
 * signal strips the bed (cloche, then trellis, back into stock; plants banked as seeds). Pipes and funnels see the whole connected plot (see {@link BedNetwork}).
 */
public class PlanterBedBlock extends BaseEntityBlock implements SoilHost {
    public static final MapCodec<PlanterBedBlock> CODEC = simpleCodec(PlanterBedBlock::new);

    public PlanterBedBlock(Properties properties) {
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
        return new PlanterBedBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, PlanterBedModule.BED_BE.get(), (l, p, s, be) -> be.serverTick());
    }

    @Override
    public @Nullable BlockState hostedSoil(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed ? bed.soil() : null;
    }

    @Override
    public boolean suppliesWater(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed && bed.hasWater();
    }

    private static boolean isSoil(Level level, BlockState state) {
        return level.registryAccess().registryOrThrow(CultivarRegistries.SOIL).stream()
                .anyMatch(s -> s.blocks().contains(state.getBlockHolder()));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockPos above = pos.above();
        // Trellis: set it on top (stacking further trellises is done on the trellis itself).
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof TrellisBlock trellis) {
            if (!level.getBlockState(above).canBeReplaced()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide()) {
                level.setBlock(above, trellis.defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, above, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1f, 1f);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Cloche: cover the trellis above, as if clicked on it.
        if (stack.getItem() instanceof BellClocheItem && level.getBlockState(above).getBlock() instanceof TrellisBlock) {
            BlockState t = level.getBlockState(above);
            if (t.getValue(TrellisBlock.CLOCHE) || t.getValue(TrellisBlock.CROSS) || TrellisBlock.hasSegmentAbove(level, above)) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                level.setBlock(above, t.setValue(TrellisBlock.CLOCHE, true), Block.UPDATE_ALL);
                level.playSound(null, above, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1f, 1f);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Soil block: fill the bed.
        if (bed.soil() == null && stack.getItem() instanceof BlockItem bi && isSoil(level, bi.getBlock().defaultBlockState())) {
            if (!level.isClientSide()) {
                bed.setSoil(bi.getBlock().defaultBlockState());
                level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1f, 1f);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Water bucket.
        if (stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide() && bed.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                    IFluidHandler.FluidAction.SIMULATE) == 1000) {
                bed.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                }
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Seeds / fertilizer into their slots.
        for (int slot : new int[]{0, 1, 2, 3, PlanterBedBlockEntity.FERTILIZER}) {
            if (bed.inventory.isItemValid(slot, stack)) {
                if (!level.isClientSide()) {
                    player.setItemInHand(hand, bed.inventory.insertItem(slot, stack, false));
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            if (bed.soil() != null) {
                player.getInventory().placeItemBackInInventory(new ItemStack(bed.soil().getBlock()));
                bed.setSoil(null);
            }
            return InteractionResult.SUCCESS;
        }
        for (int i = PlanterBedBlockEntity.OUT_START; i < PlanterBedBlockEntity.SLOTS; i++) {
            ItemStack out = bed.inventory.extractItem(i, 64, false);
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed ? bed.plotComparator() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed) {
            bed.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
