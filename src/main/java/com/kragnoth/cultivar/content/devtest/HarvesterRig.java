package com.kragnoth.cultivar.content.devtest;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import com.kragnoth.cultivar.content.field.FieldCropBlockEntity;
import com.kragnoth.cultivar.content.field.FieldCropModule;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /test cultivarharvester}: a grid of Create rotation-farm stations covering every registered plant.
 *
 * <p>Each station: Creative Motor -> Mechanical Bearing (facing up) -> a glued arm of andesite casing with three
 * Mechanical Harvesters on each side and two barrels on top, so produce goes into the contraption's storage (Create's
 * default) and anything that can't fit falls to the ground. Around it, a disc of ripe crops split into four quadrants,
 * one plant per quadrant, alternating field crops and trellises (stacked for tall plants). A sign names the plants.
 *
 * <p>Random ticks are paused so nothing grows, withers or dies off-soil while you look; {@code /test cultivarharvester
 * regrow} sets every station crop ripe again for another pass. {@code /test cultivarclear} removes it all.
 */
final class HarvesterRig {
    private static final int ARM = 3;
    private static final int SPACING = 10;
    private static final int COLUMNS = 4;
    private static final double REACH_SQ = 12.9; // crops out to where the outermost harvester reaches

    @Nullable
    private static ServerLevel builtIn;
    @Nullable
    private static AABB bounds;
    private static int savedTickSpeed = -1;
    private static final List<BlockPos> crops = new ArrayList<>();

    private HarvesterRig() {}

