package com.kragnoth.cultivar.compat.jei.client;

import java.util.List;
import java.util.function.Function;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A reference page: one slot of items and a few lines of text beside it (soils, fertilizers). */
final class LinesCategory<T> implements IRecipeCategory<T> {
    static final int LINE = 10;

    private final RecipeType<T> type;
    private final Component title;
    private final IDrawable icon;
    private final int lines;
    private final Function<T, List<ItemStack>> items;
    private final Function<T, List<Component>> text;

    LinesCategory(IGuiHelper gui, RecipeType<T> type, String titleKey, ItemStack icon, int lines,
                  Function<T, List<ItemStack>> items, Function<T, List<Component>> text) {
        this.type = type;
        this.title = Component.translatable(titleKey);
        this.icon = gui.createDrawableItemStack(icon);
        this.lines = lines;
        this.items = items;
        this.text = text;
    }

    @Override
    public RecipeType<T> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
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
        return Math.max(18, lines * LINE);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(items.apply(recipe));
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        int y = 1;
        for (Component line : text.apply(recipe)) {
            g.drawString(font, line, 24, y, 0x404040, false);
            y += LINE;
        }
    }
}
