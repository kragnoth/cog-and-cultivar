package com.kragnoth.cultivar.core.work;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Time-sliced server work. Anything that scans many blocks (conservatory detection, trough networks)
 * submits a {@link Task} here instead of doing it in one tick. Each dimension gets
 * {@code workBudgetPerTick} steps per tick, shared round-robin across tasks.
 *
 * <p>Cost when idle: one map lookup per level tick.
 */
public final class ServerWorkQueue {
    /** One resumable job. {@link #step} does a small bounded unit of work and returns true when finished. */
    @FunctionalInterface
    public interface Task {
        boolean step(ServerLevel level);
    }

    private static final Map<ResourceKey<Level>, ArrayDeque<Task>> QUEUES = new HashMap<>();

    private ServerWorkQueue() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(ServerWorkQueue::onLevelTick);
        gameBus.addListener(ServerWorkQueue::onLevelUnload);
        gameBus.addListener(ServerWorkQueue::onServerStopped);
    }

    /** Queue a task for this level. Must be called on the server thread. */
    public static void submit(ServerLevel level, Task task) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("ServerWorkQueue.submit called off the server thread");
        }
        QUEUES.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).addLast(task);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ArrayDeque<Task> queue = QUEUES.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        int budget = CultivarServerConfig.WORK_BUDGET_PER_TICK.get();
        while (budget-- > 0 && !queue.isEmpty()) {
            Task task = queue.pollFirst();
            boolean done;
            try {
                done = task.step(level);
            } catch (RuntimeException e) {
                Cultivar.LOGGER.error("Deferred task {} failed in {}; dropping it", task, level.dimension().location(), e);
                done = true;
            }
            if (!done) {
                queue.addLast(task);
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            QUEUES.remove(level.dimension());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        QUEUES.clear();
    }
}
