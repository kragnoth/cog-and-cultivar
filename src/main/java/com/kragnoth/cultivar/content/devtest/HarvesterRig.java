package com.kragnoth.cultivar.content.devtest;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.field.FieldCropBlockEntity;
import com.kragnoth.cultivar.content.field.FieldCropModule;
import com.kragnoth.cultivar.content.irrigation.IrrigationModule;
import com.kragnoth.cultivar.content.irrigation.OllaBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /test cultivarharvester}: a standard Create rotation farm, built from real parts so it exercises the whole
 * chain. A small water wheel (fed by falling water) drives shafts and gearboxes under the field into a Mechanical
 * Bearing facing up at its centre; the bearing turns a glued andesite-casing arm with Mechanical Harvesters on both
 * sides. The field around it is ripe wheat: field crops on the west half, trellis crops on the east, plus two tall
 * field cane stacks. Buried filled Ollas keep the farmland moist. Random ticks stay on, so harvested crops regrow and
 * get harvested again.
 *
 * <p>Layout (relative to the bearing, y = crop level): soil y-1, drivetrain y-2, arm along +x at y 0.
 */
final class HarvesterRig {
    private static final int RADIUS = 3;
    @Nullable
    private static ServerLevel builtIn;
    @Nullable
    private static AABB bounds;

    private HarvesterRig() {}

    private static BlockState create(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id)).defaultBlockState();
    }

    static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        clear(source, false);
        // Centre 9 blocks in front of the player (east), at their feet level.
        BlockPos c = BlockPos.containing(source.getPosition()).offset(9, 0, 0);
        builtIn = level;
        bounds = new AABB(c.getX() - 10, c.getY() - 3, c.getZ() - 6, c.getX() + 6, c.getY() + 5, c.getZ() + 6);

        // Ground: stone, farmland under the crop disc, air above.
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-10, -3, -6), c.offset(6, 5, 6))) {
            int dy = p.getY() - c.getY();
            level.setBlock(p, dy < 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // Drivetrain: wheel (-7,0,0) -> shaft -> gearbox (-5,0,0) -> down -> gearbox (-5,-2,0) -> shafts -> gearbox (0,-2,0) -> up.
        BlockState shaftX = create("shaft").setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        BlockState gearbox = create("gearbox").setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        level.setBlock(c.offset(-7, 0, 0), create("water_wheel").setValue(BlockStateProperties.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(c.offset(-6, 0, 0), shaftX, Block.UPDATE_ALL);
        level.setBlock(c.offset(-5, 0, 0), gearbox, Block.UPDATE_ALL);
        level.setBlock(c.offset(-5, -1, 0), create("shaft").setValue(BlockStateProperties.AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
        level.setBlock(c.offset(-5, -2, 0), gearbox, Block.UPDATE_ALL);
        for (int x = -4; x <= -1; x++) {
            level.setBlock(c.offset(x, -2, 0), shaftX, Block.UPDATE_ALL);
        }
        level.setBlock(c.offset(0, -2, 0), gearbox, Block.UPDATE_ALL);

        // Crops first (not where the arm will be), then the arm, so assembly only picks up the arm.
        Holder<Plant> wheat = plant(level, "minecraft:wheat"), cane = plant(level, "minecraft:sugar_cane");
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                boolean arm = Math.abs(dz) <= 1 && dx >= 0 && dx <= RADIUS;
                if (arm || dx * dx + dz * dz > (RADIUS + 0.6) * (RADIUS + 0.6)) {
                    continue;
                }
                BlockPos at = c.offset(dx, 0, dz);
                level.setBlock(at.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
                if ((dx == -2 && dz == 2) || (dx == -2 && dz == -2)) {
                    field(level, at, cane, cane.value().matureStage());
                } else if (dx < 0) {
                    field(level, at, wheat, wheat.value().matureStage());
                } else {
                    level.setBlock(at, CropModule.TIMBER_TRELLIS.get().defaultBlockState(), Block.UPDATE_ALL);
                    if (level.getBlockEntity(at) instanceof CropBlockEntity crop) {
                        crop.plant(Genome.base(wheat), wheat.value().matureStage());
                    }
                }
            }
        }
        // Filled Ollas under the field keep every farmland block moist (4-block reach from the layer below).
        for (int[] o : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            level.setBlock(c.offset(o[0], -2, o[1]), IrrigationModule.OLLA.get().defaultBlockState().setValue(OllaBlock.FILLED, true), Block.UPDATE_ALL);
        }

        // Bearing facing up, arm of andesite casing along +x, harvesters on both sides (one side always leads).
        level.setBlock(c.below(), create("mechanical_bearing").setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
        for (int x = 0; x <= RADIUS; x++) {
            level.setBlock(c.offset(x, 0, 0), create("andesite_casing"), Block.UPDATE_ALL);
        }
        for (int x = 1; x <= RADIUS; x++) {
            level.setBlock(c.offset(x, 0, 1), create("mechanical_harvester").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
            level.setBlock(c.offset(x, 0, -1), create("mechanical_harvester").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
        }
        level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(c, c.offset(RADIUS, 0, 0))));

        // Water last: a source above the wheel's south side falls past it; glass keeps it from spreading.
        for (BlockPos g : new BlockPos[]{c.offset(-8, 1, 1), c.offset(-6, 1, 1), c.offset(-7, 1, 2), c.offset(-7, 1, 0),
                c.offset(-8, 0, 1), c.offset(-6, 0, 1), c.offset(-7, 0, 2)}) {
            level.setBlock(g, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(c.offset(-7, 1, 1), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);

        source.sendSuccess(() -> Component.literal("Harvester rotation farm built east of you: water wheel -> shafts and gearboxes "
                + "under the field -> Mechanical Bearing at the centre. The arm assembles once the wheel turns. West half: field wheat "
                + "(+2 cane stacks); east half: trellis wheat. Expect produce on the ground and crops back at their first stage, "
                + "regrowing. /test cultivarclear removes it.").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static Holder<Plant> plant(ServerLevel level, String id) {
        return level.registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.parse(id)));
    }

    private static void field(ServerLevel level, BlockPos at, Holder<Plant> plant, int stage) {
        level.setBlock(at, FieldCropModule.FIELD_CROP.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof FieldCropBlockEntity crop) {
            crop.plant().set(Genome.base(plant), stage, true);
        }
    }

    static int clear(CommandSourceStack source, boolean say) {
        if (builtIn == null || bounds == null) {
            return 0;
        }
        ServerLevel level = builtIn;
        // Twice: removing the bearing can put a running contraption's blocks back.
        for (int pass = 0; pass < 2; pass++) {
            level.getEntitiesOfClass(Entity.class, bounds.inflate(2), e -> !(e instanceof Player)).forEach(Entity::discard);
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
                level.setBlock(p, p.getY() < bounds.minY + 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        builtIn = null;
        bounds = null;
        if (say) {
            source.sendSuccess(() -> Component.literal("Harvester farm removed."), false);
        }
        return 1;
    }
}
