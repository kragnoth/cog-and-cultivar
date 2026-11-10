package com.kragnoth.cultivar.content.crop;

import net.minecraft.world.item.BlockItem;
import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Trellis frame. Holds a crop ({@link CropBlockEntity}); with {@link #CROSS} set it is a grafting frame
 * that breeds from mature neighbours.
 *
 * <p>Interactions: trellis on an empty trellis = grafting frame; seed (cultivar seed or a plant's listed
 * vanilla seed) = plant; empty hand on mature = harvest; hoe on weeds = clear; sneak + empty hand on a
 * grafting frame = take the cross piece back.
 */
public class TrellisBlock extends BaseEntityBlock {
    public static final BooleanProperty CROSS = BooleanProperty.create("cross");
    /** A Bell Cloche is fitted over this trellis (its effect is the {@code bell_cloche} crop_influence entry). */
    public static final BooleanProperty CLOCHE = BooleanProperty.create("cloche");
    public static final MapCodec<TrellisBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TrellisTier.CODEC.fieldOf("tier").forGetter(TrellisBlock::tier),
            propertiesCodec()
    ).apply(i, TrellisBlock::new));

    /** Items that act on crops through their own {@code useOn} instead of the trellis's interactions. */
    /** Extra clipping tools (Farmer's Delight knives via c:tools/knife). Shears clip through their own item code. */
    public static final TagKey<Item> CLIPPERS = TagKey.create(Registries.ITEM, Cultivar.id("clippers"));
    public static final TagKey<Item> CROP_TOOLS = TagKey.create(Registries.ITEM, Cultivar.id("crop_tools"));

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    private final TrellisTier tier;

    public TrellisBlock(TrellisTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(CROSS, false).setValue(CLOCHE, false));
    }

    public TrellisTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CROSS, CLOCHE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CropBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- placement & survival

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getBlock() instanceof TrellisBlock) {
            // Stacking: a plain segment on a plain trellis, up to the stack limit.
            return !below.getValue(CROSS) && !below.getValue(CLOCHE) && !state.getValue(CROSS) && !state.getValue(CLOCHE)
                    && stackDepth(level, pos) < CropBlockEntity.MAX_STACK;
        }
        return below.isFaceSturdy(level, pos.below(), Direction.UP) || below.is(Blocks.FARMLAND);
    }

    /** 0 for a base trellis, 1 for the segment above it, and so on. */
    static int stackDepth(BlockGetter level, BlockPos pos) {
        int depth = 0;
        BlockPos p = pos.below();
        while (depth < CropBlockEntity.MAX_STACK && level.getBlockState(p).getBlock() instanceof TrellisBlock) {
            depth++;
            p = p.below();
        }
        return depth;
    }

    public static boolean isSegment(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof TrellisBlock;
    }

    public static boolean hasSegmentAbove(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.above()).getBlock() instanceof TrellisBlock;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (!level.isClientSide() && from.equals(pos.above())) {
            CropBlockEntity.resolve(level, pos).ifPresent(CropBlockEntity::clampToSpace);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return dir == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isSegment(level, pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof CropBlockEntity crop) {
            crop.randomTick(level, random);
        }
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // Another trellis on the top face stacks a segment (normal block placement).
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof TrellisBlock && hit.getDirection() == Direction.UP) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Segments act on behalf of the plant rooted below.
        if (isSegment(level, pos)) {
            BlockPos base = CropBlockEntity.resolve(level, pos).map(BlockEntity::getBlockPos).orElse(pos);
            return base.equals(pos) ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : useItemOn(stack, level.getBlockState(base), level, base, player, hand, hit);
        }
        if (!(level.getBlockEntity(pos) instanceof CropBlockEntity crop)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Cultivar tools (spade, shears, tiller, satchel) handle the click themselves via Item#useOn.
        if (stack.is(CROP_TOOLS)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        // Hoe clears weeds.
        if (stack.is(ItemTags.HOES) && crop.hasWeed()) {
            if (!level.isClientSide()) {
                crop.clearWeeds(drop -> popResource(level, pos, drop));
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Fertilizers (datapack registry: bone meal, seed meal...) push growth or trigger a cross.
        var fertilizer = CropBlockEntity.fertilizerFor(level, stack);
        if (fertilizer.isPresent()) {
            if (level instanceof ServerLevel server && crop.fertilize(server, server.random, fertilizer.get())) {
                stack.consume(1, player);
                server.levelEvent(1505, pos, 0);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Knives (and anything else tagged cultivar:clippers) clip like Pruning Shears.
        if (stack.is(CLIPPERS) && crop.hasPlant()) {
            if (!level.isClientSide() && crop.clip(drop -> popResource(level, pos, drop))) {
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1f, 1.2f);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Same-tier trellis on an empty frame makes a grafting frame.
        if (stack.is(asItem()) && !state.getValue(CROSS) && !state.getValue(CLOCHE) && !crop.hasPlant()
                && !hasSegmentAbove(level, pos)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(CROSS, true), UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1f, 1f);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Planting.
        Optional<Genome> genome = genomeFor(stack, level);
        if (genome.isPresent() && !state.getValue(CROSS) && !crop.hasPlant()) {
            if (!level.isClientSide() && crop.plant(genome.get())) {
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (isSegment(level, pos)) {
            BlockPos base = CropBlockEntity.resolve(level, pos).map(BlockEntity::getBlockPos).orElse(pos);
            return base.equals(pos) ? InteractionResult.PASS : useWithoutItem(level.getBlockState(base), level, base, player, hit);
        }
        if (!(level.getBlockEntity(pos) instanceof CropBlockEntity crop)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && state.getValue(CLOCHE)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(CLOCHE, false), UPDATE_ALL);
                popResource(level, pos, new ItemStack(ClocheModule.BELL_CLOCHE.get()));
                level.playSound(null, pos, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1f, 1.2f);
                crop.invalidateInfluence();
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (player.isShiftKeyDown() && state.getValue(CROSS)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(CROSS, false), UPDATE_ALL);
                popResource(level, pos, new ItemStack(asItem()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (crop.isMature()) {
            if (!level.isClientSide()) {
                crop.harvest(drop -> popResource(level, pos, drop));
                level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    /** Genome from a cultivar seed, or a base genome for a vanilla/modded seed listed by some plant. */
    public static Optional<Genome> genomeFor(ItemStack stack, Level level) {
        Genome existing = stack.get(CoreModule.GENOME.get());
        if (existing != null) {
            return Optional.of(existing);
        }
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return level.registryAccess().registryOrThrow(CultivarRegistries.PLANT).holders()
                .filter(h -> h.value().seeds().map(set -> set.contains(stack.getItemHolder())).orElse(false))
                .findFirst()
                .map(h -> Genome.base((Holder<Plant>) h));
    }

    // ---------------------------------------------------------------- drops & redstone

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(asItem(), state.getValue(CROSS) ? 2 : 1));
        if (state.getValue(CLOCHE)) {
            drops.add(new ItemStack(ClocheModule.BELL_CLOCHE.get()));
        }
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CropBlockEntity crop) {
            crop.collectBreakDrops(drops::add);
        }
        return drops;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return CropBlockEntity.resolve(level, pos).map(CropBlockEntity::comparatorSignal).orElse(0);
    }
}
