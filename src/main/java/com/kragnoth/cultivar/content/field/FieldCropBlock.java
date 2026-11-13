package com.kragnoth.cultivar.content.field;

import com.kragnoth.cultivar.api.crop.PlantHolder;
import com.kragnoth.cultivar.api.plant.Fertilizer;
import com.kragnoth.cultivar.content.core.lookup.FertilizerLookup;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A Cultivar plant on plain soil (no trellis). Behaves like a vanilla crop: walk-through, breaks instantly, pops off
 * if its soil goes, drops seed + produce when broken ripe. Right-click a ripe one to harvest and replant in place;
 * fertilizers (bone meal included, also from dispensers) push growth. As a {@link PlantHolder} it works with Create
 * harvesters, conditioners and Sprinklers with no extra code.
 */
public class FieldCropBlock extends BaseEntityBlock implements PlantHolder, BonemealableBlock {
    public static final MapCodec<FieldCropBlock> CODEC = simpleCodec(FieldCropBlock::new);
    /** Growth mirrored to vanilla's 0–7 crop age, for other mods and vanilla logic that read it. */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    /** An upper segment of a tall plant (cane, bamboo...): no block entity, the plant lives in the base below. */
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    /** Tallest field stack: a base plus three segments (same as a trellis stack). */
    public static final int MAX_STACK = 4;
    private static final VoxelShape[] SHAPES = new VoxelShape[8];

    static {
        for (int i = 0; i < 8; i++) {
            SHAPES[i] = Block.box(0, 0, 0, 16, 2 + i * 2, 16);
        }
    }

    public FieldCropBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(UPPER, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, UPPER);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(UPPER) ? SHAPES[7] : SHAPES[state.getValue(AGE)];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(UPPER) ? null : new FieldCropBlockEntity(pos, state);
    }

    /** The base of the field stack {@code pos} belongs to (itself for a single-block plant). */
    public static BlockPos basePos(BlockGetter level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 1; i < MAX_STACK; i++) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof FieldCropBlock) || !s.getValue(UPPER)) {
                break;
            }
            p = p.below();
        }
        return p;
    }

    public static Optional<FieldCropBlockEntity> baseEntity(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(basePos(level, pos)) instanceof FieldCropBlockEntity crop ? Optional.of(crop) : Optional.empty();
    }

    /** Stage mapped onto 0–7 (7 exactly when mature). */
    public static int ageFor(PlantCore plant) {
        if (!plant.hasPlant()) {
            return 0;
        }
        int mature = plant.species().matureStage();
        return mature <= 0 ? 7 : Math.min(7, plant.stage() * 7 / mature);
    }

    // ---------------------------------------------------------------- survival

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(UPPER)) {
            return level.getBlockState(pos.below()).getBlock() instanceof FieldCropBlock;
        }
        return level instanceof Level l ? FieldPlanting.isFieldSoil(l, pos.below()) : true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Returning air here breaks the block with drops (seed, plus a harvest if ripe), like a crop losing its farmland.
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    // ---------------------------------------------------------------- ticking

    /** Breaking an upper segment cuts the plant down to below it (yield per segment cut, like cane). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide() && state.getValue(UPPER) && !newState.is(this)) {
            baseEntity(level, pos.below()).ifPresent(base -> base.cutAt(pos.getY() - base.getBlockPos().getY(),
                    drop -> popResource(level, pos, drop)));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(UPPER);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FieldCropBlockEntity crop) {
            crop.randomTick(level, random);
        }
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(baseEntity(level, pos).orElse(null) instanceof FieldCropBlockEntity crop)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FieldTakeover.isTrellis(stack)) {
            return FieldTakeover.intoTrellis(level, crop, stack, player);
        }
        // Cultivar tools (shears, spade, satchel, cuttings) act through their own Item#useOn, not right-click harvest.
        if (stack.is(com.kragnoth.cultivar.content.crop.TrellisBlock.CROP_TOOLS)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        Optional<Fertilizer> fertilizer = FertilizerLookup.forItem(level, stack);
        if (fertilizer.isPresent()) {
            if (crop.isMature()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // let right-click harvest run
            }
            if (!level.isClientSide()) {
                if (!crop.plant().fertilize(fertilizer.get(), false)) {
                    return ItemInteractionResult.FAIL;
                }
                stack.consume(1, player);
                level.levelEvent(1505, crop.getBlockPos(), 15); // bone meal particles
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Right-click a ripe plant: harvest by Gain and drop back to its after-harvest stage (replanted in place). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(baseEntity(level, pos).orElse(null) instanceof FieldCropBlockEntity crop) || !crop.isMature()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            harvestInPlace(level, pos, drop -> popResource(level, pos, drop));
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ---------------------------------------------------------------- PlantHolder

    @Override
    public Optional<FieldCropBlockEntity> crop(BlockGetter level, BlockPos pos) {
        return baseEntity(level, pos).filter(crop -> crop.plant().hasPlant());
    }

    @Override
    public BlockPos soilPos(BlockGetter level, BlockPos pos) {
        return basePos(level, pos).below();
    }

    @Override
    public boolean harvestInPlace(Level level, BlockPos pos, Consumer<ItemStack> drops) {
        return baseEntity(level, pos).map(crop -> crop.plant().harvest(drops)).orElse(false);
    }

    // ---------------------------------------------------------------- bone meal (dispensers, other mods)

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return baseEntity(level, pos).map(crop -> crop.plant().hasPlant() && !crop.isMature()).orElse(false);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (baseEntity(level, pos).orElse(null) instanceof FieldCropBlockEntity crop) {
            FertilizerLookup.forItem(level, new ItemStack(Items.BONE_MEAL)).ifPresent(f -> crop.plant().fertilize(f, false));
        }
    }

    // ---------------------------------------------------------------- drops & redstone

    /** Seed, plus a full harvest when ripe (like breaking ripe wheat). */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FieldCropBlockEntity crop) {
            crop.plant().collectBreakDrops(drops::add);
        }
        return drops;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return baseEntity(level, pos).map(crop -> crop.plant().comparatorSignal()).orElse(0);
    }
}
