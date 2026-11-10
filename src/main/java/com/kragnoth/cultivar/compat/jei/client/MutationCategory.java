package com.kragnoth.cultivar.compat.jei.client;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.client.ledger.LedgerScreen;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
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
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One cross: two parents, the child, its chance and the habitat it needs. Hidden until the player knows it. */
final class MutationCategory implements IRecipeCategory<MutationCategory.Cross> {
    /** A mutation as JEI sees it; identity matters, since hiding works on these exact instances. */
    record Cross(Mutation mutation) {}

    static final RecipeType<Cross> TYPE = RecipeType.create(Cultivar.MOD_ID, "mutation", Cross.class);
    private static final int WIDTH = 150;
    private static final int TEXT_Y = 24;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    MutationCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(LedgerModule.LEDGER.get()));
        this.arrow = gui.getRecipeArrow();
    }

    static ItemStack seed(Holder<Plant> plant) {
        return CultivarSeedItem.of(Genome.base(plant));
    }

    @Override
    public RecipeType<Cross> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.cultivar.mutation");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return TEXT_Y + 30;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Cross cross, IFocusGroup focuses) {
        Mutation m = cross.mutation();
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStack(seed(m.parentA()));
        builder.addSlot(RecipeIngredientRole.INPUT, 37, 1).setStandardSlotBackground().addItemStack(seed(m.parentB()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 101, 1).setOutputSlotBackground().addItemStack(seed(m.child()));
    }

    @Override
    public void draw(Cross cross, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "+", 25, 5, 0x404040, false);
        arrow.draw(g, 66, 1);
        Mutation m = cross.mutation();
        g.drawString(font, Math.round(m.chance() * 100) + "%", 127, 5, 0x404040, false);
        int y = TEXT_Y;
        if (m.conditions().isEmpty()) {
            g.drawString(font, Component.translatable("jei.cultivar.mutation.anywhere"), 0, y, 0x404040, false);
        }
        for (var condition : m.conditions()) {
            g.drawString(font, LedgerScreen.describe(condition), 0, y, 0x404040, false);
            y += 10;
        }
    }
}
