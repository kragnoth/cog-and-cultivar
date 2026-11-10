package com.kragnoth.cultivar.content.core.influence;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.influence.CropInfluence;
import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds every {@link CropInfluence} acting on a crop and combines them (spec §3.15).
 *
 * <p>Source search, cheapest first:
 * <ul>
 *   <li>Blocks with a POI type (beehives, bee nests, workstations...) come from the POI index, never a block scan.</li>
 *   <li>Other blocks are scanned directly only when the entry's volume is tiny (≤ 27 blocks, e.g. a cloche
 *       with radius 0). Large non-POI entries are ignored, so a datapack can't make crops scan thousands of blocks.</li>
 * </ul>
 * Within one entry only the strongest source counts; different entries combine under the config caps.
 * Callers cache the result (see {@code CropBlockEntity}).
 */
public final class InfluenceLookup {
    private static final int MAX_SCAN_VOLUME = 27;

    private InfluenceLookup() {}

    public static InfluenceEffects compute(ServerLevel level, BlockPos crop) {
        if (!CultivarServerConfig.INFLUENCE_ENABLED.get()) {
            return InfluenceEffects.NONE;
        }
        List<InfluenceEffects> found = new ArrayList<>();
        for (CropInfluence entry : level.registryAccess().registryOrThrow(CultivarRegistries.CROP_INFLUENCE)) {
            double best = strongest(level, crop, entry);
            if (best > 0) {
                found.add(entry.effects().scaled(best));
            }
        }
        if (found.isEmpty()) {
            return InfluenceEffects.NONE;
        }
        return InfluenceMath.combine(found, InfluenceMath.configuredCaps());
    }

    private static double strongest(ServerLevel level, BlockPos crop, CropInfluence entry) {
        double best = 0;
        int r = entry.radius();
        int h = entry.height();
        // 1) POI-indexed sources.
        PoiManager pois = level.getPoiManager();
        var records = pois.getInSquare(type -> true, crop, r, PoiManager.Occupancy.ANY).toList();
        for (var record : records) {
            BlockPos pos = record.getPos();
            if (Math.abs(pos.getY() - crop.getY()) > h) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (entry.matches(state)) {
                best = Math.max(best, strength(level, pos, state, entry));
            }
        }
        // 2) Tiny direct scan for non-POI sources.
        int volume = (2 * r + 1) * (2 * r + 1) * (2 * h + 1);
        if (volume <= MAX_SCAN_VOLUME) {
            for (BlockPos pos : BlockPos.betweenClosed(crop.offset(-r, -h, -r), crop.offset(r, h, r))) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (PoiTypes.forState(state).isEmpty() && entry.matches(state)) {
                    best = Math.max(best, strength(level, pos.immutable(), state, entry));
                }
            }
        }
        return best;
    }

    private static double strength(ServerLevel level, BlockPos pos, BlockState state, CropInfluence entry) {
        return entry.scaling().map(s -> s.scale(level, pos, state)).orElse(1.0);
    }
}
