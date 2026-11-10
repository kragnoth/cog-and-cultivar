package com.kragnoth.cultivar.content.assay;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A printed assay: always shows the full genome it was made from (pair values, not just the expressed one). */
public class AssayCardItem extends Item {
    public AssayCardItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Genome genome) {
        ItemStack stack = new ItemStack(AssayModule.ASSAY_CARD.get());
        if (genome != null) {
            stack.set(CoreModule.GENOME.get(), genome);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Genome genome = stack.get(CoreModule.GENOME.get());
        return genome == null ? super.getName(stack)
                : Component.translatable("item.cultivar.assay_card.of", CultivarSeedItem.plantName(genome));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Genome genome = stack.get(CoreModule.GENOME.get());
        if (genome == null) {
            return;
        }
        var expression = Genetics.current();
        CoreModule.STATS.getEntries().forEach(stat -> {
            var pair = genome.stats().get(stat.getKey());
            String alleles = pair == null ? "" : " (" + pair.dominant() + "/" + pair.recessive() + ")";
            tooltip.add(Component.translatable("stat.cultivar." + stat.getId().getPath())
                    .append(": " + Genetics.expressed(genome, stat.getKey(), expression) + alleles)
                    .withStyle(ChatFormatting.GRAY));
        });
    }
}
