package com.kragnoth.cultivar.content.devtest;

import com.kragnoth.cultivar.Cultivar;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs Cultivar's own game tests (the ones CI runs on a dedicated server) inside a live world: the player's modpack,
 * with every other mod loaded, so conflicts show up too. Built on vanilla's game test framework, which a normal
 * (production) game doesn't tick or register: this class registers Cultivar's test methods and ticks the runner itself.
 *
 * <p>Tests build their plots in a grid next to the player and flatten the ground there, so the command asks for
 * confirmation and {@link #clear} removes the plots afterwards.
 */
final class LiveTestRunner {
    private static final Type HOLDER = Type.getType(GameTestHolder.class);
    private static boolean registered;
    @Nullable
    private static GameTestRunner running;
    @Nullable
    private static MultipleTestTracker tracker;
    private static final List<BoundingBox> plots = new ArrayList<>();
    @Nullable
    private static ServerLevel plotLevel;
    private static long savedDayTime = -1;

    private LiveTestRunner() {}

    /** Registers Cultivar's @GameTestHolder classes once (NeoForge only does this in dev runs). */
    @SuppressWarnings("deprecation")
    private static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;
        if (GameTestHooks.isGametestEnabled()) {
            return; // dev run: NeoForge already registered them
        }
        var file = ModList.get().getModFileById(Cultivar.MOD_ID);
        if (file == null) {
            return;
        }
        file.getFile().getScanResult().getAnnotations().stream()
                .filter(a -> HOLDER.equals(a.annotationType()))
                .map(a -> a.clazz().getClassName())
                .distinct()
                .forEach(name -> {
                    try {
                        Class<?> c = Class.forName(name, true, LiveTestRunner.class.getClassLoader());
                        for (Method m : c.getDeclaredMethods()) {
                            GameTestRegistry.register(m, Set.of(Cultivar.MOD_ID));
                        }
                    } catch (ClassNotFoundException e) {
                        Cultivar.LOGGER.error("Could not load game test class {}", name, e);
                    }
                });
    }

    /** Cultivar's tests, optionally only those whose class or method name contains {@code filter}. */
    static List<TestFunction> tests(@Nullable String filter) {
        ensureRegistered();
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        return GameTestRegistry.getAllTestFunctions().stream()
                .filter(t -> t.structureName().startsWith(Cultivar.MOD_ID + ":"))
                .filter(t -> t.testName().contains(f) || t.batchName().toLowerCase(Locale.ROOT).contains(f))
                .sorted(Comparator.comparing(TestFunction::testName))
                .toList();
    }

    static boolean isRunning() {
        return tracker != null && !tracker.isDone();
    }

    static int run(CommandSourceStack source, @Nullable String filter) {
        if (isRunning()) {
            source.sendFailure(Component.literal("Tests are already running: wait for the summary, or /test cultivarclear to stop."));
            return 0;
        }
        List<TestFunction> functions = tests(filter);
        if (functions.isEmpty()) {
            source.sendFailure(Component.literal("No Cultivar tests match \"" + filter + "\"."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        clear(source, false);
        // Run at noon so sky-lit tests don't fail because it's night where the player is; the time goes back after.
        savedDayTime = level.getDayTime();
        level.setDayTime(6000);

        BlockPos at = BlockPos.containing(source.getPosition());
        BlockPos corner = new BlockPos(at.getX() + 4, level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, at).getY() + 1, at.getZ() + 4);
        List<GameTestInfo> infos = functions.stream()
                .map(f -> new GameTestInfo(f, Rotation.NONE, level, RetryOptions.noRetries()))
                .toList();
        GameTestRunner runner = GameTestRunner.Builder.fromInfo(infos, level)
                .newStructureSpawner(new StructureGridSpawner(corner, 8, false))
                .build();
        MultipleTestTracker t = new MultipleTestTracker(runner.getTestInfos());
        t.addListener(new Reporter(source, t, level));
        running = runner;
        tracker = t;
        plotLevel = level;
        source.sendSuccess(() -> Component.literal("Running " + infos.size() + " Cultivar tests south-east of you...").withStyle(ChatFormatting.AQUA), false);
        runner.start();
        return infos.size();
    }

    /** Production servers don't tick game tests: tick them while a live run is active. */
    static void serverTick() {
        if (running != null && !GameTestHooks.isGametestEnabled()) {
            GameTestTicker.SINGLETON.tick();
        }
    }

    /** Stops a run and removes every test plot it built. */
    static int clear(CommandSourceStack source, boolean say) {
        GameTestTicker.SINGLETON.clear();
        running = null;
        tracker = null;
        restoreTime();
        int n = plots.size();
        if (plotLevel != null) {
            for (BoundingBox box : plots) {
                StructureUtils.clearSpaceForStructure(box, plotLevel);
            }
            GameTestRunner.clearMarkers(plotLevel);
        }
        plots.clear();
        if (say) {
            source.sendSuccess(() -> Component.literal(n == 0 ? "No test plots to clear." : "Cleared " + n + " test plots."), false);
        }
        return n;
    }

    private static void restoreTime() {
        if (savedDayTime >= 0 && plotLevel != null) {
            plotLevel.setDayTime(savedDayTime);
        }
        savedDayTime = -1;
    }

    /** Per-test chat lines (failures with their message and a click to teleport there) and the final summary. */
    private record Reporter(CommandSourceStack source, MultipleTestTracker tracker, ServerLevel level) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo info) {
            plots.add(StructureUtils.getStructureBoundingBox(info.getStructureBlockEntity()));
        }

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            finishIfDone();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            BlockPos p = info.getStructureBlockPos();
            Throwable error = info.getError();
            MutableComponent line = Component.literal("✗ " + info.getTestName()).withStyle(ChatFormatting.RED)
                    .append(Component.literal(": " + (error == null ? "failed" : error.getMessage())).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" [go]").withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/tp @s " + p.getX() + " " + (p.getY() + 2) + " " + (p.getZ() - 2)))));
            source.sendSystemMessage(line);
            finishIfDone();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            tracker.addTestToTrack(newTest);
        }

        private void finishIfDone() {
            if (!tracker.isDone()) {
                return;
            }
            restoreTime();
            running = null;
            int total = tracker.getTotalCount(), failed = tracker.getFailedRequiredCount() + tracker.getFailedOptionalCount();
            source.sendSystemMessage(failed == 0
                    ? Component.literal("✓ All " + total + " Cultivar tests passed in this world.").withStyle(ChatFormatting.GREEN)
                    : Component.literal(failed + " of " + total + " Cultivar tests failed (listed above). Plots stay for a look;"
                            + " /test cultivarclear removes them.").withStyle(ChatFormatting.RED));
        }
    }
}
