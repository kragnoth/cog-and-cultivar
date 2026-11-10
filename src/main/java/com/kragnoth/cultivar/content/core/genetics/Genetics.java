package com.kragnoth.cultivar.content.core.genetics;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.AllelePair;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Stat helpers: how an allele pair turns into the single value gameplay uses. */
public final class Genetics {
    private Genetics() {}

    /** How a diploid pair is expressed. Server config picks one (default MAX). */
    public enum Expression implements StringRepresentable {
        MAX, MIN, MEAN;

        public static final Codec<Expression> CODEC = StringRepresentable.fromEnum(Expression::values);

        public int apply(AllelePair pair) {
            return switch (this) {
                case MAX -> pair.dominant();
                case MIN -> pair.recessive();
                case MEAN -> (pair.dominant() + pair.recessive()) / 2;
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The server-configured expression. SERVER configs are synced to clients on join, so both sides agree
     * (important: satchel sorting runs on both sides during inventory clicks). Falls back to MAX before
     * configs are loaded (e.g. tooltips on the title screen).
     */
    public static Expression current() {
        return CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.STAT_EXPRESSION.get() : Expression.MAX;
    }

    public static int expressed(Genome genome, ResourceKey<Stat> stat) {
        return expressed(genome, stat, current());
    }

    public static int expressed(Genome genome, ResourceKey<Stat> stat, Expression expression) {
        return clamp(stat, expression.apply(genome.get(stat)));
    }

    public static int max(ResourceKey<Stat> stat) {
        Stat def = CultivarRegistries.STAT.get(stat);
        return def == null ? 10 : def.defaultMax();
    }

    public static int min(ResourceKey<Stat> stat) {
        Stat def = CultivarRegistries.STAT.get(stat);
        return def == null ? 1 : def.defaultMin();
    }

    public static int clamp(ResourceKey<Stat> stat, int value) {
        return Math.max(min(stat), Math.min(max(stat), value));
    }
}
