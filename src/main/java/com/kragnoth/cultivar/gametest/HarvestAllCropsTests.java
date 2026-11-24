package com.kragnoth.cultivar.gametest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.crop.PlantHolder;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.compat.create.CultivarHarvesting;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import com.kragnoth.cultivar.content.field.FieldCropBlockEntity;
import com.kragnoth.cultivar.content.field.FieldCropModule;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Every registered plant, on a field crop and on a (stacked) trellis, harvested through the same entry point Create's
 * Mechanical Harvester uses ({@link CultivarHarvesting#tryHarvest}), at the base and at every upper segment. Catches
 * plants whose harvest yields nothing, leaves them unripe-looking, or uproots them.
 */
@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestAllCropsTests {
    /** Harvests before "never dropped anything" fails: enough that even chance-only products (3% diamonds) show. */
    private static int attempts(Plant plant) {
        double best = plant.products().stream().mapToDouble(pr -> Math.min(1, pr.chance())).max().orElse(0);
        return best <= 0 ? 1 : (int) Math.min(1000, Math.ceil(Math.log(1e-5) / Math.log1p(-Math.min(best, 0.999))) + 1);
    }

    private HarvestAllCropsTests() {}

    /** Full height exactly when ripe, for every plant (a full-height plant the harvester skips looks like a bug). */
    @GameTest(template = "empty")
    public static void tallPlantsAreFullHeightOnlyWhenRipe(GameTestHelper h) {
        for (Holder.Reference<Plant> ref : plants(h)) {
            Plant p = ref.value();
            for (int s = 0; s <= p.matureStage(); s++) {
                boolean full = p.blocksAt(s) == p.height();
                h.assertTrue(full == (s == p.matureStage()) || p.height() == 1,
                        ref.key().location() + " stage " + s + ": full height " + full + " but mature " + (s == p.matureStage()));
                h.assertTrue(s == 0 || p.blocksAt(s) >= p.blocksAt(s - 1), ref.key().location() + " never shrinks while growing");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void harvesterReapsEveryFieldCrop(GameTestHelper h) {
        everyPlant(h, (pos, plant) -> {
            h.setBlock(pos, FieldCropModule.FIELD_CROP.get());
            FieldCropBlockEntity crop = (FieldCropBlockEntity) h.getBlockEntity(pos);
            crop.plant().set(Genome.base(plant), plant.value().matureStage(), true);
            return crop.plant();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void harvesterReapsEveryTrellisCrop(GameTestHelper h) {
        everyPlant(h, (pos, plant) -> {
            int tall = plant.value().blocksAt(plant.value().matureStage());
            for (int i = tall - 1; i >= 0; i--) { // top first so each segment finds its base already there
                h.setBlock(pos.above(i), CropModule.TIMBER_TRELLIS.get());
            }
            CropBlockEntity crop = (CropBlockEntity) h.getBlockEntity(pos);
            h.assertTrue(crop.plant(Genome.base(plant), plant.value().matureStage()), plant.getRegisteredName() + " plants on a trellis");
            return crop.plant();
        });
    }

    private interface Planter {
        PlantCore plant(BlockPos pos, Holder<Plant> plant);
    }

    private static void everyPlant(GameTestHelper h, Planter planter) {
        var level = h.getLevel();
        BlockPos soil = new BlockPos(1, 0, 1), base = soil.above();
        MovementContext context = new MovementContext(level,
                new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, AllBlocks.MECHANICAL_HARVESTER.getDefaultState(), null), null);
        for (Holder.Reference<Plant> ref : plants(h)) {
            String name = ref.key().location().toString();
            Plant p = ref.value();
            int tall = p.blocksAt(p.matureStage());
            clear(h, base);
            h.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            PlantCore core = planter.plant(base, ref);
            h.assertTrue(core.isMature(), name + " starts ripe");
            int segment = 0;
            boolean dropped = false;
            int attempts = attempts(p);
            for (int i = 0; i < attempts && !dropped; i++) {
                core.setStage(p.matureStage());
                // Alternate the visited block between the base and each upper segment (harvesters at any height).
                BlockPos visit = base.above(segment);
                segment = (segment + 1) % tall;
                h.assertTrue(level.getBlockState(h.absolutePos(visit)).getBlock() instanceof PlantHolder,
                        name + " has a plant block at segment " + (visit.getY() - base.getY()));
                List<ItemStack> drops = new ArrayList<>();
                h.assertTrue(CultivarHarvesting.tryHarvest(context, h.absolutePos(visit), (c, s) -> drops.add(s)),
                        name + ": the harvester treats it as a Cultivar crop");
                h.assertTrue(core.hasPlant() && !core.isMature(), name + " stays planted and goes back to growing");
                h.assertTrue(p.blocksAt(core.stage()) < tall || tall == 1, name + " is visibly shorter after harvest");
                dropped = drops.stream().anyMatch(s -> !s.isEmpty());
            }
            h.assertTrue(dropped, name + " never dropped anything in " + attempts + " harvests");
        }
        clear(h, base);
        h.succeed();
    }

    private static void clear(GameTestHelper h, BlockPos base) {
        for (int i = 4; i >= 0; i--) { // top down so plants go before what holds them
            h.setBlock(base.above(i), Blocks.AIR);
        }
    }

    private static List<Holder.Reference<Plant>> plants(GameTestHelper h) {
        return h.getLevel().registryAccess().registryOrThrow(CultivarRegistries.PLANT).holders()
                .sorted(java.util.Comparator.comparing(r -> r.key().location().toString())).toList();
    }
}
