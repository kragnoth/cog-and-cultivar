package com.kragnoth.cultivar.compat.jei.client;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.client.ledger.LedgerScreen;
import com.kragnoth.cultivar.content.core.condition.SoilMatch;
import com.kragnoth.cultivar.content.crop.CropModule;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What a plant needs to grow, and which soils give it: its seeds (Cultivar's and the mod's own), the soils that work
 * as-is, the soils that work once shifted by water, a Bell Cloche, heat or soil conditioners, and the requirement lines. Computed at
 * base Strength, so a fresh seed grows on everything listed.
 */
final class GrowingCategory implements IRecipeCategory<GrowingCategory.Growing> {
    record Growing(Holder<Plant> plant, List<ItemStack> seeds, List<ItemStack> asIs, List<ItemStack> shifted) {
        static Growing of(Holder<Plant> plant, Iterable<Soil> soils, SoilMatch.Reach reach) {
            List<ItemStack> seeds = new ArrayList<>();
            seeds.add(MutationCategory.seed(plant));
            plant.value().seeds().ifPresent(set -> set.forEach(i -> seeds.add(new ItemStack(i.value()))));
            List<ItemStack> asIs = new ArrayList<>(), shifted = new ArrayList<>();
            for (Soil soil : soils) {
                var fit = SoilMatch.fit(plant.value().requirements(), soil, 1, reach);
                if (fit == SoilMatch.Fit.NO) {
                    continue;
                }
                soil.blocks().forEach(b -> {
                    ItemStack stack = new ItemStack(b.value().asItem());
                    if (!stack.isEmpty()) {
                        (fit == SoilMatch.Fit.AS_IS ? asIs : shifted).add(stack);
                    }
                });
            }
            return new Growing(plant, seeds, asIs, shifted);
        }
    }

    static final RecipeType<Growing> TYPE = RecipeType.create(Cultivar.MOD_ID, "growing", Growing.class);
    private static final int LINES_Y = 22;

    private final IDrawable icon;

    GrowingCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(CropModule.TIMBER_TRELLIS.get()));
    }

    @Override
    public RecipeType<Growing> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.cultivar.growing");
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
        return LINES_Y + 5 * 10;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Growing g, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(g.seeds());
        builder.addSlot(RecipeIngredientRole.CATALYST, 61, 1).setStandardSlotBackground().addItemStacks(g.asIs())
                .addRichTooltipCallback((v, t) -> t.add(Component.translatable("jei.cultivar.growing.as_is").withStyle(ChatFormatting.GREEN)));
        builder.addSlot(RecipeIngredientRole.CATALYST, 101, 1).setStandardSlotBackground().addItemStacks(g.shifted())
                .addRichTooltipCallback((v, t) -> t.add(Component.translatable("jei.cultivar.growing.shifted").withStyle(ChatFormatting.GOLD)));
    }

    @Override
    public void draw(Growing g, IRecipeSlotsView slots, GuiGraphics gfx, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        gfx.drawString(font, Component.translatable("jei.cultivar.growing.soil"), 24, 6, 0x404040, false);
        gfx.drawString(font, "+", 87, 6, 0x404040, false);
        int y = LINES_Y;
        List<com.kragnoth.cultivar.api.condition.GrowthCondition> reqs = g.plant().value().requirements();
        if (reqs.isEmpty()) {
            gfx.drawString(font, Component.translatable("jei.cultivar.growing.anywhere"), 0, y, 0x404040, false);
            y += 10;
        }
        for (var c : reqs) {
            gfx.drawString(font, LedgerScreen.describe(c), 0, y, 0x404040, false);
            y += 10;
        }
        if (g.asIs().isEmpty() && g.shifted().isEmpty()) {
            gfx.drawString(font, Component.translatable("jei.cultivar.growing.no_soil").withStyle(ChatFormatting.DARK_RED), 0, y, 0x404040, false);
        }
    }
}
