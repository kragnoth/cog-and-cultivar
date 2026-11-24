package com.kragnoth.cultivar.content.irrigation;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Holds nothing: it only gives the Olla a Create goggles readout (see {@link OllaBlock#readout}). */
public class OllaBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public OllaBlockEntity(BlockPos pos, BlockState state) {
        super(IrrigationModule.OLLA_BE.get(), pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level == null) {
            return false;
        }
        OllaBlock.readout(level, worldPosition, getBlockState()).forEach(line -> tooltip.add(Component.literal("    ").append(line)));
        return true;
    }
}
