package com.kragnoth.cultivar.compat.jei.client;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;

/** Assaying Engine: any seed in, the same seed out with its stats revealed; a Ledger in its slot records the species. */
final class AssayCategory implements IRecipeCategory<AssayCategory.Assay> {
    record Assay(List<ItemStack> seeds) {}

    static final RecipeType<Assay> TYPE = RecipeType.create(Cultivar.MOD_ID, "assaying", Assay.class);

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    AssayCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(AssayModule.ENGINE_ITEM.get()));
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<Assay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.cultivar.assaying");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 48;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Assay assay, IFocusGroup focuses) {
        var in = builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(assay.seeds());
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 21).setStandardSlotBackground()
                .addItemStack(new ItemStack(LedgerModule.LEDGER.get()))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.cultivar.assaying.ledger")));
        List<ItemStack> assayed = assay.seeds().stream().map(s -> {
            ItemStack copy = s.copy();
            copy.set(CoreModule.ASSAYED.get(), Unit.INSTANCE);
            return copy;
        }).toList();
        var out = builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 11).setOutputSlotBackground().addItemStacks(assayed);
        builder.createFocusLink(in, out);
    }

    @Override
    public void draw(Assay assay, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, 28, 11);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("jei.cultivar.assaying.rotation"), 0, 40, 0x404040, false);
    }
}