    private static BlockState create(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id)).defaultBlockState();
    }

    static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        clear(source, false);
        List<Holder.Reference<Plant>> plants = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT).holders()
                .sorted((a, b) -> a.key().location().toString().compareTo(b.key().location().toString())).toList();
        int stations = (plants.size() + 3) / 4;
        int rows = (stations + COLUMNS - 1) / COLUMNS;
        // First station centre 6 blocks east of the player; the grid runs east (x) and south (z).
        BlockPos origin = BlockPos.containing(source.getPosition()).offset(6, 0, 0);
        builtIn = level;
        bounds = new AABB(origin.getX() - 5, origin.getY() - 3, origin.getZ() - 5,
                origin.getX() + COLUMNS * SPACING - 5, origin.getY() + 6, origin.getZ() + rows * SPACING - 5);

        var rule = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
        if (rule.get() != 0) {
            savedTickSpeed = rule.get();
            rule.set(0, level.getServer());
        }
        for (BlockPos p : box()) {
            int dy = p.getY() - origin.getY();
            level.setBlock(p, dy < 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int s = 0; s < stations; s++) {
            BlockPos c = origin.offset((s % COLUMNS) * SPACING, 0, (s / COLUMNS) * SPACING);
            List<Holder<Plant>> four = new ArrayList<>();
            for (int q = 0; q < 4; q++) {
                four.add(plants.get(Math.min(plants.size() - 1, s * 4 + q)));
            }
            station(level, c, four);
        }
        source.sendSuccess(() -> Component.literal(stations + " harvester stations built east of you, one per 4 plants ("
                + plants.size() + " plants, field crops and trellises alternating). Each arm carries two barrels: produce "
                + "goes there, overflow drops. Random ticks paused; ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("/test cultivarharvester regrow").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" ripens everything again; /test cultivarclear removes it.").withStyle(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static void station(ServerLevel level, BlockPos c, List<Holder<Plant>> four) {
        // Crops first, then the arm, so assembly only picks up the arm. Quadrant by direction from the centre.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int r2 = dx * dx + dz * dz;
                boolean arm = Math.abs(dz) <= 1 && dx >= 0 && dx <= ARM;
                if (arm || r2 <= 2 || r2 > REACH_SQ) {
                    continue;
                }
                Holder<Plant> plant = four.get((dx >= 0 ? 0 : 2) + (dz >= 0 ? 0 : 1));
                BlockPos at = c.offset(dx, 0, dz);
                level.setBlock(at.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
                if (((dx + dz) & 1) == 0) {
                    field(level, at, plant);
                } else {
                    trellis(level, at, plant);
                }
                crops.add(at.immutable());
            }
        }
        level.setBlock(c.offset(0, -2, 0), create("creative_motor").setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
        level.setBlock(c.below(), create("mechanical_bearing").setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
        for (int x = 0; x <= ARM; x++) {
            level.setBlock(c.offset(x, 0, 0), create("andesite_casing"), Block.UPDATE_ALL);
        }
        for (int x = 1; x <= ARM; x++) {
            level.setBlock(c.offset(x, 0, 1), create("mechanical_harvester").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
            level.setBlock(c.offset(x, 0, -1), create("mechanical_harvester").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
        }
        level.setBlock(c.above(), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
        level.setBlock(c.offset(1, 1, 0), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
        level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(c, c.offset(ARM, 1, 0))));

        // Sign in the empty corner, naming the quadrants.
        BlockPos signAt = c.offset(-4, 0, -4);
        level.setBlock(signAt, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(signAt) instanceof SignBlockEntity sign) {
            String[] corner = {"SE ", "NE ", "SW ", "NW "};
            SignText text = sign.getFrontText();
            for (int q = 0; q < 4; q++) {
                text = text.setMessage(q, Component.literal(corner[q] + four.get(q).unwrapKey().orElseThrow().location().getPath()));
            }
            sign.setText(text, true);
            sign.setChanged();
        }
    }

    private static void field(ServerLevel level, BlockPos at, Holder<Plant> plant) {
        level.setBlock(at, FieldCropModule.FIELD_CROP.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof FieldCropBlockEntity crop) {
            crop.plant().set(Genome.base(plant), plant.value().matureStage(), true);
        }
    }

    private static void trellis(ServerLevel level, BlockPos at, Holder<Plant> plant) {
        int tall = plant.value().blocksAt(plant.value().matureStage());
        for (int i = tall - 1; i >= 0; i--) { // top first so each segment finds its base already there
            level.setBlock(at.above(i), CropModule.TIMBER_TRELLIS.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(at) instanceof CropBlockEntity crop) {
            crop.plant(Genome.base(plant), plant.value().matureStage());
        }
    }

    /** {@code /test cultivarharvester regrow}: every station crop ripe again. */
    static int regrow(CommandSourceStack source) {
        if (builtIn == null) {
            source.sendFailure(Component.literal("No harvester stations built; run /test cultivarharvester first."));
            return 0;
        }
        int n = 0;
        for (BlockPos p : crops) {
            PlantCore plant = builtIn.getBlockEntity(p) instanceof FieldCropBlockEntity f ? f.plant()
                    : builtIn.getBlockEntity(p) instanceof CropBlockEntity t ? t.plant() : null;
            if (plant != null && plant.hasPlant()) {
                plant.setStage(plant.species().matureStage());
                n++;
            }
        }
        int ripened = n;
        source.sendSuccess(() -> Component.literal(ripened + " crops ripe again (" + (crops.size() - ripened) + " missing or uprooted)."), true);
        return 1;
    }

    private static Iterable<BlockPos> box() {
        return BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
    }

    static int clear(CommandSourceStack source, boolean say) {
        if (builtIn == null || bounds == null) {
            return 0;
        }
        ServerLevel level = builtIn;
        // Twice: removing a bearing can put its running contraption's blocks back.
        for (int pass = 0; pass < 2; pass++) {
            level.getEntitiesOfClass(Entity.class, bounds.inflate(2), e -> !(e instanceof Player)).forEach(Entity::discard);
            for (BlockPos p : box()) {
                level.setBlock(p, p.getY() < bounds.minY + 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if (savedTickSpeed >= 0) {
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(savedTickSpeed, level.getServer());
        }
        savedTickSpeed = -1;
        crops.clear();
        builtIn = null;
        bounds = null;
        if (say) {
            source.sendSuccess(() -> Component.literal("Harvester stations removed."), false);
        }
        return 1;
    }
}
