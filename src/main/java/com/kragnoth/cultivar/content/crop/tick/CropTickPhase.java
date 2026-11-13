package com.kragnoth.cultivar.content.crop.tick;

/**
 * One step of a crop's random tick. Phases run in priority order (see {@link CropTickPhases}); the first one to return
 * {@code true} consumes the tick and the rest are skipped. Return {@code false} to let later phases run.
 *
 * <p>Built-ins: weeds (100, trellis only), breeding on a grafting frame (200, trellis only), growth (300, any plant
 * holder). Phases that work on {@link CropTickContext#plant()} apply to every kind of holder; use
 * {@link CropTickContext#trellis()} for trellis-only behaviour. A new mechanic (seasons, pests, temperature damage...)
 * is a new phase registered from its module, not an edit to a block entity. Phases run on the server on the crop's
 * random tick, so keep them cheap; anything that scans many blocks belongs on {@code ServerWorkQueue}.
 */
@FunctionalInterface
public interface CropTickPhase {
    /** @return true to stop the pipeline for this tick */
    boolean tick(CropTickContext ctx);
}
