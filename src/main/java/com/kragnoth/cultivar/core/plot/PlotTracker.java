package com.kragnoth.cultivar.core.plot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared "plot" behaviour for block entities that join up with same-type neighbours (Planter Beds, Trial Beds, and
 * whatever machine comes next): a flood fill of sideways-touching members, cached once per game tick and shared by every
 * member, plus a plot-wide redstone check cached the same way.
 *
 * <p>Each member owns one tracker and hands out how to reach another member's tracker ({@code trackerOf}), so one fill
 * serves the whole plot. The fill is capped at {@code maxMembers}.
 */
public final class PlotTracker<T extends BlockEntity> {
    private final T self;
    private final Class<T> type;
    private final int maxMembers;
    private final Function<T, PlotTracker<T>> trackerOf;

    private List<T> members;
    private long membersTick = Long.MIN_VALUE;
    private boolean powered;
    private long poweredTick = Long.MIN_VALUE;

    public PlotTracker(T self, Class<T> type, int maxMembers, Function<T, PlotTracker<T>> trackerOf) {
        this.self = self;
        this.type = type;
        this.maxMembers = maxMembers;
        this.trackerOf = trackerOf;
    }

    /** The plot this member belongs to, recomputed at most once per game tick. */
    public List<T> members() {
        long now = now();
        if (members == null || membersTick != now) {
            List<T> found = flood(self, type, maxMembers);
            for (T member : found) {
                PlotTracker<T> t = trackerOf.apply(member);
                t.members = found;
                t.membersTick = now;
            }
        }
        return members;
    }

    /** True if any member of the plot has a redstone signal. */
    public boolean isPowered() {
        long now = now();
        if (poweredTick != now) {
            List<T> plot = members();
            boolean any = false;
            for (T member : plot) {
                Level level = member.getLevel();
                if (level != null && level.hasNeighborSignal(member.getBlockPos())) {
                    any = true;
                    break;
                }
            }
            for (T member : plot) {
                PlotTracker<T> t = trackerOf.apply(member);
                t.powered = any;
                t.poweredTick = now;
            }
        }
        return powered;
    }

    /** A neighbour changed (maybe redstone): re-check the plot's power on the next query. */
    public void invalidatePower() {
        for (T member : members()) {
            trackerOf.apply(member).poweredTick = Long.MIN_VALUE;
        }
    }

    /** Uncached flood fill over loaded, sideways-touching block entities of {@code type}, capped at {@code max}. */
    public static <T extends BlockEntity> List<T> flood(T origin, Class<T> type, int max) {
        Level level = origin.getLevel();
        if (level == null) {
            return List.of(origin);
        }
        List<T> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.getBlockPos());
        seen.add(origin.getBlockPos());
        while (!queue.isEmpty() && out.size() < max) {
            BlockPos p = queue.poll();
            if (!level.isLoaded(p) || !type.isInstance(level.getBlockEntity(p))) {
                continue;
            }
            out.add(type.cast(level.getBlockEntity(p)));
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return out;
    }

    private long now() {
        Level level = self.getLevel();
        return level == null ? 0 : level.getGameTime();
    }
}
