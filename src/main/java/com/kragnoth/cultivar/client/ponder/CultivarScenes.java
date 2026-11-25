package com.kragnoth.cultivar.client.ponder;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.AllelePair;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ponder storyboards. Text lives here and is mirrored into the lang file by tools/ponder/ponder_lang.py (Ponder reads
 * {@code cultivar.ponder.<scene>.header / text_N}, numbered in the order the texts appear below), so keep every
 * {@code .text("...")} a single literal.
 *
 * Crops are planted at play time through {@link CropBlockEntity#setDisplay}; species are looked up lazily, so the
 * scenes also open from menus where no world is loaded (the trellises just stay empty there).
 */
public final class CultivarScenes {
    private CultivarScenes() {}

    // ------------------------------------------------------------------------------------------------ helpers

    private static Optional<Holder<Plant>> species(String id) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }
        return level.registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolder(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.parse(id))).map(h -> h);
    }

    private static @Nullable Genome genome(String id, int stats) {
        return species(id).map(h -> {
            Map<ResourceKey<Stat>, AllelePair> map = new HashMap<>();
            for (var stat : new ResourceKey[]{CoreModule.GAIN.getKey(), CoreModule.GROWTH.getKey(),
                    CoreModule.STRENGTH.getKey(), CoreModule.VIGOUR.getKey()}) {
                @SuppressWarnings("unchecked") ResourceKey<Stat> key = (ResourceKey<Stat>) stat;
                map.put(key, AllelePair.of(stats, stats));
            }
            return new Genome(h, map);
        }).orElse(null);
    }

    /** Shows a species at a stage on the trellis at {@code pos} (null = empty), and redraws it. */
    private static void crop(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, @Nullable String id, int stats, int stage) {
        scene.world().modifyBlockEntity(pos, CropBlockEntity.class, be -> be.setDisplay(id == null ? null : genome(id, stats), stage));
        scene.world().modifyBlockEntityNBT(util.select().position(pos), CropBlockEntity.class, tag -> { }, true);
    }

    /** Grows a crop stage by stage, a few ticks apart. */
    private static void grow(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String id, int stats, int from, int to) {
        for (int s = from; s <= to; s++) {
            crop(scene, util, pos, id, stats, s);
            scene.idle(4);
        }
    }

    private static void weed(SceneBuilder scene, BlockPos pos, @Nullable String id, int stage) {
        scene.world().modifyBlockEntity(pos, CropBlockEntity.class, be -> {
            var level = Minecraft.getInstance().level;
            Holder<Weed> w = id == null || level == null ? null : level.registryAccess().registryOrThrow(CultivarRegistries.WEED)
                    .getHolder(ResourceKey.create(CultivarRegistries.WEED, ResourceLocation.parse(id))).orElse(null);
            be.setWeed(w, stage);
        });
    }

    private static void frame(SceneBuilder scene, BlockPos pos, boolean on) {
        scene.world().modifyBlock(pos, s -> s.setValue(TrellisBlock.CROSS, on), false);
    }

    private static void sparkle(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos) {
        scene.effects().emitParticles(util.vector().centerOf(pos),
                scene.effects().simpleParticleEmitter(ParticleTypes.HAPPY_VILLAGER, new Vec3(0, 0.05, 0)), 6, 2);
        scene.effects().indicateSuccess(pos);
    }

    // ------------------------------------------------------------------------------------------------ spreading

    public static void spreading(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("spreading", "Spreading crops");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos a = util.grid().at(1, 1, 2), f = util.grid().at(2, 1, 2), c = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().position(a), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(a), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.WHEAT_SEEDS));
        scene.idle(10);
        crop(scene, util, a, "minecraft:wheat", 1, 0);
        scene.overlay().showText(80).text("Plant seeds on a Trellis standing on soil. Most vanilla and modded seeds work.")
                .pointAt(util.vector().topOf(a)).placeNearTarget().attachKeyFrame();
        scene.idle(40);
        grow(scene, util, a, "minecraft:wheat", 1, 1, 7);
        scene.idle(30);

        scene.overlay().showControls(util.vector().topOf(a), Pointing.DOWN, 40).rightClick();
        scene.overlay().showText(70).text("Right-click a ripe crop to harvest it. It stays planted and grows back.")
                .pointAt(util.vector().topOf(a)).placeNearTarget();
        scene.idle(20);
        scene.world().createItemEntity(util.vector().topOf(a), new Vec3(0.05, 0.25, 0), new ItemStack(Items.WHEAT));
        crop(scene, util, a, "minecraft:wheat", 1, 0);
        scene.idle(30);
        grow(scene, util, a, "minecraft:wheat", 1, 1, 7);
        scene.world().modifyEntities(ItemEntity.class, e -> e.discard());
        scene.idle(30);

        scene.world().showSection(util.select().position(f), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(f), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(CropModule.TIMBER_TRELLIS.get()));
        scene.idle(10);
        frame(scene, f, true);
        scene.overlay().showText(80).text("Right-click an empty Trellis with another Trellis to make a Grafting Frame.")
                .pointAt(util.vector().topOf(f)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(100).text("A Grafting Frame beside a single ripe crop slowly copies it. The higher the parent's Vigor, the sooner it happens.")
                .pointAt(util.vector().centerOf(f)).placeNearTarget();
        scene.idle(70);
        frame(scene, f, false);
        crop(scene, util, f, "minecraft:wheat", 1, 0);
        sparkle(scene, util, f);
        scene.idle(30);
        scene.overlay().showText(70).text("The copy is a perfect clone: the same species and the same stats.")
                .pointAt(util.vector().topOf(f)).placeNearTarget();
        grow(scene, util, f, "minecraft:wheat", 1, 1, 7);
        scene.idle(50);

        scene.world().showSection(util.select().position(c), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(a), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(ToolsModule.PRUNING_SHEARS.get()));
        scene.idle(10);
        scene.world().createItemEntity(util.vector().topOf(a), new Vec3(0.08, 0.25, 0), new ItemStack(ToolsModule.CUTTING.get()));
        crop(scene, util, a, "minecraft:wheat", 1, 6);
        scene.overlay().showText(100).text("Pruning Shears (or a knife) take a Cutting from a ripe crop and set it back one stage. The chance is (Strength + 1) in 10.")
                .pointAt(util.vector().topOf(a)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.world().modifyEntities(ItemEntity.class, e -> e.discard());
        scene.overlay().showControls(util.vector().topOf(c), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(ToolsModule.CUTTING.get()));
        scene.idle(10);
        crop(scene, util, c, "minecraft:wheat", 1, 1);
        scene.overlay().showText(80).text("A Cutting plants an exact clone that starts already sprouted.")
                .pointAt(util.vector().topOf(c)).placeNearTarget();
        scene.idle(90);

        weed(scene, f, "cultivar:rust_rot", 2);
        scene.world().modifyBlockEntityNBT(util.select().position(f), CropBlockEntity.class, tag -> { }, true);
        scene.idle(10);
        scene.overlay().showText(90).text("Blight on a crop stops it from spreading or breeding. Clear it with a hoe; let it ripen first and it drops something useful.")
                .pointAt(util.vector().topOf(f)).placeNearTarget().colored(PonderPalette.RED).attachKeyFrame();
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(f), Pointing.DOWN, 30).rightClick().withItem(new ItemStack(Items.IRON_HOE));
        scene.idle(10);
        weed(scene, f, null, 0);
        scene.world().modifyBlockEntityNBT(util.select().position(f), CropBlockEntity.class, tag -> { }, true);
        scene.idle(40);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ stats

    public static void stats(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("crop_stats", "Improving crop stats");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos p1 = util.grid().at(1, 1, 2), f = util.grid().at(2, 1, 2), p2 = util.grid().at(3, 1, 2);
        BlockPos f2 = util.grid().at(4, 1, 2), p3 = util.grid().at(5, 1, 2);
        scene.world().showSection(util.select().position(p1).add(util.select().position(p2)), Direction.DOWN);
        scene.idle(10);
        crop(scene, util, p1, "minecraft:carrot", 1, 7);
        crop(scene, util, p2, "minecraft:carrot", 2, 7);
        scene.overlay().showText(90).text("Every seed carries four stats, each from 1 to 10.").independent(10).attachKeyFrame();
        scene.idle(30);
        scene.overlay().showText(140).text("Gain: more produce per harvest. Growth: ripens faster. Strength: copes with worse soil and light and fends off blight; at 10 it grows on any soil. Vigor: crosses more readily and passes on better stats.")
                .independent(40);
        scene.idle(150);

        scene.world().showSection(util.select().position(f), Direction.DOWN);
        scene.idle(10);
        frame(scene, f, true);
        scene.overlay().showText(80).text("To improve a line, put a Grafting Frame between two ripe crops of the same species.")
                .pointAt(util.vector().topOf(f)).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        frame(scene, f, false);
        crop(scene, util, f, "minecraft:carrot", 3, 0);
        sparkle(scene, util, f);
        scene.overlay().showText(120).text("The child takes one value from each parent for every stat, and each may drift a step up or down. Low stats climb easily; the last steps to 10 are the hardest.")
                .pointAt(util.vector().topOf(f)).placeNearTarget();
        scene.idle(60);
        grow(scene, util, f, "minecraft:carrot", 3, 1, 7);
        scene.idle(60);

        scene.overlay().showText(80).text("Stats stay hidden until the seed is read in an Assayer.")
                .pointAt(util.vector().topOf(f)).placeNearTarget().colored(PonderPalette.BLUE);
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(f), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(ToolsModule.SEED_SATCHEL.get()));
        scene.idle(10);
        scene.overlay().showText(100).text("Bag the child's seed with a Seed Satchel (or take a Cutting), replace the weaker parent with it, and breed again.")
                .pointAt(util.vector().topOf(f)).placeNearTarget().attachKeyFrame();
        scene.idle(50);
        crop(scene, util, f, null, 1, 0);
        frame(scene, f, true);
        crop(scene, util, p1, "minecraft:carrot", 3, 7);
        sparkle(scene, util, p1);
        scene.idle(70);

        scene.world().showSection(util.select().position(f2).add(util.select().position(p3)), Direction.DOWN);
        scene.idle(10);
        frame(scene, f2, true);
        crop(scene, util, p3, "minecraft:carrot", 2, 7);
        scene.overlay().showText(90).text("Frames work side by side: every frame touching two ripe parents breeds on its own.")
                .pointAt(util.vector().topOf(f2)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.world().setBlock(util.grid().at(3, 1, 1), Blocks.BEEHIVE.defaultBlockState(), false);
        scene.world().showSection(util.select().position(3, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("Bees nearby pollinate: crosses come more often and stats drift upward a little more.")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().colored(PonderPalette.GREEN);
        scene.idle(100);

        scene.overlay().showText(110).text("Vigor speeds everything up: at 1 a parent joins a cross about half the time, at 10 every time. Breed it up first.")
                .independent(20);
        scene.idle(120);
        scene.overlay().showText(90).text("When you're ready to automate, a Planter Bed breeds, ranks and keeps its best seeds by itself.")
                .independent(20);
        scene.idle(100);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ crossbreeding

    public static void crossbreeding(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("crossbreeding", "Crossbreeding new species");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos p1 = util.grid().at(1, 1, 2), f = util.grid().at(2, 1, 2), p2 = util.grid().at(3, 1, 2);
        BlockPos d = util.grid().at(5, 1, 2), soil = util.grid().at(5, 0, 2);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.idle(10);
        crop(scene, util, p1, "minecraft:wheat", 1, 7);
        crop(scene, util, p2, "minecraft:red_mushroom", 1, 3);
        frame(scene, f, true);
        scene.overlay().showText(90).text("Two different species around a Grafting Frame can cross into a new one.")
                .pointAt(util.vector().topOf(f)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.GREEN, "habitat", util.select().fromTo(2, 0, 2, 2, 1, 2), 100);
        scene.overlay().showText(110).text("Each cross needs its habitat: the light at the frame and the soil under it. Outside it, the child just copies one parent.")
                .pointAt(util.vector().centerOf(f)).placeNearTarget();
        scene.idle(120);
        scene.overlay().showText(130).text("Parents and frame sit side by side, so they share the light. Crops grow at light 9 or more, mushrooms at 12 or less, so this pair crosses in twilight: light 9 to 11.")
                .pointAt(util.vector().topOf(p2)).placeNearTarget().colored(PonderPalette.BLUE);
        scene.idle(140);

        frame(scene, f, false);
        crop(scene, util, f, "cultivar:bonecap", 1, 0);
        sparkle(scene, util, f);
        scene.overlay().showText(80).text("A new species! A chime and a burst of sparkles mark the find.")
                .pointAt(util.vector().topOf(f)).placeNearTarget().attachKeyFrame();
        scene.idle(30);
        grow(scene, util, f, "cultivar:bonecap", 1, 1, 3);
        scene.idle(60);

        scene.overlay().showControls(util.vector().topOf(f), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(LedgerModule.LEDGER.get()));
        scene.idle(10);
        scene.overlay().showText(110).text("Record it with a Botanical Ledger. Once you know both parents and the child, the cross shows up in JEI.")
                .pointAt(util.vector().topOf(f)).placeNearTarget();
        scene.idle(120);

        scene.world().showSection(util.select().position(d), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(100).text("Soil humidity can be nudged to suit a habitat. This coarse dirt starts dry.")
                .pointAt(util.vector().blockSurface(soil, Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.world().setBlock(util.grid().at(6, 0, 2), Blocks.WATER.defaultBlockState(), false);
        scene.overlay().showText(90).text("Water beside it: one step wetter. (Farmland counts its own moisture instead.)")
                .pointAt(util.vector().topOf(6, 0, 2)).placeNearTarget().colored(PonderPalette.BLUE);
        scene.idle(100);
        scene.world().modifyBlock(d, s -> s.setValue(TrellisBlock.CLOCHE, true), false);
        scene.overlay().showText(90).text("A Bell Cloche on the trellis: another step wetter.")
                .pointAt(util.vector().topOf(d)).placeNearTarget().colored(PonderPalette.BLUE);
        scene.idle(100);
        scene.world().setBlock(util.grid().at(5, 0, 1), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), false);
        scene.overlay().showText(100).text("A lit campfire, magma block or fuelled Blaze Burner beside or under the soil: one step drier.")
                .pointAt(util.vector().topOf(5, 0, 1)).placeNearTarget().colored(PonderPalette.RED);
        scene.idle(110);
        scene.overlay().showText(100).text("JEI's Growing page lists the soils each plant accepts, as they are or with these tweaks.")
                .independent(20);
        scene.idle(110);
        scene.overlay().showText(90).text("Stuck on a pairing? A Trial Bed grows test crosses by itself and fills in your Ledger.")
                .independent(20);
        scene.idle(100);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ assaying

    public static void assaying(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("assaying", "Assaying seeds");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos motor = util.grid().at(0, 0, 2), cog = util.grid().at(0, 1, 2);
        BlockPos andesite = util.grid().at(1, 1, 2), brass = util.grid().at(2, 1, 2), chromatic = util.grid().at(3, 1, 2);
        var drive = util.select().position(motor).add(util.select().position(cog));
        scene.world().showSection(drive, Direction.DOWN);
        scene.world().showSection(util.select().position(andesite), Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(drive, 32);
        scene.world().setKineticSpeed(util.select().position(andesite), -32);
        scene.overlay().showText(90).text("The Assayer reads the genome of seeds and cuttings. Its cogwheel takes rotation from a neighboring cog, like a Mechanical Crafter.")
                .pointAt(util.vector().blockSurface(cog, Direction.UP)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().position(brass), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(chromatic), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(brass), 32);
        scene.world().setKineticSpeed(util.select().position(chromatic), -32);
        scene.overlay().showText(100).text("Touching Assayers mesh and join into one unit. Andesite, brass (glazed) and chromatic (glowing) tiers assay 1x, 2x and 4x as fast.")
                .pointAt(util.vector().topOf(brass)).placeNearTarget().attachKeyFrame();
        scene.idle(110);

        scene.overlay().showControls(util.vector().topOf(brass), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(CropModule.SEED.get()));
        scene.idle(10);
        scene.overlay().showText(90).text("Right-click any of them with seeds, or feed one from a funnel or pipe: the whole unit shares the work.")
                .pointAt(util.vector().topOf(brass)).placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90).text("At 32 RPM an andesite Assayer takes 5 seconds per seed. Spin it faster to assay faster.")
                .pointAt(util.vector().centerOf(andesite)).placeNearTarget().attachKeyFrame();
        scene.idle(40);
        scene.world().setKineticSpeed(drive, 128);
        scene.world().setKineticSpeed(util.select().position(andesite).add(util.select().position(chromatic)), -128);
        scene.world().setKineticSpeed(util.select().position(brass), 128);
        scene.effects().rotationSpeedIndicator(cog);
        scene.idle(60);

        ItemStack assayed = new ItemStack(CropModule.SEED.get());
        assayed.set(CoreModule.ASSAYED.get(), net.minecraft.util.Unit.INSTANCE);
        scene.world().createItemEntity(util.vector().topOf(brass), new Vec3(0, 0.25, 0), assayed);
        scene.overlay().showText(100).text("Assayed seeds show their four stats in the tooltip. Sneak-right-click with an empty hand to take them, or pull them out from any side.")
                .pointAt(util.vector().topOf(brass)).placeNearTarget();
        scene.idle(110);
        scene.world().modifyEntities(ItemEntity.class, e -> e.discard());

        scene.overlay().showControls(util.vector().topOf(chromatic), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(LedgerModule.LEDGER.get()));
        scene.idle(10);
        scene.overlay().showText(100).text("A Botanical Ledger in the unit records every species assayed. Sometimes it also maps the family tree: a cross from its ancestry, and the crosses it leads to two generations down.")
                .pointAt(util.vector().topOf(chromatic)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(90).text("Engineer's Goggles show progress and the unit's queue, and a comparator reads how full the outputs are.")
                .pointAt(util.vector().blockSurface(andesite, Direction.NORTH)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ field crops

    /** Plants a field crop (no trellis) at {@code pos} and shows it at a stage. */
    private static void field(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String id, int stage) {
        scene.world().modifyBlockEntity(pos, com.kragnoth.cultivar.content.field.FieldCropBlockEntity.class,
                be -> be.plant().set(genome(id, 1), stage, true));
        scene.world().modifyBlockEntityNBT(util.select().position(pos), com.kragnoth.cultivar.content.field.FieldCropBlockEntity.class,
                tag -> { }, true);
    }

    public static void fieldCrops(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("field_crops", "Planting without a trellis");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos a = util.grid().at(1, 1, 2), b = util.grid().at(2, 1, 2), c = util.grid().at(3, 1, 2);
        scene.overlay().showControls(util.vector().topOf(a.below()), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(Items.WHEAT_SEEDS));
        scene.idle(10);
        for (BlockPos p : new BlockPos[]{a, b, c}) {
            scene.world().setBlock(p, com.kragnoth.cultivar.content.field.FieldCropModule.FIELD_CROP.get().defaultBlockState(), false);
            field(scene, util, p, "minecraft:wheat", 0);
        }
        scene.world().showSection(util.select().fromTo(a, c), Direction.DOWN); // placed blocks only draw once shown
        scene.overlay().showText(90).text("Any seed Cultivar knows can go straight onto farmland, or whatever soil the plant likes. It keeps its genome and stats.")
                .pointAt(util.vector().topOf(b)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        for (int stage = 1; stage <= 7; stage++) {
            for (BlockPos p : new BlockPos[]{a, b, c}) {
                field(scene, util, p, "minecraft:wheat", stage);
            }
            scene.idle(5);
        }
        scene.overlay().showText(90).text("It grows like a normal crop, using its Growth, Gain and Strength, but it never spreads, crosses or gets blight.")
                .pointAt(util.vector().topOf(b)).placeNearTarget();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(a), Pointing.DOWN, 40).rightClick();
        scene.world().createItemEntity(util.vector().topOf(a), new Vec3(0.05, 0.25, 0), new ItemStack(Items.WHEAT));
        field(scene, util, a, "minecraft:wheat", 0);
        scene.overlay().showText(90).text("Right-click it when ripe to harvest and replant. Break it like wheat to get the seed back. Create Harvesters work on it too.")
                .pointAt(util.vector().topOf(a)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.world().modifyEntities(ItemEntity.class, e -> e.discard());
        scene.overlay().showText(80).text("If a seed won't take, a message says why, and its tooltip lists what the plant needs.")
                .pointAt(util.vector().topOf(c)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ cloches

    private static void cloche(SceneBuilder scene, BlockPos pos, com.kragnoth.cultivar.content.cloche.ClocheType type) {
        scene.world().modifyBlock(pos, s -> s.setValue(TrellisBlock.CLOCHE, true).setValue(TrellisBlock.CLOCHE_TYPE, type), false);
    }

    public static void cloches(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("cloches", "Cloches");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos bell = util.grid().at(1, 1, 2), shade = util.grid().at(2, 1, 2), lamp = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(bell, lamp), Direction.DOWN);
        scene.idle(10);
        for (BlockPos p : new BlockPos[]{bell, shade, lamp}) {
            crop(scene, util, p, "minecraft:wheat", 1, 3);
        }
        scene.overlay().showControls(util.vector().topOf(bell), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(com.kragnoth.cultivar.content.cloche.ClocheModule.BELL_CLOCHE.get()));
        scene.idle(10);
        cloche(scene, bell, com.kragnoth.cultivar.content.cloche.ClocheType.BELL);
        scene.overlay().showText(90).text("A Bell Cloche on a single trellis: the crop grows 10% faster, its soil reads one step wetter, and no new blight takes hold.")
                .pointAt(util.vector().topOf(bell)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(shade), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(com.kragnoth.cultivar.content.cloche.ClocheModule.SHADE_CLOCHE.get()));
        scene.idle(10);
        cloche(scene, shade, com.kragnoth.cultivar.content.cloche.ClocheType.SHADE);
        scene.overlay().showText(90).text("A Shade Cloche does the same, and its crop reads light 3 at most: dark-loving plants grow beside bright ones.")
                .pointAt(util.vector().topOf(shade)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(lamp), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(com.kragnoth.cultivar.content.cloche.ClocheModule.LAMP_CLOCHE.get()));
        scene.idle(10);
        cloche(scene, lamp, com.kragnoth.cultivar.content.cloche.ClocheType.LAMP);
        scene.overlay().showText(90).text("A Lamp Cloche lights its own crop to at least 14, day or night, without lighting anything else.")
                .pointAt(util.vector().topOf(lamp)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80).text("Sneak-right-click with an empty hand to take a cloche off. Tall crops can't grow under one.")
                .pointAt(util.vector().topOf(shade)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ olla

    public static void olla(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("olla", "Watering with an Olla");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos pot = util.grid().at(2, 0, 2);
        scene.overlay().showControls(util.vector().topOf(pot), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(Items.WATER_BUCKET));
        scene.idle(10);
        scene.world().modifyBlock(pot, s -> s.setValue(com.kragnoth.cultivar.content.irrigation.OllaBlock.FILLED, true), false);
        scene.overlay().showText(80).text("An Olla is a brick pot. Fill it once with a water bucket or bottle; it never runs dry.")
                .pointAt(util.vector().topOf(pot)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        var field = util.select().fromTo(0, 1, 0, 4, 1, 4);
        scene.world().showSection(field, Direction.DOWN);
        scene.idle(20);
        scene.world().modifyBlocks(field, s -> s.hasProperty(net.minecraft.world.level.block.FarmBlock.MOISTURE)
                ? s.setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7) : s, false);
        scene.overlay().showText(100).text("Bury it under the farmland: farmland within 4 blocks, on its level or the layer above, stays moist like it would beside water.")
                .pointAt(util.vector().topOf(util.grid().at(4, 1, 2))).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(90).text("Nothing flows or spills, and the spot above it can be farmed. Goggles show what it waters; a comparator reads whether it's filled.")
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2))).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ sprinkler

    public static void sprinkler(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("sprinkler", "The Sprinkler");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos head = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(head), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("Mount a Sprinkler on any fluid pipe and feed it water, or fill it with a water bucket.")
                .pointAt(util.vector().topOf(head)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.effects().emitParticles(util.vector().topOf(head),
                scene.effects().simpleParticleEmitter(ParticleTypes.SPLASH, new Vec3(0, 0.3, 0)), 12, 60);
        scene.overlay().showText(100).text("While it has water it gives crops nearby extra growth, now and then a bone-meal pulse, and keeps farmland moist.")
                .pointAt(util.vector().topOf(util.grid().at(4, 0, 2))).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(80).text("A redstone signal switches it off. Use a wrench to turn it to face any side.")
                .pointAt(util.vector().blockSurface(head, Direction.WEST)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ planter beds

    private static void bedSoil(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos), com.kragnoth.cultivar.content.bed.PlanterBedBlockEntity.class,
                tag -> tag.put("Soil", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.FARMLAND.defaultBlockState())), true);
    }

    public static void planterBeds(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("planter_beds", "Planter Beds");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(10);

        var beds = util.select().fromTo(2, 1, 1, 4, 1, 3);
        scene.world().showSection(beds, Direction.DOWN);
        scene.idle(10);
        BlockPos corner = util.grid().at(2, 1, 1), middle = util.grid().at(3, 1, 2);
        scene.overlay().showText(90).text("A Planter Bed is a self-running crop plot. Touching beds join into one plot, up to 64, that shares its stock, water and output.")
                .pointAt(util.vector().topOf(middle)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(middle), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(Items.FARMLAND));
        scene.idle(10);
        for (int x = 2; x <= 4; x++) {
            for (int z = 1; z <= 3; z++) {
                bedSoil(scene, util, util.grid().at(x, 1, z));
            }
        }
        scene.overlay().showText(80).text("Give each bed a soil block, then water: a bucket, or a pipe into any bed of the plot.")
                .pointAt(util.vector().topOf(middle)).placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 2, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(100).text("Feed seeds, fertilizer, trellises and cloches through one bed, by hand, hopper or pipe. The plot places trellises and cloches itself.")
                .pointAt(util.vector().topOf(util.grid().at(1, 2, 1))).placeNearTarget().attachKeyFrame();
        scene.idle(60);
        for (int x = 2; x <= 4; x++) {
            for (int z = 1; z <= 3; z++) {
                BlockPos top = util.grid().at(x, 2, z);
                scene.world().setBlock(top, CropModule.TIMBER_TRELLIS.get().defaultBlockState(), false);
            }
        }
        scene.world().showSection(util.select().fromTo(2, 2, 1, 4, 2, 3), Direction.DOWN);
        scene.idle(10);
        for (int x = 2; x <= 4; x++) {
            for (int z = 1; z <= 3; z++) {
                crop(scene, util, util.grid().at(x, 2, z), "minecraft:wheat", 1, 7);
            }
        }
        scene.idle(40);
        scene.overlay().showText(100).text("It plants its best seeds, grows them blight-free, stacks trellises for tall crops, harvests into its outputs, and swaps in better seeds over time.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 2))).placeNearTarget().attachKeyFrame();
        scene.idle(110);

        frame(scene, util.grid().at(3, 2, 2), true);
        crop(scene, util, util.grid().at(3, 2, 2), null, 1, 0);
        scene.overlay().showText(90).text("Put a grafting frame on a bed and it becomes a breeder: children born there are banked as seeds for the plot.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 2))).placeNearTarget();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(corner), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(com.simibubi.create.AllItems.WRENCH.get()));
        scene.idle(10);
        scene.overlay().showText(100).text("Sneak-right-click with an empty hand to take outputs, then stock, then soil. A wrench switches the plot to dismantle; redstone pauses it.")
                .pointAt(util.vector().topOf(corner)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------------ trial beds

    public static void trialBeds(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("trial_beds", "Trial Beds");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        var beds = util.select().fromTo(1, 1, 1, 3, 1, 3);
        scene.world().showSection(beds, Direction.DOWN);
        scene.idle(10);
        BlockPos middle = util.grid().at(2, 1, 2);
        scene.overlay().showText(90).text("Trial Beds are a research plot. Touching beds join up, and a full 9x9 gets the best discovery odds.")
                .pointAt(util.vector().topOf(middle)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(middle), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(LedgerModule.LEDGER.get()));
        scene.idle(10);
        scene.overlay().showText(80).text("Load seeds of many plants and a Botanical Ledger, by hand or hopper.")
                .pointAt(util.vector().topOf(middle)).placeNearTarget();
        scene.idle(90);

        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                BlockPos top = util.grid().at(x, 2, z);
                boolean frameSquare = ((x + z) & 1) == 1;
                scene.world().setBlock(top, CropModule.TIMBER_TRELLIS.get().defaultBlockState().setValue(TrellisBlock.CROSS, frameSquare), false);
            }
        }
        scene.world().showSection(util.select().fromTo(1, 2, 1, 3, 2, 3), Direction.DOWN);
        scene.idle(10);
        String[] plants = {"minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:wheat"};
        int n = 0;
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (((x + z) & 1) == 0) {
                    crop(scene, util, util.grid().at(x, 2, z), plants[n++ % plants.length], 1, 7);
                }
            }
        }
        scene.overlay().showText(110).text("It grows them on a checkerboard of free trellises and grafting frames, with every habitat limit and all blight removed, and crosses them for real.")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2))).placeNearTarget().attachKeyFrame();
        scene.idle(120);
        sparkle(scene, util, util.grid().at(2, 2, 1));
        scene.overlay().showText(100).text("Every real mutation it finds is recorded in the Ledger, and now and then it discovers an untested cross outright.")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 1))).placeNearTarget();
        scene.idle(110);
        scene.overlay().showText(90).text("Sneak-right-click with an empty hand to take the Ledger back. Redstone pauses the plot so pipes can pull its seeds.")
                .pointAt(util.vector().topOf(middle)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }
}
