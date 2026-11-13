package com.kragnoth.cultivar.content.irrigation;

import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Olla: an unglazed brick pot buried in the field. Fill it once with a water bucket (or bottle) and it seeps water
 * into the soil around it, keeping vanilla farmland moist within the usual 4-block reach, forever, like a water
 * source does. Unlike a water source it is a solid block: bury it under the farmland layer and the spot above stays
 * plantable, and there is no water to spill or flow.
 */
public class OllaBlock extends Block {
    public static final MapCodec<OllaBlock> CODEC = simpleCodec(OllaBlock::new);
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public OllaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(FILLED)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack empty;
        if (stack.is(Items.WATER_BUCKET)) {
            empty = new ItemStack(Items.BUCKET);
        } else if (stack.is(Items.POTION) && stack.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
            empty = new ItemStack(Items.GLASS_BOTTLE);
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, empty));
            level.setBlock(pos, state.setValue(FILLED, true), UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    public static int radius() {
        return CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.OLLA_RADIUS.get() : 4;
    }

    /**
     * Whether a filled olla keeps the farmland at {@code farmland} moist: within the radius horizontally, on the
     * farmland's own level or the layer just under it. Same box size as vanilla's water check, so the same cost.
     */
    public static boolean moistens(LevelReader level, BlockPos farmland) {
        int r = radius();
        for (BlockPos p : BlockPos.betweenClosed(farmland.offset(-r, -1, -r), farmland.offset(r, 0, r))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof OllaBlock && state.getValue(FILLED)) {
                return true;
            }
        }
        return false;
    }
}
