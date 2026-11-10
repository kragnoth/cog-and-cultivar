package com.kragnoth.cultivar.client.dev;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import com.kragnoth.cultivar.client.ledger.LedgerScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * Dev-only screenshot harness. Active only when the {@code CULTIVAR_SCENES} environment variable is set:
 * creates a flat creative world, builds review scenes, shoots each camera and quits.
 * Screenshots land in {@code run/screenshots/}. Usage (fish):
 * <pre>env CULTIVAR_SCENES=1 ./gradlew runClient</pre>
 * Headless: wrap with {@code xvfb-run -s "-screen 0 1280x720x24"}.
 */
public final class SceneShooter {
    /** One camera: where to stand, where to look, and the screenshot name. */
    private record Shot(String name, double x, double y, double z, float yaw, float pitch) {}

    // Scene is built around the origin of a default flat world (grass at y=-61).
    private static final int Y = -60;
    // Display pieces sit on z = -6, spaced 3 apart; close-up cameras look north (yaw 180) so +X is to the right.
    private static final int DZ = -6;
    private static final List<Shot> ALL_SHOTS = List.of(
            new Shot("cloche_01_wheat_closeup", 0.5, Y + 1.0, DZ + 2.6, 180f, 12f),
            new Shot("cloche_02_empty_closeup", 3.5, Y + 1.0, DZ + 2.6, 180f, 12f),
            new Shot("cloche_03_iron_carrot_closeup", 6.5, Y + 1.0, DZ + 2.6, 180f, 12f),
            new Shot("cloche_04_with_vs_without", 11.0, Y + 1.4, DZ + 3.4, 180f, 15f),
            new Shot("cloche_05_high_angle", 0.5, Y + 2.8, DZ + 2.2, 180f, 45f),
            new Shot("cloche_06_field", 3.5, Y + 4.5, 12.0, 180f, 28f),
            new Shot("ledger_01_index", 4.5, Y + 1.4, 4.6, 180f, 20f),
            new Shot("ledger_02_carrot", 4.5, Y + 1.4, 4.6, 180f, 20f),
            new Shot("assay_01_engine", 15.2, Y + 2.4, DZ + 2.6, 160f, 20f),
            new Shot("assay_02_goggles", 14.5, Y + 2.3, DZ + 3.0, 180f, 18f),
            new Shot("crops_01_row", 6.5, Y + 2.6, -16.5, 180f, 22f),
            new Shot("crops_02_close_left", 3.0, Y + 1.6, -17.8, 180f, 18f),
            new Shot("crops_03_close_right", 10.0, Y + 1.6, -17.8, 180f, 18f),
            new Shot("tall_01_stacks", 3.5, Y + 2.8, -21.0, 180f, 12f),
            new Shot("ruin_01_greenhouse", 33.5, Y + 6.5, -15.0, 25f, 22f),
            new Shot("ruin_02_inside", 28.5, Y + 1.6, -7.2, 0f, 8f),
            new Shot("ruin_03_lean_to", 41.0, Y + 4.0, -6.5, 0f, 28f),
            new Shot("sprinkler_01", 54.2, Y + 2.6, 3.6, 155f, 24f),
            new Shot("bed_01_row", 66.5, Y + 3.0, 4.5, 150f, 26f),
            new Shot("trial_01_plot", 74.0, Y + 4.5, 7.5, 160f, 32f));

    /** CULTIVAR_SCENES=1 shoots everything; any other value shoots only names starting with it (e.g. "tall"). */
    private static final List<Shot> SHOTS = selectShots();

    private static List<Shot> selectShots() {
        String filter = System.getenv("CULTIVAR_SCENES");
        return filter == null || filter.equals("1") ? ALL_SHOTS
                : ALL_SHOTS.stream().filter(s -> s.name().startsWith(filter)).toList();
    }

    private static int tick = -1;
    private static boolean worldRequested;

    private SceneShooter() {}

    public static void registerIfEnabled() {
        if (System.getenv("CULTIVAR_SCENES") == null) {
            return;
        }
        Cultivar.LOGGER.info("[scenes] Screenshot harness enabled");
        NeoForge.EVENT_BUS.addListener(SceneShooter::onScreen);
        NeoForge.EVENT_BUS.addListener(SceneShooter::onTick);
    }

