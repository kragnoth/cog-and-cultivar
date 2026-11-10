package com.kragnoth.cultivar.client.assay;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.assay.AssayingEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Spins the drum and the half-shaft below it with the network's rotation. */
public class AssayingEngineRenderer extends KineticBlockEntityRenderer<AssayingEngineBlockEntity> {
    public static final PartialModel DRUM = PartialModel.of(Cultivar.id("block/assaying_engine/drum"));

    public AssayingEngineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AssayingEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        renderRotatingBuffer(be, CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN), ms, vb, light);
        renderRotatingBuffer(be, CachedBuffers.partial(DRUM, state), ms, vb, light);
    }
}
