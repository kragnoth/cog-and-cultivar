package com.kragnoth.cultivar.content.bed;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.soil.SoilHost;
import com.kragnoth.cultivar.content.cloche.BellClocheItem;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * Planter Bed. Right-click with: a soil block to fill it or swap its soil; a trellis to set one on top; a Bell Cloche to cover that
 * trellis; seeds or fertilizer to load it; a water bucket to fill it. Sneak with an empty hand to take the plot's
 * outputs, then this bed's stock, then its soil. Pipes can also feed trellises and cloches, which the bed places
 * itself. Pipes pull only crop outputs, unless the plot is in dismantle mode (toggled with a wrench): then it strips
 * down (cloche, then trellis, back into stock; plants banked as seeds) and every slot can be pulled. Redstone pauses
 * the plot. Pipes and funnels see the whole connected plot (see {@link BedNetwork}).
 */
public class PlanterBedBlock extends com.simibubi.create.content.kinetics.base.KineticBlock
        implements com.simibubi.create.foundation.block.IBE<PlanterBedBlockEntity>, com.simibubi.create.content.kinetics.simpleRelays.ICogWheel, SoilHost {

    public PlanterBedBlock(Properties properties) {
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
    public Class<PlanterBedBlockEntity> getBlockEntityClass() {
        return PlanterBedBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PlanterBedBlockEntity> getBlockEntityType() {
        return PlanterBedModule.BED_BE.get();
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

    /** A tended bed: weeds scaled by {@code planter_bed.weedMultiplier} (default 0, none). */
    @Override
    public double weedMultiplier(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return com.kragnoth.cultivar.core.config.CultivarServerConfig.SPEC.isLoaded()
                ? com.kragnoth.cultivar.core.config.CultivarServerConfig.BED_WEED_MULTIPLIER.get() : 0.0;
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
        if (stack.getItem() instanceof BellClocheItem cloche && level.getBlockState(above).getBlock() instanceof TrellisBlock) {
            BlockState t = level.getBlockState(above);
            if (t.getValue(TrellisBlock.CLOCHE) || t.getValue(TrellisBlock.CROSS) || TrellisBlock.hasSegmentAbove(level, above)) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                level.setBlock(above, cloche.fit(t), Block.UPDATE_ALL);
                level.playSound(null, above, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1f, 1f);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Soil block: fill the bed, or swap its soil (the old soil goes back to the player). Never falls through to
        // vanilla placement, so a soil block can't be set down on top of the bed by accident (sneak still does that).
        if (stack.getItem() instanceof BlockItem bi && isSoil(level, bi.getBlock().defaultBlockState())) {
            BlockState current = bed.soil();
            BlockState incoming = bi.getBlock().defaultBlockState();
            if (current != null && current.is(incoming.getBlock())) {
                return ItemInteractionResult.FAIL; // already this soil
            }
            if (!level.isClientSide()) {
                bed.setSoil(incoming);
                level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1f, 1f);
                stack.consume(1, player);
                if (current != null && !player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(current.getBlock()));
                }
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
        // Seeds / fertilizer: into this bed first, then anywhere in the plot (the plot shares its stock).
        for (int slot : new int[]{0, 1, 2, 3, PlanterBedBlockEntity.FERTILIZER}) {
            if (bed.inventory.isItemValid(slot, stack)) {
                if (!level.isClientSide()) {
                    ItemStack rest = bed.inventory.insertItem(slot, stack, false);
                    for (PlanterBedBlockEntity other : bed.cachedNetwork()) {
                        for (int s : new int[]{0, 1, 2, 3, PlanterBedBlockEntity.FERTILIZER}) {
                            if (!rest.isEmpty() && other.inventory.isItemValid(s, rest)) {
                                rest = other.inventory.insertItem(s, rest, false);
                            }
                        }
                    }
                    player.setItemInHand(hand, rest);
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
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
        // Machine rule: shift + empty hand takes items out: the plot's outputs, then this bed's stock, then its soil.
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean took = false;
        for (PlanterBedBlockEntity member : bed.cachedNetwork()) {
            for (int i = PlanterBedBlockEntity.OUT_START; i < PlanterBedBlockEntity.SLOTS; i++) {
                ItemStack out = member.inventory.extractItem(i, 64, false);
                if (!out.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(out);
                    took = true;
                }
            }
        }
        if (!took) {
            for (int i = 0; i < PlanterBedBlockEntity.OUT_START; i++) {
                ItemStack in = bed.inventory.extractItem(i, 64, false);
                if (!in.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(in);
                    took = true;
                }
            }
        }
        if (!took && bed.soil() != null) { // last of all, the soil itself
            player.getInventory().placeItemBackInInventory(new ItemStack(bed.soil().getBlock()));
            bed.setSoil(null);
            took = true;
        }
        if (took) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1f);
        }
        return took ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed) {
            bed.invalidatePower();
        }
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed) {
            bed.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /**
     * Beds don't rotate: the wrench flips the whole plot between growing and dismantling (strip trellises and cloches
     * back into stock, bank plants as seeds, and let pipes empty every slot). Sneak-wrench picks the bed up.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PlanterBedBlockEntity bed)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            boolean dismantling = bed.toggleDismantling();
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable(dismantling ? "cultivar.bed.mode.dismantle"
                        : "cultivar.bed.mode.grow"), true);
            }
            level.playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1f, dismantling ? 0.8f : 1.2f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
