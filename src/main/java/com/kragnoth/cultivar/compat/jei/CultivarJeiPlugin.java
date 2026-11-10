package com.kragnoth.cultivar.compat.jei;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CropModule;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * JEI integration, discovered by annotation and only loaded when JEI is installed.
 * Seeds are one item, so JEI is told that the species makes them distinct (stats don't).
 */
@JeiPlugin
public final class CultivarJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Cultivar.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(CropModule.SEED.get(), new ISubtypeInterpreter<>() {
            @Override
            public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) {
                Genome genome = stack.get(CoreModule.GENOME.get());
                return genome == null ? null : genome.species().unwrapKey().map(k -> k.location()).orElse(null);
            }

            @Override
            @SuppressWarnings("deprecation") // still abstract in JEI 19.21; required until JEI drops it
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                Object data = getSubtypeData(stack, context);
                return data == null ? "" : data.toString();
            }
        });
    }
}
