package com.kragnoth.cultivar.content.crop;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** One item for every species; identity and stats live in the {@code cultivar:genome} component. */
public class CultivarSeedItem extends Item {
    public CultivarSeedItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Genome genome) {
        ItemStack stack = new ItemStack(CropModule.SEED.get());
        stack.set(CoreModule.GENOME.get(), genome);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Genome genome = stack.get(CoreModule.GENOME.get());
        if (genome == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.cultivar.seed.named", plantName(genome));
    }

    public static Component plantName(Genome genome) {
        return plantName(genome.species().unwrapKey().map(k -> k.location())
                .orElse(ResourceLocation.withDefaultNamespace("unknown")));
    }

    public static Component plantName(ResourceLocation id) {
        String fallback = Character.toUpperCase(id.getPath().charAt(0)) + id.getPath().substring(1).replace('_', ' ');
        return Component.translatableWithFallback("plant." + id.getNamespace() + "." + id.getPath(), fallback);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Genome genome = stack.get(CoreModule.GENOME.get());
        if (genome == null) {
            return;
        }
        var expression = Genetics.current();
        CoreModule.STATS.getEntries().forEach(stat -> tooltip.add(Component.translatable(
                "stat.cultivar." + stat.getId().getPath()).append(": " + Genetics.expressed(genome, stat.getKey(), expression))
                .withStyle(ChatFormatting.GRAY)));
    }
}
