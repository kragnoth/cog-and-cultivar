package com.kragnoth.cultivar.compat.jei.client;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.ledger.ClientKnowledge;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import com.kragnoth.cultivar.content.trial.TrialModule;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * JEI integration, discovered by annotation and only loaded when JEI is installed.
 * <ul>
 *   <li>Seeds and cuttings are one item each; the species makes them distinct (stats don't).</li>
 *   <li>Crosses are a category, each hidden until the player knows the child and both parents (the same rule as the
 *       Ledger), so JEI never spoils discovery. {@code genetics.revealAllMutations} shows them all.</li>
 *   <li>Every bred plant gets an info page saying how it is found.</li>
 * </ul>
 */
@JeiPlugin
public final class CultivarJeiPlugin implements IModPlugin {
    private static final List<MutationCategory.Cross> CROSSES = new ArrayList<>();
    private static @Nullable IJeiRuntime runtime;
    private static boolean listening;

    @Override
    public ResourceLocation getPluginUid() {
        return Cultivar.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        ISubtypeInterpreter<ItemStack> bySpecies = new ISubtypeInterpreter<>() {
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
        };
        registration.registerSubtypeInterpreter(CropModule.SEED.get(), bySpecies);
        registration.registerSubtypeInterpreter(ToolsModule.CUTTING.get(), bySpecies);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MutationCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        CROSSES.clear();
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Set<Holder<Plant>> bred = new LinkedHashSet<>();
        for (Mutation m : level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION)) {
            CROSSES.add(new MutationCategory.Cross(m));
            bred.add(m.child());
        }
        registration.addRecipes(MutationCategory.TYPE, List.copyOf(CROSSES));
        Component info = Component.translatable("jei.cultivar.bred_info");
        bred.forEach(plant -> registration.addItemStackInfo(MutationCategory.seed(plant), info));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(LedgerModule.LEDGER.get(), MutationCategory.TYPE);
        registration.addRecipeCatalyst(CropModule.TIMBER_TRELLIS.get(), MutationCategory.TYPE);
        registration.addRecipeCatalyst(CropModule.IRON_TRELLIS.get(), MutationCategory.TYPE);
        registration.addRecipeCatalyst(TrialModule.TRIAL_BED.get(), MutationCategory.TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        if (!listening) {
            listening = true;
            ClientKnowledge.onChange(CultivarJeiPlugin::applyKnowledge);
        }
        applyKnowledge();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Shows the crosses the player knows and hides the rest. */
    private static void applyKnowledge() {
        IJeiRuntime rt = runtime;
        if (rt == null || CROSSES.isEmpty()) {
            return;
        }
        LedgerItem.Data known = ClientKnowledge.get();
        boolean reveal = reveal();
        List<MutationCategory.Cross> shown = new ArrayList<>();
        List<MutationCategory.Cross> hidden = new ArrayList<>();
        for (MutationCategory.Cross cross : CROSSES) {
            Mutation m = cross.mutation();
            boolean visible = reveal || knows(known, m.parentA()) && knows(known, m.parentB()) && knows(known, m.child());
            (visible ? shown : hidden).add(cross);
        }
        rt.getRecipeManager().unhideRecipes(MutationCategory.TYPE, shown);
        rt.getRecipeManager().hideRecipes(MutationCategory.TYPE, hidden);
    }

    private static boolean knows(LedgerItem.Data known, Holder<Plant> plant) {
        return plant.unwrapKey().map(known::knows).orElse(false);
    }

    private static boolean reveal() {
        try {
            return CultivarServerConfig.REVEAL_ALL_MUTATIONS.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }
}
