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
import net.minecraft.client.resources.model.BakedModel;
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
    private record Visual(@Nullable ResourceLocation plant, PlantRender.Shape shape, @Nullable ResourceLocation weed) {}

    private record Key(ResourceLocation texture, PlantRender.Shape shape) {}

    private final Map<Key, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public CropBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (!(level.getBlockEntity(pos) instanceof CropBlockEntity crop)) {
            return modelData;
        }
        ResourceLocation plantTex = null;
        PlantRender.Shape shape = PlantRender.Shape.HASH;
        Genome genome = crop.genome();
        if (genome != null) {
            Plant plant = genome.species().value();
            if (plant.render().isPresent()) {
                PlantRender render = plant.render().get();
                plantTex = render.textureFor(crop.stage(), plant.stages());
                shape = render.shape();
            }
        }
        ResourceLocation weedTex = null;
        if (crop.weed() != null) {
            Weed weed = crop.weed().value();
            if (!weed.textures().isEmpty()) {
                int i = Math.min(weed.textures().size() - 1, crop.weedStage() * weed.textures().size() / Math.max(1, weed.stages()));
                weedTex = weed.textures().get(i);
            }
        }
        if (plantTex == null && weedTex == null) {
            return modelData;
        }
        return modelData.derive().with(VISUAL, new Visual(plantTex, shape, weedTex)).build();
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
        if (visual.plant() != null) {
            out.addAll(cache.computeIfAbsent(new Key(visual.plant(), visual.shape()), CropBakedModel::bake));
        }
        if (visual.weed() != null) {
            out.addAll(cache.computeIfAbsent(new Key(visual.weed(), PlantRender.Shape.HASH), CropBakedModel::bake));
        }
        return out;
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
