package com.kragnoth.cultivar.api.crop;

import com.kragnoth.cultivar.api.genetics.Genome;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of one planted Cultivar crop, whatever holds it (a trellis today; field crops next). Readouts,
 * Create display sources, Jade and addons should read crops through this rather than a concrete block entity.
 */
public interface CropView {
    /** The planted genome, or null if nothing is planted. */
    @Nullable
    Genome genome();

    int stage();

    boolean isMature();

    /** Whether the genome may be shown (assayed, or the server doesn't require assaying). */
    boolean statsKnown();

    /** Where the plant is rooted (the base of a tall plant). */
    BlockPos rootPos();

    /** The soil block feeding it. */
    default BlockPos soilPos() {
        return rootPos().below();
    }
}
