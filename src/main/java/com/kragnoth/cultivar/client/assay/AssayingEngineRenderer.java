package com.kragnoth.cultivar.client.assay;

import com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Spins a Create cogwheel in the Assayer's gear window (like the Mechanical Crafter's, but on the vertical axis, so it
 * meshes with neighbouring Assayers and cogwheels), the half-shaft below, and lays the seed being assayed on the
 * farmland, turning slowly while it works.
 */
public class AssayingEngineRenderer extends KineticBlockEntityRenderer<AssayingEngineBlockEntity> {
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

        ItemStack seed = be.inventory.getStackInSlot(AssayingEngineBlockEntity.SEED_IN);
        if (seed.isEmpty() || be.getLevel() == null) {
            return;
        }
        ms.pushPose();
        ms.translate(0.5, 1.03, 0.5);
        if (be.progress() > 0) {
            float t = (be.getLevel().getGameTime() + partialTicks) * 2f;
            ms.mulPose(Axis.YP.rotationDegrees(t % 360));
        }
        ms.mulPose(Axis.XP.rotationDegrees(90));
        ms.scale(0.3f, 0.3f, 0.3f);
        Minecraft.getInstance().getItemRenderer().renderStatic(seed, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                be.getLevel(), (int) be.getBlockPos().asLong());
        ms.popPose();
    }
}
