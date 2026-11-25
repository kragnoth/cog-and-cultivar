package com.kragnoth.cultivar.compat.jei.client;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Product;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
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
import net.minecraft.world.item.Items;

/**
 * What a plant gives when harvested: its seeds (Cultivar's and the mod's own) on the left, the products from the
 * plant's {@code products} list on the right (and, for pruning shears, its {@code clip_products}), each with its
 * chance and count range straight from the datapack JSON. Pages are per roll; Gain adds rolls per harvest.
 */
final class HarvestCategory implements IRecipeCategory<HarvestCategory.Harvest> {
    record Harvest(Holder<Plant> plant, List<ItemStack> seeds, List<Product> products, List<Product> clips) {
        static Harvest of(Holder<Plant> plant) {
            List<ItemStack> seeds = new ArrayList<>();
            seeds.add(MutationCategory.seed(plant));
            plant.value().seeds().ifPresent(set -> set.forEach(i -> seeds.add(new ItemStack(i.value()))));
            return new Harvest(plant, seeds, real(plant.value().products()), real(plant.value().clipProducts()));
        }

        /** Products that can actually drop something. */
        private static List<Product> real(List<Product> in) {
            return in.stream().filter(p -> p.item().value() != Items.AIR && p.max() > 0 && p.chance() > 0).toList();
        }

        boolean isEmpty() {
            return products.isEmpty() && clips.isEmpty();
        }
    }

    static final RecipeType<Harvest> TYPE = RecipeType.create(Cultivar.MOD_ID, "harvest", Harvest.class);
    private static final int OUT_X = 52;
    private static final int PER_ROW = 5;
    private static final int TEXT_Y = 40; // below two rows of output slots
    private static final int TEXT_LINES = 4;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    HarvestCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(ToolsModule.PRUNING_SHEARS.get()));
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<Harvest> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.cultivar.harvest");
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
        return TEXT_Y + (TEXT_LINES + 1) * 10;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Harvest h, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(h.seeds());
        int i = 0;
        for (Product p : h.products()) {
            output(builder, i++, p, false);
        }
        for (Product p : h.clips()) {
            output(builder, i++, p, true);
        }
    }

    private static void output(IRecipeLayoutBuilder builder, int index, Product p, boolean clip) {
        if (index >= PER_ROW * 2) {
            return; // two rows of slots; the text lines still list everything that fits
        }
        int x = OUT_X + (index % PER_ROW) * 18;
        int y = 1 + (index / PER_ROW) * 18;
        builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).setOutputSlotBackground()
                .addItemStack(new ItemStack(p.item().value(), Math.max(1, p.max())))
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.cultivar.harvest.tip", percent(p.chance()), range(p))
                            .withStyle(ChatFormatting.GRAY));
                    if (clip) {
                        tooltip.add(Component.translatable("jei.cultivar.harvest.clip_tip").withStyle(ChatFormatting.GOLD));
                    }
                });
    }

    @Override
    public void draw(Harvest h, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        arrow.draw(g, 26, 1);
        int y = TEXT_Y;
        int used = 0;
        List<Component> lines = new ArrayList<>();
        h.products().forEach(p -> lines.add(line(p)));
        if (!h.clips().isEmpty()) {
            lines.add(Component.translatable("jei.cultivar.harvest.clip").withStyle(ChatFormatting.GOLD));
            h.clips().forEach(p -> lines.add(line(p)));
        }
        for (Component line : lines) {
            if (used == TEXT_LINES - 1 && lines.size() > TEXT_LINES) {
                g.drawString(font, Component.translatable("jei.cultivar.harvest.more", lines.size() - used), 0, y, 0x404040, false);
                break;
            }
            g.drawString(font, line, 0, y, 0x404040, false);
            y += 10;
            used++;
        }
        g.drawString(font, Component.translatable("jei.cultivar.harvest.gain").withStyle(ChatFormatting.DARK_GRAY),
                0, TEXT_Y + TEXT_LINES * 10, 0x404040, false);
    }

    private static Component line(Product p) {
        return Component.translatable("jei.cultivar.harvest.line", new ItemStack(p.item().value()).getHoverName(), range(p),
                percent(p.chance()));
    }

    private static String range(Product p) {
        return p.max() > p.min() ? p.min() + "-" + p.max() : String.valueOf(p.min());
    }

    private static String percent(float chance) {
        double pct = Math.min(1, chance) * 100;
        return Math.abs(pct - Math.rint(pct)) < 0.05 ? String.valueOf((long) Math.rint(pct)) : String.format(Locale.ROOT, "%.1f", pct);
    }
}
