package com.kragnoth.cultivar.client.assay;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.PlantRender;
import com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Spins a Create cogwheel in the Assayer's gear window (like the Mechanical Crafter's, on the vertical axis, so it meshes
 * with neighbouring Assayers and cogwheels) and the half-shaft below. The seed under test is drawn as a sprout in the
 * soil pit, using that plant's own textures: it grows from the first stage toward the second as the assay progresses,
 * and starts over with the next seed.
 */
public class AssayingEngineRenderer extends KineticBlockEntityRenderer<AssayingEngineBlockEntity> {
    /** Soil surface in the pit, and the sprout's footprint (inside the 6 px window). */
    private static final float SOIL = 11 / 16f, HALF = 2.6f / 16f, MIN_H = 1.5f / 16f, MAX_H = 5.2f / 16f;

    public AssayingEngineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AssayingEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
        renderRotatingBuffer(be, CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, state), ms, vb, light);
        renderRotatingBuffer(be, CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN), ms, vb, light);
        sprout(be, ms, buffer, light);
    }

    private static void sprout(AssayingEngineBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        ItemStack seed = be.inventory.getStackInSlot(AssayingEngineBlockEntity.SEED_IN);
        Genome genome = seed.get(CoreModule.GENOME.get());
        if (genome == null) {
            return;
        }
        Plant plant = genome.species().value();
        PlantRender render = plant.render().orElse(null);
        float t = Math.min(1f, be.progress() / AssayingEngineBlockEntity.WORK);
        int stages = plant.stages();
        ResourceLocation tex = render == null || render.textures().isEmpty() ? null
                : render.textureFor(t < 0.5f ? 0 : Math.min(1, stages - 1), stages);
        if (tex == null) { // model-only plants (melons, cocoa): the seed itself lies in the soil
            ms.pushPose();
            ms.translate(0.5, SOIL + 0.01, 0.5);
            ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            ms.scale(0.3f, 0.3f, 0.3f);
            Minecraft.getInstance().getItemRenderer().renderStatic(seed, net.minecraft.world.item.ItemDisplayContext.FIXED,
                    light, OverlayTexture.NO_OVERLAY, ms, buffer, be.getLevel(), (int) be.getBlockPos().asLong());
            ms.popPose();
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
        float h = MIN_H + (MAX_H - MIN_H) * t;
        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        Matrix4f m = ms.last().pose();
        // Two crossed planes, like a crop, the texture squeezed to the sprout's height.
        quad(vc, m, sprite, 0.5f - HALF, 0.5f - HALF, 0.5f + HALF, 0.5f + HALF, h, light);
        quad(vc, m, sprite, 0.5f - HALF, 0.5f + HALF, 0.5f + HALF, 0.5f - HALF, h, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, TextureAtlasSprite s, float x0, float z0, float x1, float z1,
                             float h, int light) {
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        float y0 = SOIL, y1 = SOIL + h;
        // Front and back, so the plane shows from both sides.
        vertex(vc, m, x0, y0, z0, u0, v1, light);
        vertex(vc, m, x1, y0, z1, u1, v1, light);
        vertex(vc, m, x1, y1, z1, u1, v0, light);
        vertex(vc, m, x0, y1, z0, u0, v0, light);
        vertex(vc, m, x0, y1, z0, u0, v0, light);
        vertex(vc, m, x1, y1, z1, u1, v0, light);
        vertex(vc, m, x1, y0, z1, u1, v1, light);
        vertex(vc, m, x0, y0, z0, u0, v1, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(0, 1, 0);
    }
}
