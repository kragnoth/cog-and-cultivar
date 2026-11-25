package com.kragnoth.cultivar.client.bed;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Planter and Trial Beds: a Create cogwheel spinning in the crafter-style window of the bed's body (vertical axis, so
 * it meshes with neighbouring beds and cogwheels), and the half-shaft below. Same look as the Assayer's.
 */
public class BedCogRenderer<T extends KineticBlockEntity> extends KineticBlockEntityRenderer<T> {
    public BedCogRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
        renderRotatingBuffer(be, CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, state), ms, vb, light);
        renderRotatingBuffer(be, CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN), ms, vb, light);
    }
}
