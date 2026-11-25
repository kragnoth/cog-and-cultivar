package com.kragnoth.cultivar.content.devtest;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity;
import com.kragnoth.cultivar.content.bed.PlanterBedBlockEntity;
import com.kragnoth.cultivar.content.bed.PlanterBedModule;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import com.kragnoth.cultivar.content.trial.TrialBedBlockEntity;
import com.kragnoth.cultivar.content.trial.TrialModule;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /test cultivar planterbeds}: every Cultivar machine set up the way it's meant to be used, built for real (no
 * test structures or barriers), small and large, in rows going south from the player. Each plot is fed and emptied
 * through one bed, since a plot shares its capabilities:
 * <ol>
 *   <li><b>Producer plot</b> (3x3, 8x8: a Planter Bed plot holds up to 64): an input chest + hopper feeds seeds,
 *       bone meal, trellises and cloches; a hopper under the corner bed empties the outputs into a chest; a Mechanical
 *       Pump (cogwheel + Creative Motor) draws from a 2x2 infinite pool into the plot's shared tank;</li>
 *   <li><b>Breeder plot</b> (3x3, 8x8): the same, checkerboarded with grafting frames, stocked with both parents of a
 *       real cross; children are banked as seeds and come out with the outputs;</li>
 *   <li><b>Trial plot</b> (3x3, 9x9: 81 beds is the full discovery chance): an input chest + hopper with a Ledger and
 *       seeds of many plants; no output hopper, since the Ledger is what it produces (sneak-click to take it);</li>
 *   <li><b>Assayer unit</b> (3x3, 9x9): rows cycle andesite, brass, chromatic, driven by a cogwheel meshing with the
 *       corner Assayer; loaded with unassayed seeds and a Ledger.</li>
 * </ol>
 * Every plot is driven by a Creative Motor and a cogwheel meshing with a corner machine (beds and Assayers mesh with
 * each other like Mechanical Crafters). Signs label each part. {@code /test cultivarclear} removes it.
 */
final class MachineRigs {
    private static final int SPACING_Z = 13;
    private static final int LARGE_X = 11;
    @Nullable
    private static ServerLevel builtIn;
    @Nullable
    private static AABB bounds;

    private MachineRigs() {}

    private enum Kind { PRODUCER, BREEDER, TRIAL, ASSAYER }

