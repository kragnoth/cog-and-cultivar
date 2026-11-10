package com.kragnoth.cultivar.content.irrigation;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sprinkler (Phase 4): one block, fed water by anything that speaks NeoForge's fluid capability (Create pipes and
 * pumps, Pipez, Modern Dynamics, tanks). {@code FACING} is the nozzle direction: it sits on top of, beside or under
 * whatever it's attached to. While it has water it adds random ticks and occasional bone-meal pulses to growables
 * around it and keeps farmland moist. See {@link SprinklerBlockEntity}.
 */
public class SprinklerBlock extends BaseEntityBlock {
    public static final MapCodec<SprinklerBlock> CODEC = simpleCodec(SprinklerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            SHAPES.put(d, Shapes.or(box(d, 4, 0, 4, 12, 2, 12), box(d, 6, 2, 6, 10, 6, 10), box(d, 5, 6, 5, 11, 9, 11)));
        }
    }

    /** A box given for a nozzle pointing UP, rotated to point {@code d}. */
    private static VoxelShape box(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case UP -> Block.box(x0, y0, z0, x1, y1, z1);
            case DOWN -> Block.box(x0, 16 - y1, z0, x1, 16 - y0, z1);
            case NORTH -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case SOUTH -> Block.box(x0, z0, y0, x1, z1, y1);
            case WEST -> Block.box(16 - y1, z0, x0, 16 - y0, z1, x1);
            case EAST -> Block.box(y0, z0, x0, y1, z1, x1);
        };
    }

    public SprinklerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SprinklerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, IrrigationModule.SPRINKLER_BE.get(),
                (l, p, s, be) -> be.serverTick());
    }

    /** Spray particles while active (client only; driven by the synced ACTIVE state). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double cx = pos.getX() + 0.5 + facing.getStepX() * 0.45;
        double cy = pos.getY() + 0.5 + facing.getStepY() * 0.45;
        double cz = pos.getZ() + 0.5 + facing.getStepZ() * 0.45;
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.15 + random.nextDouble() * 0.2;
            level.addParticle(ParticleTypes.SPLASH, cx, cy, cz, Math.cos(angle) * speed, 0.1, Math.sin(angle) * speed);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FALLING_WATER, cx + random.nextGaussian() * 1.5, cy, cz + random.nextGaussian() * 1.5, 0, 0, 0);
        }
    }
}
