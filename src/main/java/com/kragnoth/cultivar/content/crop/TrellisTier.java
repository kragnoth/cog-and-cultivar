package com.kragnoth.cultivar.content.crop;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Trellis materials (spec §3.2). Brass and blackened steel arrive with Create recipes later.
 *
 * @param strengthBonus added to the crop's strength for tolerance checks
 */
public enum TrellisTier implements StringRepresentable {
    TIMBER(0, false),
    IRON(0, true),
    BRASS(1, true),
    BLACKENED_STEEL(1, true);

    public static final Codec<TrellisTier> CODEC = StringRepresentable.fromEnum(TrellisTier::values);

    private final int strengthBonus;
    private final boolean fireproof;

    TrellisTier(int strengthBonus, boolean fireproof) {
        this.strengthBonus = strengthBonus;
        this.fireproof = fireproof;
    }

    public int strengthBonus() {
        return strengthBonus;
    }

    public boolean fireproof() {
        return fireproof;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
