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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /test cultivar planterbeds}: every Cultivar machine setup, built for real (no test structures or barriers), as
 * a 3x3 and a 9x9 each, in rows going south from the player:
 * <ol>
 *   <li><b>Producer plot</b>: Planter Beds with farmland, wheat seeds, trellises, bell cloches, bone meal and water;</li>
 *   <li><b>Breeder plot</b>: the same, checkerboarded with grafting frames, stocked with both parents of a real
 *       cross;</li>
 *   <li><b>Trial plot</b>: Trial Beds stocked with every plant's seeds and a Botanical Ledger;</li>
 *   <li><b>Assayer unit</b>: Assayers (rows cycle andesite, brass, chromatic) loaded with unassayed seeds and a Ledger,
 *       driven by a Creative Motor through a cogwheel meshing with the corner Assayer, like a crafter grid.</li>
 * </ol>
 * Signs label each setup. {@code /test cultivarclear} removes it.
 */
final class MachineRigs {
    private static final int SPACING_Z = 13;
    @Nullable
    private static ServerLevel builtIn;
    @Nullable
    private static AABB bounds;

    private MachineRigs() {}

    static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        clear(source, false);
        BlockPos o = BlockPos.containing(source.getPosition()).offset(4, 0, 0);
        builtIn = level;
        bounds = new AABB(o.getX() - 4, o.getY() - 3, o.getZ() - 3, o.getX() + 18, o.getY() + 6, o.getZ() + 4 * SPACING_Z);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            level.setBlock(p, p.getY() < o.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        var plants = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT);
        Holder<Plant> wheat = plants.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(CultivarRegistries.PLANT,
                ResourceLocation.withDefaultNamespace("wheat")));
        Mutation cross = level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION).stream().findFirst().orElse(null);

        String[] kinds = {"Producer plot", "Breeder plot", "Trial plot", "Assayer unit"};
        for (int k = 0; k < kinds.length; k++) {
            for (int size : new int[]{3, 9}) {
                BlockPos at = o.offset(size == 3 ? 0 : 6, 0, k * SPACING_Z);
                sign(level, at.offset(-2, 0, -1), kinds[k], size + "x" + size);
                for (int dx = 0; dx < size; dx++) {
                    for (int dz = 0; dz < size; dz++) {
                        BlockPos p = at.offset(dx, 0, dz);
                        boolean first = dx == 0 && dz == 0;
                        switch (k) {
                            case 0 -> bed(level, p, wheat, wheat, false);
                            case 1 -> {
                                boolean frame = ((dx + dz) & 1) == 1;
                                Holder<Plant> a = cross == null ? wheat : cross.parentA(), b = cross == null ? wheat : cross.parentB();
                                bed(level, p, (dx & 1) == 0 ? a : b, (dx & 1) == 0 ? b : a, frame);
                            }
                            case 2 -> trial(level, p, plants.holders().map(h -> (Holder<Plant>) h).toList(), dx * size + dz, first);
                            default -> assayer(level, p, dz % 3, wheat, first);
                        }
                    }
                }
                if (k == 3) { // drive: Creative Motor -> cogwheel meshing with the corner Assayer
                    BlockPos cog = at.offset(-1, 0, 0);
                    level.setBlock(cog.below(), create("creative_motor").setValue(BlockStateProperties.FACING, Direction.UP), Block.UPDATE_ALL);
                    level.setBlock(cog, create("cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("Machine setups built east of you, rows going south: producer, breeder, trial "
                + "and assayer, each 3x3 and 9x9. Assayers run off a Creative Motor and cogwheel. /test cultivarclear removes them.")
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static BlockState create(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id)).defaultBlockState();
    }

    /** A Planter Bed with farmland, full water, supplies and seeds; a grafting frame on top makes it a breeder. */
    private static void bed(ServerLevel level, BlockPos p, Holder<Plant> seed, Holder<Plant> spare, boolean frame) {
        level.setBlock(p, PlanterBedModule.BED.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(p) instanceof PlanterBedBlockEntity bed)) {
            return;
        }
        bed.setSoil(Blocks.FARMLAND.defaultBlockState());
        bed.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, PlanterBedBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        bed.inventory.insertItem(0, CultivarSeedItem.of(Genome.base(seed), true).copyWithCount(8), false);
        bed.inventory.insertItem(1, CultivarSeedItem.of(Genome.base(spare), true).copyWithCount(8), false);
        bed.inventory.insertItem(PlanterBedBlockEntity.FERTILIZER, new ItemStack(Items.BONE_MEAL, 32), false);
        bed.inventory.insertItem(PlanterBedBlockEntity.TRELLIS, new ItemStack(CropModule.TIMBER_TRELLIS.get(), 4), false);
        if (frame) {
            level.setBlock(p.above(), CropModule.TIMBER_TRELLIS.get().defaultBlockState().setValue(TrellisBlock.CROSS, true), Block.UPDATE_ALL);
        } else {
            bed.inventory.insertItem(PlanterBedBlockEntity.CLOCHE, new ItemStack(ClocheModule.BELL_CLOCHE.get(), 1), false);
        }
    }

    /** A Trial Bed stocked with two plants (cycling through all of them across the plot); the first holds a Ledger. */
    private static void trial(ServerLevel level, BlockPos p, List<Holder<Plant>> all, int index, boolean ledger) {
        level.setBlock(p, TrialModule.TRIAL_BED.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(p) instanceof TrialBedBlockEntity bed) || all.isEmpty()) {
            return;
        }
        for (int s = 0; s < TrialBedBlockEntity.SEED_SLOTS; s++) {
            Holder<Plant> plant = all.get((index * 2 + s) % all.size());
            bed.inventory.insertItem(s, CultivarSeedItem.of(Genome.base(plant), true).copyWithCount(16), false);
        }
        if (ledger) {
            bed.inventory.insertItem(TrialBedBlockEntity.LEDGER, new ItemStack(LedgerModule.LEDGER.get()), false);
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