    static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        clear(source, false);
        // Machines stand on a stone floor at the player's feet level, so hoppers fit underneath.
        BlockPos o = BlockPos.containing(source.getPosition()).offset(6, 0, 0);
        builtIn = level;
        bounds = new AABB(o.getX() - 6, o.getY() - 3, o.getZ() - 3, o.getX() + LARGE_X + 11, o.getY() + 6, o.getZ() + 4 * SPACING_Z);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            level.setBlock(p, p.getY() <= o.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        var plants = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT);
        Holder<Plant> wheat = plants.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(CultivarRegistries.PLANT,
                ResourceLocation.withDefaultNamespace("wheat")));
        Mutation cross = level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION).stream().findFirst().orElse(null);
        Holder<Plant> parentA = cross == null ? wheat : cross.parentA(), parentB = cross == null ? wheat : cross.parentB();
        List<Holder<Plant>> all = plants.holders().map(h -> (Holder<Plant>) h).toList();

        for (Kind kind : Kind.values()) {
            int large = kind == Kind.PRODUCER || kind == Kind.BREEDER ? 8 : 9;
            for (int size : new int[]{3, large}) {
                BlockPos at = o.offset(size == 3 ? 0 : LARGE_X, 1, kind.ordinal() * SPACING_Z);
                String name = switch (kind) {
                    case PRODUCER -> "Producer plot";
                    case BREEDER -> "Breeder plot";
                    case TRIAL -> "Trial plot";
                    case ASSAYER -> "Assayer unit";
                };
                sign(level, at.offset(-1, 0, -2), name, size + "x" + size);
                for (int dx = 0; dx < size; dx++) {
                    for (int dz = 0; dz < size; dz++) {
                        BlockPos p = at.offset(dx, 0, dz);
                        switch (kind) {
                            case PRODUCER -> bed(level, p, wheat, false);
                            case BREEDER -> bed(level, p, (dx & 1) == 0 ? parentA : parentB, ((dx + dz) & 1) == 1);
                            case TRIAL -> trial(level, p, all, dx * size + dz);
                            case ASSAYER -> assayer(level, p, dz % 3, wheat, dx == 0 && dz == 0);
                        }
                    }
                }
                if (kind != Kind.ASSAYER) { // beds need rotation too: a cogwheel meshing with the east corner bed
                    BlockPos cog = at.offset(size, 0, 0);
                    level.setBlock(cog.below(), create("creative_motor").setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
                    level.setBlock(cog, create("cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
                    sign(level, cog.offset(1, 0, 0), "Creative Motor", "drives a cog;", "beds mesh", "like crafters");
                }
                switch (kind) {
                    case PRODUCER -> {
                        inputChest(level, at, List.of(seeds(wheat, 64), new ItemStack(Items.BONE_MEAL, 64),
                                new ItemStack(CropModule.TIMBER_TRELLIS.get(), 64), new ItemStack(ClocheModule.BELL_CLOCHE.get(), 16),
                                new ItemStack(ClocheModule.BELL_CLOCHE.get(), 16), new ItemStack(ClocheModule.BELL_CLOCHE.get(), 16),
                                new ItemStack(ClocheModule.BELL_CLOCHE.get(), 16)));
                        outputChest(level, at);
                        water(level, at, size);
                    }
                    case BREEDER -> {
                        inputChest(level, at, List.of(seeds(parentA, 64), seeds(parentB, 64), new ItemStack(Items.BONE_MEAL, 64),
                                new ItemStack(CropModule.TIMBER_TRELLIS.get(), 64)));
                        outputChest(level, at);
                        water(level, at, size);
                    }
                    case TRIAL -> {
                        java.util.ArrayList<ItemStack> stock = new java.util.ArrayList<>();
                        stock.add(new ItemStack(LedgerModule.LEDGER.get()));
                        all.stream().limit(26).forEach(pl -> stock.add(seeds(pl, 16)));
                        inputChest(level, at, stock);
                    }
                    case ASSAYER -> { // Creative Motor -> cogwheel meshing with the corner Assayer
                        BlockPos cog = at.offset(-1, 0, 0);
                        level.setBlock(cog.below(), create("creative_motor").setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
                        level.setBlock(cog, create("cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
                        sign(level, cog.offset(0, 0, -1), "Creative Motor", "drives a cog;", "Assayers mesh", "like crafters");
                    }
                }
            }
        }
        source.sendSuccess(() -> Component.literal("Machine setups built east of you, rows going south: producer, breeder, trial "
                + "and assayer, small and large, each fed by an input chest + hopper and (beds) emptied by a hopper into an output "
                + "chest, with a pumped water line. /test cultivarclear removes them.").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static ItemStack seeds(Holder<Plant> plant, int count) {
        return CultivarSeedItem.of(Genome.base(plant), true).copyWithCount(count);
    }

    /** Chest on a hopper pointing into the corner bed's west side: one feed serves the whole plot. */
    private static void inputChest(ServerLevel level, BlockPos corner, List<ItemStack> stock) {
        BlockPos hopper = corner.west();
        level.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, Direction.EAST), Block.UPDATE_ALL);
        BlockPos chest = hopper.above();
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c) {
            for (int i = 0; i < Math.min(27, stock.size()); i++) {
                c.setItem(i, stock.get(i));
            }
        }
        sign(level, corner.offset(-1, 0, -1), "Input: chest +", "hopper feeds", "the whole plot");
    }

    /** Hopper under the corner bed (pulls the plot's outputs) into a chest beside it. */
    private static void outputChest(ServerLevel level, BlockPos corner) {
        BlockPos hopper = corner.below();
        level.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, Direction.WEST), Block.UPDATE_ALL);
        level.setBlock(hopper.west(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        // (the input hopper stands on this chest; hoppers only pull from above, so they don't interfere)
        sign(level, corner.offset(-2, 0, -1), "Output: hopper", "under corner bed", "-> chest below", "the input hopper");
    }

    /**
     * Water line into the far corner bed's west side: a Mechanical Pump drawing from a 2x2 infinite pool, powered by a
     * cogwheel beside it (pumps mesh like cogs) and a Creative Motor.
     */
    private static void water(ServerLevel level, BlockPos corner, int size) {
        BlockPos pump = corner.offset(-1, 0, size - 1);
        level.setBlock(pump, create("mechanical_pump").setValue(BlockStateProperties.FACING, Direction.EAST), Block.UPDATE_ALL);
        for (int x = -3; x <= -2; x++) {
            for (int dz = -1; dz <= 0; dz++) {
                level.setBlock(corner.offset(x, 0, size - 1 + dz), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(corner.offset(x, 0, size - 3), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(corner.offset(-4, 0, size - 2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corner.offset(-4, 0, size - 1), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corner.offset(-3, 0, size), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pump.north(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos cog = pump.south();
        level.setBlock(cog, create("cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        level.setBlock(cog.west(), create("creative_motor").setValue(BlockStateProperties.FACING, Direction.EAST), Block.UPDATE_ALL);
        sign(level, cog.south(), "Water: pump +", "2x2 pool fills", "the plot's tank");
    }

    private static BlockState create(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id)).defaultBlockState();
    }

    /** A Planter Bed with farmland, primed with a little stock; a grafting frame on top makes it a breeder. */
    private static void bed(ServerLevel level, BlockPos p, Holder<Plant> seed, boolean frame) {
        level.setBlock(p, PlanterBedModule.BED.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(p) instanceof PlanterBedBlockEntity bed)) {
            return;
        }
        bed.setSoil(Blocks.FARMLAND.defaultBlockState());
        bed.inventory.insertItem(0, seeds(seed, 4), false);
        if (frame) {
            level.setBlock(p.above(), CropModule.TIMBER_TRELLIS.get().defaultBlockState().setValue(TrellisBlock.CROSS, true), Block.UPDATE_ALL);
        }
    }

    /** A Trial Bed primed with two plants (cycling through all of them across the plot). */
    private static void trial(ServerLevel level, BlockPos p, List<Holder<Plant>> all, int index) {
        level.setBlock(p, TrialModule.TRIAL_BED.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(p) instanceof TrialBedBlockEntity bed) || all.isEmpty()) {
            return;
        }
        for (int s = 0; s < TrialBedBlockEntity.SEED_SLOTS; s++) {
            bed.inventory.insertItem(s, seeds(all.get((index * 2 + s) % all.size()), 4), false);
        }
    }

    /** An Assayer of the given tier (0 andesite, 1 brass, 2 chromatic) with 16 unassayed seeds; the first holds a Ledger. */
    private static void assayer(ServerLevel level, BlockPos p, int tier, Holder<Plant> plant, boolean ledger) {
        level.setBlock(p, AssayModule.tiers().get(tier).get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(p) instanceof AssayingEngineBlockEntity be)) {
            return;
        }
        be.inventory.insertItem(AssayingEngineBlockEntity.SEED_IN, CultivarSeedItem.of(Genome.base(plant), false).copyWithCount(16), false);
        if (ledger) {
            be.inventory.insertItem(AssayingEngineBlockEntity.LEDGER, new ItemStack(LedgerModule.LEDGER.get()), false);
        }
    }

    private static void sign(ServerLevel level, BlockPos at, String... lines) {
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            SignText text = sign.getFrontText();
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                text = text.setMessage(i, Component.literal(lines[i]));
            }
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
        }
    }

    static int clear(CommandSourceStack source, boolean say) {
        if (builtIn == null || bounds == null) {
            return 0;
        }
        ServerLevel level = builtIn;
        for (int pass = 0; pass < 2; pass++) { // twice: breaking containers drops items, the second pass sweeps them
            level.getEntitiesOfClass(Entity.class, bounds.inflate(2), e -> !(e instanceof Player)).forEach(Entity::discard);
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
                level.setBlock(p, p.getY() < bounds.minY + 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        builtIn = null;
        bounds = null;
        if (say) {
            source.sendSuccess(() -> Component.literal("Machine setups removed."), false);
        }
        return 1;
    }
}
