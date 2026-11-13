package com.kragnoth.cultivar.content.field;

import com.kragnoth.cultivar.api.crop.CropView;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.crop.CropInfo;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import com.kragnoth.cultivar.content.crop.tick.CropTickContext;
import com.kragnoth.cultivar.content.crop.tick.CropTickPhases;
import com.kragnoth.cultivar.api.influence.InfluenceEffects;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Cultivar plant growing straight on soil, no trellis: a {@link PlantCore} and nothing else. It grows, uses
 * Strength (tolerance, Hardy) and Gain (yield) like a trellis crop, but never breeds, spreads or gets blight.
 * The block's {@code age} (0–7) mirrors growth so other mods and vanilla crop logic can read it.
 */
public class FieldCropBlockEntity extends BlockEntity implements CropView, IHaveGoggleInformation {
    private final PlantCore plant = new PlantCore(new PlantCore.Host() {
        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos rootPos() {
            return worldPosition;
        }

        @Override
        public int blocksAvailable() {
            return FieldCropBlockEntity.this.blocksAvailable();
        }

        @Override
        public void changed() {
            FieldCropBlockEntity.this.changed();
        }
    });
    /** Set while this entity adds/removes its own segments, so their removal doesn't cut the plant. */
    private boolean syncingSegments;

    public FieldCropBlockEntity(BlockPos pos, BlockState state) {
        super(FieldCropModule.FIELD_CROP_BE.get(), pos, state);
    }

    public PlantCore plant() {
        return plant;
    }

    // ---------------------------------------------------------------- CropView

    @Override
    @Nullable
    public Genome genome() {
        return plant.genome();
    }

    @Override
    public int stage() {
        return plant.stage();
    }

    @Override
    public boolean isMature() {
        return plant.isMature();
    }

    @Override
    public boolean statsKnown() {
        return plant.statsKnown();
    }

    @Override
    public BlockPos rootPos() {
        return worldPosition;
    }

    // ---------------------------------------------------------------- tall plants

    /** The base plus free space (air or this plant's own segments) above it, up to the plant's height. */
    public int blocksAvailable() {
        if (level == null || !plant.hasPlant()) {
            return 1;
        }
        int height = Math.min(plant.species().height(), FieldCropBlock.MAX_STACK);
        int n = 1;
        for (BlockPos p = worldPosition.above(); n < height; p = p.above(), n++) {
            BlockState s = level.getBlockState(p);
            boolean ours = s.getBlock() instanceof FieldCropBlock && s.getValue(FieldCropBlock.UPPER);
            if (!ours && !s.isAir()) {
                break;
            }
        }
        return n;
    }

    /**
     * An upper segment at {@code segment} (1 = just above the base) was broken: the plant is cut down to fit below
     * it, yielding a product roll per segment cut if it was ripe, like cutting cane.
     */
    public void cutAt(int segment, java.util.function.Consumer<net.minecraft.world.item.ItemStack> drops) {
        if (syncingSegments || !plant.hasPlant() || level == null) {
            return;
        }
        var species = plant.species();
        int stage = plant.stage();
        if (species.blocksAt(stage) <= segment) {
            return;
        }
        int target = stage;
        while (target > 0 && species.blocksAt(target) > segment) {
            target--;
        }
        if (plant.isMature()) {
            for (int i = 0; i < species.blocksAt(stage) - segment; i++) {
                plant.rollProducts(drops, level.random);
            }
        }
        plant.setStage(target);
    }

    /** Places or removes upper segments so the stack is as tall as the plant. Server side. */
    private void syncSegments() {
        int needed = plant.hasPlant() ? Math.min(plant.species().blocksAt(plant.stage()), FieldCropBlock.MAX_STACK) : 1;
        syncingSegments = true;
        try {
            BlockPos p = worldPosition.above();
            for (int i = 1; i < FieldCropBlock.MAX_STACK; i++, p = p.above()) {
                BlockState s = level.getBlockState(p);
                boolean ours = s.getBlock() instanceof FieldCropBlock && s.getValue(FieldCropBlock.UPPER);
                if (i < needed && s.isAir()) {
                    level.setBlock(p, FieldCropModule.FIELD_CROP.get().defaultBlockState().setValue(FieldCropBlock.UPPER, true), Block.UPDATE_ALL);
                } else if (i >= needed && ours) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                } else if (!ours && i >= needed) {
                    break;
                }
            }
        } finally {
            syncingSegments = false;
        }
    }

    // ---------------------------------------------------------------- ticking

    /** The shared crop tick steps; weeds and breeding are trellis-only steps, so only growth runs here. */
    public void randomTick(ServerLevel level, RandomSource random) {
        CropTickPhases.run(new CropTickContext(this, plant, level, random, InfluenceEffects.NONE));
    }

    // ---------------------------------------------------------------- persistence & sync

    private void changed() {
        setChanged();
        if (level == null || level.isClientSide()) {
            return;
        }
        syncSegments();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof FieldCropBlock) {
            int age = FieldCropBlock.ageFor(plant);
            if (state.getValue(FieldCropBlock.AGE) != age) {
                level.setBlock(worldPosition, state.setValue(FieldCropBlock.AGE, age), Block.UPDATE_CLIENTS);
                return;
            }
        }
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        plant.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        plant.load(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        rerenderOnClient();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        rerenderOnClient();
    }

    private void rerenderOnClient() {
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            BlockPos p = worldPosition.above(); // tall plants are drawn by their segments too
            for (int i = 1; i < FieldCropBlock.MAX_STACK && level.getBlockState(p).getBlock() instanceof FieldCropBlock; i++, p = p.above()) {
                level.sendBlockUpdated(p, level.getBlockState(p), level.getBlockState(p), Block.UPDATE_IMMEDIATE);
            }
        }
    }

    /** Create Engineer's Goggles readout: the same lines as a trellis crop. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level == null || !plant.hasPlant()) {
            return false;
        }
        CropInfo.lines(CropInfo.Subject.simple(plant, level, worldPosition, getBlockState()))
                .forEach(line -> tooltip.add(Component.literal("    ").append(line)));
        return true;
    }
}
