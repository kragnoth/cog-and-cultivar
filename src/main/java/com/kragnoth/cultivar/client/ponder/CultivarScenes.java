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

        scene.overlay().showText(100).text("A Grafting Frame beside a single ripe crop slowly copies it. The higher the parent's Vigour, the sooner it happens.")
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
        scene.overlay().showText(140).text("Gain: more produce per harvest. Growth: ripens faster. Strength: copes with worse soil and light and fends off blight; at 10 it grows on any soil. Vigour: crosses more readily and passes on better stats.")
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

        scene.overlay().showText(80).text("Stats stay hidden until the seed is read in an Assaying Engine.")
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

        scene.overlay().showText(110).text("Vigour speeds everything up: at 1 a parent joins a cross about half the time, at 10 every time. Breed it up first.")
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

        BlockPos engine = util.grid().at(2, 1, 2), shaft = util.grid().at(2, 0, 2);
        var kinetics = util.select().position(engine).add(util.select().position(shaft));
        scene.world().showSection(util.select().position(engine), Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(kinetics, 32);
        scene.overlay().showText(90).text("The Assaying Engine reads the genome of seeds and cuttings. It runs on rotation from below.")
                .pointAt(util.vector().blockSurface(shaft, Direction.WEST)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(engine), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(CropModule.SEED.get()));
        scene.idle(10);
        scene.overlay().showText(90).text("Right-click it with seeds, or feed it from a funnel, chute, hopper or pipe.")
                .pointAt(util.vector().topOf(engine)).placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90).text("Each seed takes 5 seconds at 32 RPM. Spin it faster to assay faster.")
                .pointAt(util.vector().centerOf(engine)).placeNearTarget().attachKeyFrame();
        scene.idle(40);
        scene.world().setKineticSpeed(kinetics, 128);
        scene.effects().rotationSpeedIndicator(engine);
        scene.idle(60);

        ItemStack assayed = new ItemStack(CropModule.SEED.get());
        assayed.set(CoreModule.ASSAYED.get(), net.minecraft.util.Unit.INSTANCE);
        scene.world().createItemEntity(util.vector().topOf(engine), new Vec3(0, 0.25, 0), assayed);
        scene.overlay().showText(100).text("Assayed seeds show their four stats in the tooltip. Take them with an empty hand, or pull them out from any side.")
                .pointAt(util.vector().topOf(engine)).placeNearTarget();
        scene.idle(110);
        scene.world().modifyEntities(ItemEntity.class, e -> e.discard());

        scene.overlay().showControls(util.vector().topOf(engine), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(LedgerModule.LEDGER.get()));
        scene.idle(10);
        scene.overlay().showText(100).text("Put a Botanical Ledger inside and it records every species the engine assays. Sneak with an empty hand to take it back.")
                .pointAt(util.vector().topOf(engine)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(90).text("Engineer's Goggles show its progress, and a comparator reads how full the output is.")
                .pointAt(util.vector().blockSurface(engine, Direction.NORTH)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }
}