    private static void onScreen(ScreenEvent.Init.Post event) {
        if (worldRequested || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = true;
        mc.options.renderDistance().set(4);
        mc.options.fov().set(55);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        String name = "cultivar_scenes_" + System.currentTimeMillis();
        mc.createWorldOpenFlows().createFreshLevel(name,
                new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(12345L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                event.getScreen());
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        tick++;
        if (tick == 20) {
            server.execute(() -> build(server.overworld()));
        }
        for (int i = 0; i < SHOTS.size(); i++) {
            Shot shot = SHOTS.get(i);
            int base = 120 + i * 100;
            if (tick == base) {
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                    boolean goggles = shot.name().startsWith("assay_02");
                    boolean creative = goggles;
                    if (goggles) {
                        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "goggles"))));
                    }
                    p.setGameMode(creative ? GameType.CREATIVE : GameType.SPECTATOR);
                    p.teleportTo(server.overworld(), shot.x(), shot.y() - p.getEyeHeight(), shot.z(), shot.yaw(), shot.pitch()); // shot.y is eye height
                });
            }
            if (tick == base + 40 || tick == base + 70) {
                // The server teleport doesn't reliably carry rotation to the client camera; set it directly.
                mc.player.setYRot(shot.yaw());
                mc.player.setXRot(shot.pitch());
                mc.player.setYHeadRot(shot.yaw());
                mc.player.yRotO = shot.yaw();
                mc.player.xRotO = shot.pitch();
            }
            if (tick == base + 75) {
                mc.options.hideGui = !shot.name().startsWith("ledger")
                        && !shot.name().startsWith("assay_02");
                mc.setScreen(null);
                if (shot.name().startsWith("ledger")) {
                    ItemStack ledger = new ItemStack(LedgerModule.LEDGER.get());
                    for (String id : new String[]{"wheat", "potato", "carrot"}) {
                        LedgerItem.record(ledger, ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace(id)));
                    }
                    LedgerScreen screen = new LedgerScreen(ledger);
                    mc.setScreen(screen);
                    screen.goToPage(shot.name().endsWith("index") ? 0 : 1);
                }
            }
            if (tick == base + 80) {
                Screenshot.grab(mc.gameDirectory, shot.name() + ".png", mc.getMainRenderTarget(),
                        msg -> Cultivar.LOGGER.info("[scenes] {}", msg.getString()));
            }
        }
        if (tick == 120 + SHOTS.size() * 100 + 20) {
            Cultivar.LOGGER.info("[scenes] Done, stopping client");
            mc.stop();
        }
    }

    // ---------------------------------------------------------------- scene

    private static void build(ServerLevel level) {
        level.setDayTime(6000);
        Holder<Plant> wheat = plant(level, "wheat");
        Holder<Plant> carrot = plant(level, "carrot");
        Holder<Plant> potato = plant(level, "potato");
        Holder<Plant> beetroot = plant(level, "beetroot");
        Holder<Weed> rot = level.registryAccess().registryOrThrow(CultivarRegistries.WEED)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.WEED, Cultivar.id("rust_rot")));
        BlockState timber = CropModule.TIMBER_TRELLIS.get().defaultBlockState();
        BlockState iron = CropModule.IRON_TRELLIS.get().defaultBlockState();

        // Row 1 (z=0): closeup pair, then cloche vs no cloche for each tier and stage.
        crop(level, 0, 0, timber.setValue(TrellisBlock.CLOCHE, true), wheat, 7);
        crop(level, 1, 0, timber, wheat, 7);
        crop(level, 2, 0, iron.setValue(TrellisBlock.CLOCHE, true), carrot, 3);
        crop(level, 3, 0, iron, carrot, 3);
        crop(level, 4, 0, timber.setValue(TrellisBlock.CLOCHE, true), beetroot, 1);
        crop(level, 5, 0, timber.setValue(TrellisBlock.CLOCHE, true), null, 0);

        // Row 2 (z=2): a small breeding cell with a grafting frame, blight, and a cloche'd potato.
        crop(level, 1, 2, timber, wheat, 7);
        crop(level, 2, 2, timber.setValue(TrellisBlock.CROSS, true), null, 0);
        crop(level, 3, 2, timber, potato, 7);
        crop(level, 4, 2, iron.setValue(TrellisBlock.CLOCHE, true), potato, 5);
        crop(level, 5, 2, timber, carrot, 2);
        if (level.getBlockEntity(new BlockPos(5, Y, 2)) instanceof CropBlockEntity c) {
            c.setWeed(rot, 3);
        }
        level.setBlock(new BlockPos(7, Y, 2), Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5), Block.UPDATE_ALL);

        // Isolated display pieces for close-ups.
        crop(level, 0, DZ, timber.setValue(TrellisBlock.CLOCHE, true), wheat, 7);
        crop(level, 3, DZ, timber.setValue(TrellisBlock.CLOCHE, true), null, 0);
        crop(level, 6, DZ, iron.setValue(TrellisBlock.CLOCHE, true), carrot, 3);
        crop(level, 10, DZ, timber.setValue(TrellisBlock.CLOCHE, true), wheat, 5);
        crop(level, 11, DZ, timber, wheat, 5);

        // Assaying Engine on a creative motor, loaded with seeds so it runs.
        BlockState motor = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("create", "creative_motor")).defaultBlockState();
        motor = motor.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP);
        level.setBlock(new BlockPos(14, Y, DZ), motor, Block.UPDATE_ALL);
        BlockPos engine = new BlockPos(14, Y + 1, DZ);
        level.setBlock(engine, com.kragnoth.cultivar.content.assay.AssayModule.ENGINE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(engine) instanceof com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity be) {
            be.inventory.setStackInSlot(com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity.SEED_IN,
                    com.kragnoth.cultivar.content.crop.CultivarSeedItem.of(com.kragnoth.cultivar.api.genetics.Genome.base(carrot)).copyWithCount(64));
        }

        // Every vanilla crop on the trellis: mature on the back row, mid-growth in front.
        String[] crops = {"pumpkin", "melon", "sugar_cane", "bamboo", "cactus", "cocoa", "sweet_berries",
                "glow_berries", "nether_wart", "brown_mushroom", "red_mushroom", "torchflower", "pitcher_plant"};
        for (int i = 0; i < crops.length; i++) {
            Holder<Plant> p = plant(level, crops[i]);
            int stages = p.value().stages();
            crop(level, i, -22, timber, p, stages - 1);
            crop(level, i, -20, iron, p, stages / 2);
        }

        // Tall crops on stacked trellises: cane (mature, half-grown, on one trellis), bamboo, cactus.
        stack(level, 0, -28, Blocks.MUD, 3, plant(level, "sugar_cane"), 5);
        stack(level, 2, -28, Blocks.MUD, 3, plant(level, "sugar_cane"), 3);
        stack(level, 3, -28, Blocks.MUD, 1, plant(level, "sugar_cane"), 5);
        stack(level, 5, -28, Blocks.MUD, 4, plant(level, "bamboo"), 7);
        stack(level, 7, -28, Blocks.SAND, 3, plant(level, "cactus"), 5);

        // Derelict conservatories, placed with their decay processor as worldgen would.
        ruin(level, "greenhouse", new BlockPos(24, Y - 1, -8));
        ruin(level, "lean_to", new BlockPos(38, Y - 1, -1));

        // Sprinklers: on top of a Create pipe, on its side and hanging under it, fed with water.
        BlockPos pipe = new BlockPos(52, Y + 1, 0);
        var pipeBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "fluid_pipe"));
        for (int dx = -1; dx <= 1; dx++) {
            level.setBlock(pipe.offset(dx, 0, 0), pipeBlock.defaultBlockState(), Block.UPDATE_ALL);
        }
        var sprinkler = com.kragnoth.cultivar.content.irrigation.IrrigationModule.SPRINKLER.get().defaultBlockState()
                .setValue(com.kragnoth.cultivar.content.irrigation.SprinklerBlock.ACTIVE, true);
        var F = com.kragnoth.cultivar.content.irrigation.SprinklerBlock.FACING;
        level.setBlock(pipe.offset(-1, 1, 0), sprinkler.setValue(F, net.minecraft.core.Direction.UP), Block.UPDATE_ALL);
        level.setBlock(pipe.offset(0, 0, 1), sprinkler.setValue(F, net.minecraft.core.Direction.SOUTH), Block.UPDATE_ALL);
        level.setBlock(pipe.offset(1, -1, 0), sprinkler.setValue(F, net.minecraft.core.Direction.DOWN), Block.UPDATE_ALL);

        // Planter beds: a touching row with different soils, crops planted in some, one cloche.
        Block[] soils = {Blocks.FARMLAND, Blocks.PODZOL, Blocks.MUD, Blocks.SAND, Blocks.SOUL_SAND, Blocks.MOSS_BLOCK};
        String[] bedCrops = {"wheat", "carrot", "sugar_cane", "cactus", "nether_wart", null};
        for (int i = 0; i < soils.length; i++) {
            BlockPos bp = new BlockPos(61 + i, Y, 0);
            level.setBlock(bp, com.kragnoth.cultivar.content.bed.PlanterBedModule.BED.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(bp) instanceof com.kragnoth.cultivar.content.bed.PlanterBedBlockEntity bed) {
                bed.setSoil(soils[i].defaultBlockState());
            }
            if (bedCrops[i] != null) {
                level.setBlock(bp.above(), timber.setValue(TrellisBlock.CLOCHE, i == 1), Block.UPDATE_ALL);
                if (level.getBlockEntity(bp.above()) instanceof CropBlockEntity c) {
                    Holder<Plant> p = plant(level, bedCrops[i]);
                    c.plant(Genome.base(p), p.value().matureStage());
                    c.clampToSpace();
                }
            }
        }

        // A 5x5 trial plot, stocked and fast-forwarded so its checkerboard of crops and frames is grown in.
        java.util.List<com.kragnoth.cultivar.content.trial.TrialBedBlockEntity> trial = new java.util.ArrayList<>();
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                BlockPos tp = new BlockPos(70 + dx, Y, dz);
                level.setBlock(tp, com.kragnoth.cultivar.content.trial.TrialModule.TRIAL_BED.get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(tp) instanceof com.kragnoth.cultivar.content.trial.TrialBedBlockEntity tb) {
                    trial.add(tb);
                }
            }
        }
        net.minecraft.world.item.Item[] stock = {net.minecraft.world.item.Items.WHEAT_SEEDS, net.minecraft.world.item.Items.CARROT,
                net.minecraft.world.item.Items.POTATO, net.minecraft.world.item.Items.BEETROOT_SEEDS, net.minecraft.world.item.Items.SWEET_BERRIES};
        for (int i = 0; i < stock.length; i++) {
            trial.get(i).inventory.setStackInSlot(0, new net.minecraft.world.item.ItemStack(stock[i], 64));
        }
        var rng = level.random;
        for (int cycle = 0; cycle < 300; cycle++) {
            for (var tb : trial) {
                tb.work(level, rng);
                BlockPos up = tb.getBlockPos().above();
                if (cycle % 2 == 0) {
                    level.getBlockState(up).randomTick(level, up, rng);
                }
            }
        }

        // A wider field for the establishing shot.
        for (int x = 0; x < 8; x++) {
            for (int z = 4; z < 8; z++) {
                boolean cloche = (x + z) % 3 == 0;
                crop(level, x, z, (x % 2 == 0 ? timber : iron).setValue(TrellisBlock.CLOCHE, cloche), x < 4 ? wheat : carrot, (x + z) % 8);
            }
        }
    }

    private static void ruin(ServerLevel level, String piece, BlockPos at) {
        var manager = level.getServer().getStructureManager();
        manager.get(com.kragnoth.cultivar.Cultivar.id("derelict_conservatory/" + piece)).ifPresent(t -> {
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PROCESSOR_LIST)
                    .getOptional(com.kragnoth.cultivar.Cultivar.id("derelict_decay"))
                    .ifPresent(list -> list.list().forEach(settings::addProcessor));
            t.placeInWorld(level, at, at, settings, level.random, Block.UPDATE_ALL);
        });
    }

    private static void stack(ServerLevel level, int x, int z, Block soil, int height, Holder<Plant> plant, int stage) {
        BlockPos base = new BlockPos(x, Y, z);
        level.setBlock(base.below(), soil.defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < height; i++) {
            level.setBlock(base.above(i), CropModule.TIMBER_TRELLIS.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(base) instanceof CropBlockEntity crop) {
            crop.plant(Genome.base(plant), stage);
            crop.clampToSpace();
        }
    }

    private static Holder<Plant> plant(ServerLevel level, String id) {
        return level.registryAccess().registryOrThrow(CultivarRegistries.PLANT)
                .getHolderOrThrow(ResourceKey.create(CultivarRegistries.PLANT, ResourceLocation.withDefaultNamespace(id)));
    }

    private static void crop(ServerLevel level, int x, int z, BlockState trellis, Holder<Plant> plant, int stage) {
        BlockPos pos = new BlockPos(x, Y, z);
        level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, trellis, Block.UPDATE_ALL);
        if (plant != null && level.getBlockEntity(pos) instanceof CropBlockEntity crop) {
            crop.plant(Genome.base(plant), stage);
        }
    }
}
