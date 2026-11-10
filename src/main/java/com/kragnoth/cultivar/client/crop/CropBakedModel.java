package com.kragnoth.cultivar.client.crop;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.PlantRender;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps the trellis block model and adds the plant (and weed) as extra quads baked into the chunk mesh.
 * No block-entity renderer, so a field of crops costs nothing per frame; meshes rebuild only when a crop
 * changes stage. Quads are cached per (sprite, shape) and the cache dies with the model on resource reload.
 */
public class CropBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Visual> VISUAL = new ModelProperty<>();

    /** What to draw inside the frame; either part may be null. */
    private record Visual(@Nullable ResourceLocation plant, PlantRender.Shape shape, boolean model, @Nullable ResourceLocation weed) {}

    private record Key(ResourceLocation texture, PlantRender.Shape shape) {}

    private final Map<Key, List<BakedQuad>> cache = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, List<BakedQuad>> modelCache = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Boolean> greyscale = new ConcurrentHashMap<>();

    public CropBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        // Segments of a stacked trellis draw their share of the plant rooted at the base.
        if (!(CropBlockEntity.resolve(level, pos).orElse(null) instanceof CropBlockEntity crop)) {
            return modelData;
        }
        int segment = pos.getY() - crop.getBlockPos().getY();
        ResourceLocation plantTex = null;
        PlantRender.Shape shape = PlantRender.Shape.HASH;
        boolean model = false;
        Genome genome = crop.genome();
        if (genome != null) {
            Plant plant = genome.species().value();
            if (plant.render().isPresent()) {
                PlantRender render = plant.render().get();
                model = render.usesModels();
                shape = render.shape();
                if (plant.height() <= 1) {
                    if (segment == 0) {
                        plantTex = model ? render.modelFor(crop.stage(), plant.stages()) : render.textureFor(crop.stage(), plant.stages());
                    }
                } else {
                    double fill = (crop.stage() + 1) * plant.height() / (double) plant.stages() - segment;
                    if (fill > 0) {
                        plantTex = render.forFill(Math.min(1.0, fill));
                    }
                }
            }
        }
        ResourceLocation weedTex = null;
        if (crop.weed() != null && segment == 0) {
            Weed weed = crop.weed().value();
            if (!weed.textures().isEmpty()) {
                int i = Math.min(weed.textures().size() - 1, crop.weedStage() * weed.textures().size() / Math.max(1, weed.stages()));
                weedTex = weed.textures().get(i);
            }
        }
        if (plantTex == null && weedTex == null) {
            return modelData;
        }
        return modelData.derive().with(VISUAL, new Visual(plantTex, shape, model, weedTex)).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        Visual visual = data.get(VISUAL);
        if (visual == null || side != null || (renderType != null && renderType != RenderType.cutout())) {
            return base;
        }
        List<BakedQuad> out = new ArrayList<>(base);
        if (visual.plant() != null && visual.model()) {
            out.addAll(modelCache.computeIfAbsent(visual.plant(), this::bakeModel));
        } else if (visual.plant() != null) {
            out.addAll(cache.computeIfAbsent(new Key(visual.plant(), visual.shape()), CropBakedModel::bake));
        }
        if (visual.weed() != null) {
            out.addAll(cache.computeIfAbsent(new Key(visual.weed(), PlantRender.Shape.HASH), CropBakedModel::bake));
        }
        return out;
    }

    // ---------------------------------------------------------------- crop models

    /** Model ids registered by {@link CropClientModule}: everything under {@code models/crop/}. */
    public static ModelResourceLocation modelLocation(ResourceLocation id) {
        return ModelResourceLocation.standalone(id);
    }

    private List<BakedQuad> bakeModel(ResourceLocation id) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLocation(id));
        RandomSource rand = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>();
        for (BakedQuad q : model.getQuads(null, null, rand, ModelData.EMPTY, null)) {
            quads.add(q);
        }
        for (Direction d : Direction.values()) {
            quads.addAll(model.getQuads(null, d, rand, ModelData.EMPTY, null));
        }
        // Drop the tint where the pack already coloured the texture, so it isn't tinted twice.
        quads.replaceAll(q -> q.isTinted() && !isGreyscale(q.getSprite())
                ? new BakedQuad(q.getVertices(), -1, q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion())
                : q);
        return List.copyOf(quads);
    }

    private boolean isGreyscale(TextureAtlasSprite sprite) {
        return greyscale.computeIfAbsent(sprite.contents().name(), name -> {
            ResourceLocation file = name.withPath(p -> "textures/" + p + ".png");
            var resource = Minecraft.getInstance().getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                return true;
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                long spread = 0, count = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int abgr = image.getPixelRGBA(x, y);
                        if ((abgr >>> 24) < 16) {
                            continue;
                        }
                        int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
                        spread += Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                        count++;
                    }
                }
                return count == 0 || spread / count < 16;
            } catch (IOException e) {
                return true;
            }
        });
    }

    // ---------------------------------------------------------------- quad building

    private static List<BakedQuad> bake(Key key) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(key.texture());
        List<BakedQuad> quads = new ArrayList<>();
        if (key.shape() == PlantRender.Shape.HASH) {
            for (float c : new float[]{0.25f, 0.75f}) {
                plane(quads, sprite, c, 0, c, 1, Direction.EAST);   // x = c, spans z
                plane(quads, sprite, 0, c, 1, c, Direction.SOUTH);  // z = c, spans x
            }
        } else {
            plane(quads, sprite, 0.15f, 0.15f, 0.85f, 0.85f, Direction.EAST);
            plane(quads, sprite, 0.15f, 0.85f, 0.85f, 0.15f, Direction.EAST);
        }
        return List.copyOf(quads);
    }

    /** A vertical, double-sided plane from (x0, z0) to (x1, z1), full block height. */
    private static void plane(List<BakedQuad> out, TextureAtlasSprite sprite, float x0, float z0, float x1, float z1, Direction facing) {
        out.add(quad(sprite, facing, x0, z0, x1, z1));
        out.add(quad(sprite, facing.getOpposite(), x1, z1, x0, z0));
    }

    private static BakedQuad quad(TextureAtlasSprite sprite, Direction facing, float x0, float z0, float x1, float z1) {
        QuadBakingVertexConsumer b = new QuadBakingVertexConsumer();
        b.setSprite(sprite);
        b.setDirection(facing);
        b.setShade(true);
        b.setHasAmbientOcclusion(false);
        float nx = facing.getStepX(), ny = facing.getStepY(), nz = facing.getStepZ();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        // Counter-clockwise from the front: top-left, bottom-left, bottom-right, top-right.
        vertex(b, x0, 1, z0, u0, v0, nx, ny, nz);
        vertex(b, x0, 0, z0, u0, v1, nx, ny, nz);
        vertex(b, x1, 0, z1, u1, v1, nx, ny, nz);
        vertex(b, x1, 1, z1, u1, v0, nx, ny, nz);
        return b.bakeQuad();
    }

    private static void vertex(QuadBakingVertexConsumer b, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        b.addVertex(x, y, z).setColor(-1).setUv(u, v).setUv2(0, 0).setNormal(nx, ny, nz);
    }
}
