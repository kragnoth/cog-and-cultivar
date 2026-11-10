package com.kragnoth.cultivar.client.bed;

import com.kragnoth.cultivar.content.bed.PlanterBedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the bed's soil: the soil block's own top face (so the active resource pack supplies it), lowered 2px to sit
 * inside the casing rim. Baked into the chunk mesh; no block entity renderer.
 */
public class PlanterBedBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BlockState> SOIL = new ModelProperty<>();
    private static final float DROP = 2f / 16f;
    private static final int STRIDE = 8; // ints per vertex in DefaultVertexFormat.BLOCK; y is int #1

    private final Map<BlockState, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public PlanterBedBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        if (level.getBlockEntity(pos) instanceof PlanterBedBlockEntity bed && bed.soil() != null) {
            return modelData.derive().with(SOIL, bed.soil()).build();
        }
        return modelData;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        BlockState soil = data.get(SOIL);
        if (side != null || soil == null || (renderType != null && renderType != RenderType.solid())) {
            return base;
        }
        List<BakedQuad> out = new ArrayList<>(base);
        out.addAll(cache.computeIfAbsent(soil, this::soilTop));
        return out;
    }

    private List<BakedQuad> soilTop(BlockState soil) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(soil);
        List<BakedQuad> out = new ArrayList<>();
        for (BakedQuad q : model.getQuads(soil, Direction.UP, RandomSource.create(42L), ModelData.EMPTY, null)) {
            int[] v = q.getVertices().clone();
            for (int i = 0; i < 4; i++) {
                v[i * STRIDE + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[i * STRIDE + 1]) - DROP);
            }
            out.add(new BakedQuad(v, q.getTintIndex(), Direction.UP, q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
        }
        return out;
    }
}
