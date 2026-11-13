package com.kragnoth.cultivar.content.tools;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A clone snipped from a living plant (Pruning Shears or a knife): carries the exact genome. Planted on an empty
 * trellis it takes with chance (Strength + 1) / 10 (always at 9+) and starts at stage 1 for a head start;
 * a cutting that doesn't take is lost. Not a seed: it can't go in the Seed Satchel or be composted as one.
 */
public class CuttingItem extends Item {
    /** Stage a cutting that takes starts at. */
    public static final int START_STAGE = 1;

    public CuttingItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Genome genome) {
        ItemStack stack = new ItemStack(ToolsModule.CUTTING.get());
        stack.set(CoreModule.GENOME.get(), genome);
        return stack;
    }

    public static double takeChance(Genome genome) {
        int strength = Genetics.expressed(genome, CoreModule.STRENGTH.getKey(), Genetics.current());
        return Math.min(1.0, (strength + 1) / 10.0);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        Genome genome = stack.get(CoreModule.GENOME.get());
        if (genome != null && ctx.getClickedFace() == net.minecraft.core.Direction.UP
                && !(level.getBlockState(ctx.getClickedPos()).getBlock() instanceof com.kragnoth.cultivar.api.crop.PlantHolder)) {
            // Straight into soil: same take roll; refused soil costs nothing.
            var check = com.kragnoth.cultivar.content.field.FieldPlanting.check(level, ctx.getClickedPos(), genome, ctx.getPlayer());
            if (check != com.kragnoth.cultivar.content.field.FieldPlanting.Result.PASS) {
                if (check == com.kragnoth.cultivar.content.field.FieldPlanting.Result.PLANTED && !level.isClientSide()) {
                    boolean took = level.random.nextDouble() < takeChance(genome);
                    if (took) {
                        com.kragnoth.cultivar.content.field.FieldPlanting.plant(level, ctx.getClickedPos(), genome, START_STAGE,
                                stack.has(CoreModule.ASSAYED.get()), ctx.getPlayer());
                    } else {
                        level.playSound(null, ctx.getClickedPos(), SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 1f, 1f);
                        if (ctx.getPlayer() != null) {
                            ctx.getPlayer().displayClientMessage(Component.translatable("item.cultivar.cutting.failed").withStyle(ChatFormatting.GRAY), true);
                        }
                    }
                    if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        if (genome == null || TrellisBlock.isSegment(level, ctx.getClickedPos())
                || !(level.getBlockEntity(ctx.getClickedPos()) instanceof CropBlockEntity crop)
                || crop.hasPlant() || crop.isCross()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean took = level.random.nextDouble() < takeChance(genome) && crop.plant(genome, START_STAGE);
        if (took) {
            crop.setAssayed(stack.has(CoreModule.ASSAYED.get()));
        }
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, ctx.getClickedPos(), took ? SoundEvents.CROP_PLANTED : SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 1f, 1f);
        if (!took && ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("item.cultivar.cutting.failed").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        Genome genome = stack.get(CoreModule.GENOME.get());
        return genome == null ? super.getName(stack)
                : Component.translatable("item.cultivar.cutting.of", CultivarSeedItem.plantName(genome));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Genome genome = stack.get(CoreModule.GENOME.get());
        if (genome == null) {
            return;
        }
        tooltip.add(Component.translatable("item.cultivar.cutting.hint", (int) Math.round(takeChance(genome) * 100))
                .withStyle(ChatFormatting.DARK_GREEN));
        if (!CultivarSeedItem.statsVisible(stack)) {
            tooltip.add(Component.translatable("item.cultivar.seed.unassayed").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        var expression = Genetics.current();
        CoreModule.STATS.getEntries().forEach(stat -> tooltip.add(Component.translatable(
                "stat.cultivar." + stat.getId().getPath()).append(": " + Genetics.expressed(genome, stat.getKey(), expression))
                .withStyle(ChatFormatting.GRAY)));
    }
}
