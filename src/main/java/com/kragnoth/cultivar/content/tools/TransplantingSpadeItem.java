package com.kragnoth.cultivar.content.tools;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Lifts a living plant (genome + growth stage) from a trellis and sets it down on an empty one. */
public class TransplantingSpadeItem extends Item {
    public record Held(Genome genome, int stage) {
        public static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
                Genome.CODEC.fieldOf("genome").forGetter(Held::genome),
                Codec.INT.fieldOf("stage").forGetter(Held::stage)
        ).apply(i, Held::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                Genome.STREAM_CODEC, Held::genome, ByteBufCodecs.VAR_INT, Held::stage, Held::new);
    }

    public TransplantingSpadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(CropBlockEntity.resolve(level, ctx.getClickedPos()).orElse(null) instanceof CropBlockEntity crop)
                || ctx.getLevel().getBlockState(ctx.getClickedPos()).getValue(TrellisBlock.CROSS)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = ctx.getItemInHand();
        Held held = stack.get(ToolsModule.SPADE_PLANT.get());
        if (held == null && crop.hasPlant()) {
            if (!level.isClientSide()) {
                crop.uproot().ifPresent(u -> stack.set(ToolsModule.SPADE_PLANT.get(), new Held(u.genome(), u.stage())));
                level.playSound(null, ctx.getClickedPos(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (held != null && !crop.hasPlant()) {
            if (!level.isClientSide() && crop.plant(held.genome(), held.stage())) {
                stack.remove(ToolsModule.SPADE_PLANT.get());
                if (ctx.getPlayer() != null) {
                    stack.hurtAndBreak(1, ctx.getPlayer(), net.minecraft.world.entity.LivingEntity.getSlotForHand(ctx.getHand()));
                }
                level.playSound(null, ctx.getClickedPos(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Held held = stack.get(ToolsModule.SPADE_PLANT.get());
        if (held != null) {
            tooltip.add(Component.translatable("item.cultivar.transplanting_spade.holding", CultivarSeedItem.plantName(held.genome()))
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
