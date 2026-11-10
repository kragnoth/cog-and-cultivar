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
import com.kragnoth.cultivar.api.plant.Fertilizer;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.bed.PlanterBedModule;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.irrigation.IrrigationModule;
import java.util.ArrayList;
import java.util.Locale;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
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
 *   <li>Reference pages that spoil nothing: soils, fertilizers, the Assaying Engine; info pages for the machines.</li>
 * </ul>
 */
@JeiPlugin
public final class CultivarJeiPlugin implements IModPlugin {
    private static final List<MutationCategory.Cross> CROSSES = new ArrayList<>();
    private static final List<GrowingCategory.Growing> GROWING = new ArrayList<>();
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

    record SoilPage(Soil soil) {}

    record FertilizerPage(Fertilizer fertilizer) {}

    record BlightPage(ResourceLocation id, Weed weed) {}

    static final RecipeType<SoilPage> SOILS = RecipeType.create(Cultivar.MOD_ID, "soil", SoilPage.class);
    static final RecipeType<BlightPage> BLIGHTS = RecipeType.create(Cultivar.MOD_ID, "blight", BlightPage.class);
    static final RecipeType<FertilizerPage> FERTILIZERS = RecipeType.create(Cultivar.MOD_ID, "fertilizer", FertilizerPage.class);

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MutationCategory(gui),
                new GrowingCategory(gui),
                new AssayCategory(gui),
                new LinesCategory<>(gui, SOILS, "jei.cultivar.soil", new ItemStack(Items.FARMLAND), 6, CultivarJeiPlugin::soilItems, CultivarJeiPlugin::soilLines),
                new LinesCategory<>(gui, FERTILIZERS, "jei.cultivar.fertilizer", new ItemStack(Items.BONE_MEAL), 3,
                        p -> p.fertilizer().items().stream().map(h -> new ItemStack(h.value())).toList(),
                        CultivarJeiPlugin::fertilizerLines),
                new LinesCategory<>(gui, BLIGHTS, "jei.cultivar.blight", new ItemStack(Items.IRON_HOE), 5,
                        p -> p.weed().rakeProducts().stream().map(pr -> new ItemStack(pr.item(), pr.max())).toList(),
                        CultivarJeiPlugin::blightLines));
    }

    private static List<ItemStack> soilItems(SoilPage page) {
        return page.soil().blocks().stream().map(h -> new ItemStack(h.value().asItem())).filter(st -> !st.isEmpty()).toList();
    }

    private static List<Component> soilLines(SoilPage page) {
        Soil soil = page.soil();
        List<Component> lines = new ArrayList<>();
        var first = soil.blocks().stream().findFirst();
        first.ifPresent(h -> lines.add(h.value().getName().copy().withStyle(ChatFormatting.BOLD)));
        lines.add(Component.translatable("ledger.cultivar.cond.humidity", level(soil.humidity().getSerializedName())));
        lines.add(Component.translatable("ledger.cultivar.cond.acidity", level(soil.acidity().getSerializedName())));
        lines.add(Component.translatable("ledger.cultivar.cond.nutrients", level(soil.nutrients().getSerializedName())));
        lines.add(Component.translatable("jei.cultivar.soil.growth", String.format(Locale.ROOT, "%.2f", soil.growthModifier())));
        lines.add(Component.translatable("jei.cultivar.soil.shifts").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static List<Component> blightLines(BlightPage page) {
        Weed weed = page.weed();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatableWithFallback("weed." + page.id().getNamespace() + "." + page.id().getPath(),
                page.id().getPath()).withStyle(ChatFormatting.BOLD));
        if (weed.habitat().isEmpty()) {
            lines.add(Component.translatable("jei.cultivar.blight.anywhere"));
        }
        weed.habitat().forEach(c -> lines.add(com.kragnoth.cultivar.client.ledger.LedgerScreen.describe(c)));
        String traits = (weed.lethal() ? "lethal" : "") + (weed.aggressive() ? "spreads" : "");
        lines.add(Component.translatable("jei.cultivar.blight." + (traits.isEmpty() ? "harmless" : traits)));
        lines.add(Component.translatable("jei.cultivar.blight.drops").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static List<Component> fertilizerLines(FertilizerPage page) {
        Fertilizer f = page.fertilizer();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jei.cultivar.fertilizer.potency", f.potency()));
        if (f.triggersMutation()) {
            lines.add(Component.translatable("jei.cultivar.fertilizer.mutation"));
        }
        if (f.triggersWeeds()) {
            lines.add(Component.translatable("jei.cultivar.fertilizer.weeds"));
        }
        return lines;
    }

    private static String level(String serialized) {
        return serialized.replace('_', ' ');
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

        var access = level.registryAccess();
        GROWING.clear();
        var soilList = access.registryOrThrow(CultivarRegistries.SOIL).stream().toList();
        access.registryOrThrow(CultivarRegistries.PLANT).holders()
                .forEach(h -> GROWING.add(GrowingCategory.Growing.of(h, soilList)));
        registration.addRecipes(GrowingCategory.TYPE, List.copyOf(GROWING));
        registration.addRecipes(SOILS, access.registryOrThrow(CultivarRegistries.SOIL).stream().map(SoilPage::new).toList());
        registration.addRecipes(BLIGHTS, access.registryOrThrow(CultivarRegistries.WEED).entrySet().stream()
                .map(e -> new BlightPage(e.getKey().location(), e.getValue())).toList());
        registration.addRecipes(FERTILIZERS,
                access.registryOrThrow(CultivarRegistries.FERTILIZER).stream().map(FertilizerPage::new).toList());
        List<ItemStack> seeds = access.registryOrThrow(CultivarRegistries.PLANT).holders()
                .map(h -> MutationCategory.seed(h)).toList();
        registration.addRecipes(AssayCategory.TYPE, List.of(new AssayCategory.Assay(seeds)));

        info(registration, PlanterBedModule.BED_ITEM.get(), "planter_bed");
        info(registration, TrialModule.TRIAL_BED_ITEM.get(), "trial_bed");
        info(registration, IrrigationModule.SPRINKLER_ITEM.get(), "sprinkler");
        info(registration, ClocheModule.BELL_CLOCHE.get(), "bell_cloche");
        info(registration, LedgerModule.LEDGER.get(), "botanical_ledger");
        info(registration, CropModule.TIMBER_TRELLIS.get(), "trellis");
        info(registration, CropModule.IRON_TRELLIS.get(), "trellis");
    }

    private static void info(IRecipeRegistration registration, ItemLike item, String key) {
        registration.addIngredientInfo(item, Component.translatable("jei.cultivar.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(LedgerModule.LEDGER.get(), MutationCategory.TYPE);
        registration.addRecipeCatalyst(CropModule.TIMBER_TRELLIS.get(), MutationCategory.TYPE);
        registration.addRecipeCatalyst(CropModule.IRON_TRELLIS.get(), MutationCategory.TYPE);
        registration.addRecipeCatalyst(TrialModule.TRIAL_BED.get(), MutationCategory.TYPE);
        registration.addRecipeCatalyst(AssayModule.ENGINE_ITEM.get(), AssayCategory.TYPE);
        for (var trellis : List.of(CropModule.TIMBER_TRELLIS.get(), CropModule.IRON_TRELLIS.get())) {
            registration.addRecipeCatalyst(trellis, GrowingCategory.TYPE, SOILS, FERTILIZERS, BLIGHTS);
        }
        registration.addRecipeCatalyst(PlanterBedModule.BED_ITEM.get(), FERTILIZERS);
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
        if (rt == null || CROSSES.isEmpty() && GROWING.isEmpty()) {
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

        // Growing pages: any plant with a real seed item (vanilla or modded) always shows; plants that only exist by
        // breeding (the blooms) show once you know them.
        java.util.Set<Holder<Plant>> bred = new java.util.HashSet<>();
        CROSSES.forEach(c -> bred.add(c.mutation().child()));
        List<GrowingCategory.Growing> grow = new ArrayList<>(), hide = new ArrayList<>();
        for (GrowingCategory.Growing g : GROWING) {
            boolean breedOnly = g.plant().value().seeds().isEmpty() && bred.stream().anyMatch(x -> x.is(g.plant()));
            boolean visible = reveal || !breedOnly || knows(known, g.plant());
            (visible ? grow : hide).add(g);
        }
        rt.getRecipeManager().unhideRecipes(GrowingCategory.TYPE, grow);
        rt.getRecipeManager().hideRecipes(GrowingCategory.TYPE, hide);
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
