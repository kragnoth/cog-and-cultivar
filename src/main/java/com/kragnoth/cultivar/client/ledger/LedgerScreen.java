package com.kragnoth.cultivar.client.ledger;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Product;
import com.kragnoth.cultivar.content.core.condition.BiomeCondition;
import com.kragnoth.cultivar.content.core.condition.DimensionCondition;
import com.kragnoth.cultivar.content.core.condition.LightCondition;
import com.kragnoth.cultivar.content.core.condition.SoilConditions;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.ledger.LedgerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-only view of a Botanical Ledger. Everything comes from synced data (the item's component and the
 * client's copy of the plant/mutation registries), so it needs no packets.
 */
public class LedgerScreen extends Screen {
    private static final int TEXT_X = 36;
    private static final int TEXT_Y = 30;
    private static final int TEXT_W = 114;
    private static final int LINES_PER_PAGE = 14;

    private final LedgerItem.Data data;
    private final List<List<FormattedCharSequence>> pages = new ArrayList<>();
    private int page;
    private PageButton forward;
    private PageButton back;

    public LedgerScreen(ItemStack ledger) {
        super(Component.translatable("item.cultivar.botanical_ledger"));
        // What the player knows plus anything in this Ledger not yet read (the server merges them on use).
        var merged = new java.util.HashSet<>(LedgerItem.data(ledger).researched());
        merged.addAll(com.kragnoth.cultivar.content.ledger.ClientKnowledge.get().researched());
        this.data = new LedgerItem.Data(merged);
    }

    @Override
    protected void init() {
        buildPages();
        int left = (width - 192) / 2;
        forward = addRenderableWidget(new PageButton(left + 116, 159, true, b -> turn(1), true));
        back = addRenderableWidget(new PageButton(left + 43, 159, false, b -> turn(-1), true));
        updateButtons();
    }

    /** Jump to a page (used by the dev screenshot harness). */
    public void goToPage(int target) {
        turn(target - page);
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages.size() - 1, page + delta));
        updateButtons();
    }

    private void updateButtons() {
        forward.visible = page < pages.size() - 1;
        back.visible = page > 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - 192) / 2;
        int y = TEXT_Y;
        for (FormattedCharSequence line : pages.get(page)) {
            g.drawString(font, line, left + TEXT_X, y, 0x2B2118, false);
            y += 9;
        }
        Component number = Component.literal((page + 1) + " / " + pages.size());
        g.drawString(font, number, left + 192 - 44 - font.width(number), 16, 0x6B5A44, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BookViewScreen.BOOK_LOCATION, (width - 192) / 2, 2, 0, 0, 192, 192);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- content

    private void buildPages() {
        pages.clear();
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            pages.add(List.of());
            return;
        }
        Registry<Plant> plants = mc.level.registryAccess().registryOrThrow(CultivarRegistries.PLANT);
        Registry<Mutation> mutations = mc.level.registryAccess().registryOrThrow(CultivarRegistries.MUTATION);
        List<ResourceKey<Plant>> known = data.researched().stream().filter(plants::containsKey)
                .sorted(Comparator.comparing(k -> k.location().toString())).toList();

        List<Component> index = new ArrayList<>();
        index.add(Component.translatable("ledger.cultivar.title").withStyle(ChatFormatting.BOLD));
        index.add(Component.translatable("ledger.cultivar.progress", known.size(), plants.size()));
        index.add(Component.empty());
        known.forEach(k -> index.add(Component.literal("• ").append(CultivarSeedItem.plantName(k.location()))));
        if (known.isEmpty()) {
            index.add(Component.translatable("ledger.cultivar.empty").withStyle(ChatFormatting.ITALIC));
        }
        paginate(index);

        for (ResourceKey<Plant> key : known) {
            paginate(plantPage(key, plants.getOrThrow(key), mutations));
        }
    }

    private List<Component> plantPage(ResourceKey<Plant> key, Plant plant, Registry<Mutation> mutations) {
        List<Component> lines = new ArrayList<>();
        lines.add(CultivarSeedItem.plantName(key.location()).copy().withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable("ledger.cultivar.growth", plant.stages(),
                Math.round(plant.growthChance() * 100), String.format(Locale.ROOT, "%.1f", plant.growthBonus() * 100)));
        if (!plant.requirements().isEmpty()) {
            lines.add(Component.translatable("ledger.cultivar.needs").withStyle(ChatFormatting.DARK_GREEN));
            plant.requirements().forEach(c -> lines.add(Component.literal("  ").append(describe(c))));
        }
        if (!plant.products().isEmpty()) {
            lines.add(Component.translatable("ledger.cultivar.yields").withStyle(ChatFormatting.DARK_GREEN));
            for (Product p : plant.products()) {
                lines.add(Component.literal("  ").append(new ItemStack(p.item()).getHoverName())
                        .append(" " + p.min() + (p.max() > p.min() ? "-" + p.max() : "")));
            }
        }
        boolean reveal = com.kragnoth.cultivar.core.config.CultivarServerConfig.REVEAL_ALL_MUTATIONS.get();
        List<Component> from = new ArrayList<>();
        List<Component> leadsTo = new ArrayList<>();
        for (Mutation m : mutations) {
            boolean allKnown = reveal || known(m.parentA()) && known(m.parentB()) && known(m.child());
            if (m.child().is(key)) {
                from.add(allKnown ? name(m.parentA()).copy().append(" × ").append(name(m.parentB()))
                        .append(" (" + Math.round(m.chance() * 100) + "%)")
                        : Component.translatable("ledger.cultivar.unknown_cross").withStyle(ChatFormatting.GRAY));
            } else if (allKnown && (m.parentA().is(key) || m.parentB().is(key))) {
                leadsTo.add(name(m.child()));
            }
        }
        if (!from.isEmpty()) {
            lines.add(Component.translatable("ledger.cultivar.bred_from").withStyle(ChatFormatting.DARK_PURPLE));
            from.forEach(c -> lines.add(Component.literal("  ").append(c)));
        }
        if (!leadsTo.isEmpty()) {
            lines.add(Component.translatable("ledger.cultivar.leads_to").withStyle(ChatFormatting.DARK_PURPLE));
            leadsTo.forEach(c -> lines.add(Component.literal("  ").append(c)));
        }
        return lines;
    }

    private boolean known(net.minecraft.core.Holder<Plant> plant) {
        return plant.unwrapKey().map(data::knows).orElse(false);
    }

    private static Component name(net.minecraft.core.Holder<Plant> plant) {
        return plant.unwrapKey().map(k -> CultivarSeedItem.plantName(k.location())).orElse(Component.literal("?"));
    }

    /** Short text for a growth condition; shared with the JEI mutation category. */
    public static Component describe(GrowthCondition c) {
        return switch (c) {
            case LightCondition l -> Component.translatable("ledger.cultivar.cond.light", l.min(), l.max());
            case SoilConditions.Humidity h -> soil("humidity", h.value().getSerializedName(), h.comparison().getSerializedName());
            case SoilConditions.Acidity a -> soil("acidity", a.value().getSerializedName(), a.comparison().getSerializedName());
            case SoilConditions.Nutrients n -> soil("nutrients", n.value().getSerializedName(), n.comparison().getSerializedName());
            case BiomeCondition b -> Component.translatable(b.blacklist() ? "ledger.cultivar.cond.not_biome" : "ledger.cultivar.cond.biome");
            case DimensionCondition d -> Component.translatable("ledger.cultivar.cond.dimension");
            default -> {
                var id = com.kragnoth.cultivar.api.CultivarRegistries.GROWTH_CONDITION_TYPE.getKey(c.type());
                yield Component.literal(id == null ? "?" : id.getPath());
            }
        };
    }

    private static MutableComponent soil(String kind, String value, String comparison) {
        String cmp = switch (comparison) {
            case "at_least" -> "≥ ";
            case "at_most" -> "≤ ";
            default -> "";
        };
        return Component.translatable("ledger.cultivar.cond." + kind,
                cmp + value.replace('_', ' ').toLowerCase(Locale.ROOT));
    }

    private void paginate(List<Component> lines) {
        List<FormattedCharSequence> current = new ArrayList<>();
        for (Component line : lines) {
            for (FormattedCharSequence wrapped : font.split(line, TEXT_W)) {
                if (current.size() == LINES_PER_PAGE) {
                    pages.add(current);
                    current = new ArrayList<>();
                }
                current.add(wrapped);
            }
        }
        pages.add(current);
    }
}
