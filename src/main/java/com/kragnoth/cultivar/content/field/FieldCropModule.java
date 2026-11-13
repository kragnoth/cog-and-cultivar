package com.kragnoth.cultivar.content.field;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Field crops (roadmap phase A): Cultivar seeds planted straight on soil, no trellis. Decorative and practical:
 * they grow and use Strength and Gain, but never breed, spread or blight. Planting is a right-click with a Cultivar
 * seed on the top of a soil block (deployers too); see {@link FieldPlanting}.
 */
public final class FieldCropModule implements CultivarModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cultivar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cultivar.MOD_ID);

    public static final DeferredBlock<FieldCropBlock> FIELD_CROP = BLOCKS.register("field_crop", () -> new FieldCropBlock(
            BlockBehaviour.Properties.of().noCollission().noOcclusion().instabreak().randomTicks()
                    .sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FieldCropBlockEntity>> FIELD_CROP_BE =
            BLOCK_ENTITIES.register("field_crop", () -> BlockEntityType.Builder.of(FieldCropBlockEntity::new, FIELD_CROP.get()).build(null));

    @Override
    public String id() {
        return "field_crops";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(FieldCropModule::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getFace() != Direction.UP) {
            return;
        }
        FieldPlanting.Result result = FieldPlanting.tryPlant(event.getLevel(), event.getPos(), event.getItemStack(), event.getEntity());
        if (result != FieldPlanting.Result.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result == FieldPlanting.Result.PLANTED
                    ? InteractionResult.sidedSuccess(event.getLevel().isClientSide()) : InteractionResult.CONSUME);
        }
    }
}
