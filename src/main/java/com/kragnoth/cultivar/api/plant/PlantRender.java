package com.kragnoth.cultivar.api.plant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;

/**
 * How a plant looks. {@code textures} are block-atlas sprites (anything under {@code textures/block/}),
 * spread evenly across the growth stages, so 4 textures on an 8-stage plant change every 2 stages.
 * Vanilla crop textures can be reused directly, and resource packs re-skin them automatically.
 *
 * <p>{@code models} instead names one block model per step ({@code cultivar:crop/cactus_stage0} lives at
 * {@code assets/cultivar/models/crop/cactus_stage0.json}). Models crop and arrange vanilla textures by UV, so the
 * active resource pack still supplies the pixels; a pack can also override the model itself. Faces with
 * {@code tintindex} 0 take the stem colour and 1 the biome grass colour, but only when the pack's texture is
 * greyscale (as vanilla's are); pre-coloured pack textures are left untinted.
 *
 * <p>{@code top} (optional, same kind as the main list) is used for the topmost segment of a tall plant,
 * e.g. rice stalks below and panicles on top.
 */
public record PlantRender(List<ResourceLocation> textures, List<ResourceLocation> models, Shape shape, List<ResourceLocation> top) {
    public static final Codec<PlantRender> CODEC = RecordCodecBuilder.<PlantRender>create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("textures", List.of()).forGetter(PlantRender::textures),
            ResourceLocation.CODEC.listOf().optionalFieldOf("models", List.of()).forGetter(PlantRender::models),
            Shape.CODEC.optionalFieldOf("shape", Shape.HASH).forGetter(PlantRender::shape),
            ResourceLocation.CODEC.listOf().optionalFieldOf("top", List.of()).forGetter(PlantRender::top)
    ).apply(i, PlantRender::new)).validate(r -> r.textures().isEmpty() && r.models().isEmpty()
            ? DataResult.error(() -> "render needs \"textures\" or \"models\"") : DataResult.success(r));

    public PlantRender {
        textures = List.copyOf(textures);
        models = List.copyOf(models);
        top = List.copyOf(top);
    }

    /** Models win over textures. Model ids are {@code <ns>:crop/<name>} under {@code assets/<ns>/models/crop/}. */
    public boolean usesModels() {
        return !models.isEmpty();
    }

    public ResourceLocation textureFor(int stage, int stages) {
        return pick(textures, stage, stages);
    }

    public ResourceLocation modelFor(int stage, int stages) {
        return pick(models, stage, stages);
    }

    /**
     * For one segment of a tall plant: {@code fill} is how much of this segment the plant occupies (0..1].
     * A full segment uses the last entry; a partly grown one the entry matching its fill.
     */
    public ResourceLocation forFill(double fill) {
        return forFill(usesModels() ? models : textures, fill);
    }

    /** Like {@link #forFill(double)} for the topmost segment of a tall plant, using {@code top} when given. */
    public ResourceLocation forTopFill(double fill) {
        return forFill(top.isEmpty() ? (usesModels() ? models : textures) : top, fill);
    }

    private static ResourceLocation forFill(List<ResourceLocation> list, double fill) {
        int index = Math.min(list.size() - 1, Math.max(0, (int) Math.ceil(fill * list.size()) - 1));
        return list.get(index);
    }

    private static ResourceLocation pick(List<ResourceLocation> list, int stage, int stages) {
        int index = Math.min(list.size() - 1, stage * list.size() / Math.max(1, stages));
        return list.get(Math.max(0, index));
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
