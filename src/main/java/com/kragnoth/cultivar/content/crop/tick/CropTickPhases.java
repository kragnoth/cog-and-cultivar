package com.kragnoth.cultivar.content.crop.tick;

import com.kragnoth.cultivar.Cultivar;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ordered list of {@link CropTickPhase}s a crop runs on its random tick. Lower priority runs first; equal
 * priorities keep registration order. Registering an id that already exists replaces that phase, so an addon can
 * swap a built-in. Register from a module's {@code onConstruct}, before any world ticks. Any block entity that owns a
 * {@code PlantCore} runs the same pipeline with {@link #run}.
 */
public final class CropTickPhases {
    public static final int WEEDS = 100;
    public static final int BREEDING = 200;
    public static final int GROWTH = 300;

    public record Entry(ResourceLocation id, int priority, CropTickPhase phase) {}

    private static final List<Entry> PHASES = new CopyOnWriteArrayList<>();

    static {
        register(Cultivar.id("weeds"), WEEDS, CropTickPhases::weeds);
        register(Cultivar.id("breeding"), BREEDING, CropTickPhases::breeding);
        register(Cultivar.id("growth"), GROWTH, CropTickPhases::growth);
    }

    private CropTickPhases() {}

    public static synchronized void register(ResourceLocation id, int priority, CropTickPhase phase) {
        PHASES.removeIf(e -> e.id().equals(id));
        PHASES.add(new Entry(id, priority, phase));
        PHASES.sort(Comparator.comparingInt(Entry::priority));
    }

    /** Snapshot of the current phases in run order (for debugging and docs). */
    public static List<Entry> all() {
        return List.copyOf(PHASES);
    }

    /** Runs the phases for one random tick, stopping at the first that consumes it. */
    public static void run(CropTickContext ctx) {
        for (Entry entry : PHASES) {
            if (entry.phase().tick(ctx)) {
                return;
            }
        }
    }

    // ---- built-in phases: trellis-only ones delegate to the entity's own (tested) logic ----

    private static boolean weeds(CropTickContext ctx) {
        return ctx.trellis().map(crop -> !ctx.onIdealHost() && crop.tickWeeds(ctx.level(), ctx.random(), ctx.fx())).orElse(false);
    }

    private static boolean breeding(CropTickContext ctx) {
        return ctx.trellis().map(crop -> {
            if (!crop.isCross()) {
                return false;
            }
            crop.tickBreeding(ctx.level(), ctx.random(), ctx.fx());
            return true;
        }).orElse(false);
    }

    private static boolean growth(CropTickContext ctx) {
        if (!ctx.plant().hasPlant()) {
            return false;
        }
        ctx.plant().tickGrowth(ctx.random(), ctx.onIdealHost());
        return true;
    }
}
