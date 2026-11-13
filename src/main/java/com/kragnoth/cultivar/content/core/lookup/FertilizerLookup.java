package com.kragnoth.cultivar.content.core.lookup;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Fertilizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** The datapack fertilizer entry an item belongs to, if any. */
public final class FertilizerLookup {
    private FertilizerLookup() {}

    public static Optional<Fertilizer> forItem(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return level.registryAccess().registryOrThrow(CultivarRegistries.FERTILIZER).stream()
                .filter(f -> f.items().contains(stack.getItemHolder())).findFirst();
    }
}
