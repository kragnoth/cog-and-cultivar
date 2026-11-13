package com.kragnoth.cultivar.content.cloche;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Which cloche is fitted over a trellis ({@code cloche_type}, only meaningful while {@code cloche=true}). Every kind is
 * a Bell Cloche underneath (humidity +1, the {@code bell_cloche} influence); Shade and Lamp also override the light the
 * crop under them reads, without changing the world's light, so a dark parent can sit beside a bright one.
 */
public enum ClocheType implements StringRepresentable {
    BELL, SHADE, LAMP;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Item item() {
        return switch (this) {
            case BELL -> ClocheModule.BELL_CLOCHE.get();
            case SHADE -> ClocheModule.SHADE_CLOCHE.get();
            case LAMP -> ClocheModule.LAMP_CLOCHE.get();
        };
    }

    /** The cloche fitted on a trellis state, or null if none. */
    public static ClocheType fitted(BlockState state) {
        if (!state.hasProperty(com.kragnoth.cultivar.content.crop.TrellisBlock.CLOCHE)
                || !state.getValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CLOCHE)) {
            return null;
        }
        return state.getValue(com.kragnoth.cultivar.content.crop.TrellisBlock.CLOCHE_TYPE);
    }
}
