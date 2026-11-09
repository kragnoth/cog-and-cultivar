package com.kragnoth.cultivar.api.plant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;

/**
 * How a plant looks. {@code textures} are block-atlas sprites (anything under {@code textures/block/}),
 * spread evenly across the growth stages, so 4 textures on an 8-stage plant change every 2 stages.
 * Vanilla crop textures can be reused directly, and resource packs re-skin them automatically.
 */
public record PlantRender(List<ResourceLocation> textures, Shape shape) {
    public static final Codec<PlantRender> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("textures").forGetter(PlantRender::textures),
            Shape.CODEC.optionalFieldOf("shape", Shape.HASH).forGetter(PlantRender::shape)
    ).apply(i, PlantRender::new));

    public PlantRender {
        textures = List.copyOf(textures);
    }

    public ResourceLocation textureFor(int stage, int stages) {
        int index = Math.min(textures.size() - 1, stage * textures.size() / Math.max(1, stages));
        return textures.get(Math.max(0, index));
    }

    public enum Shape implements StringRepresentable {
        /** Four planes in a # pattern, like vanilla wheat. */
        HASH,
        /** Two diagonal planes, like flowers and saplings. */
        CROSS;

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
