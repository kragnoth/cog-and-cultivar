package com.kragnoth.cultivar.client.tools;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.tools.SatchelActionPayload;
import com.kragnoth.cultivar.content.tools.SatchelActionPayload.Action;
import com.kragnoth.cultivar.content.tools.SeedSatchelItem;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Seed Satchel contents: one row per stored genome with its count and stats (or "not assayed"), Take and Void
 * buttons, a Void-unassayed button and the auto-collect toggle. Reads the held satchel live, so it updates as the
 * server applies each action.
 */
public final class SatchelScreen extends Screen {
    private static final int W = 248, ROW = 22, ROWS = 7, TOP = 46;

    private final InteractionHand hand;
    private int scroll;
    private int shownHash = 0;
    private final List<Button> rowButtons = new ArrayList<>();

    public SatchelScreen(InteractionHand hand) {
        super(Component.translatable("item.cultivar.seed_satchel"));
        this.hand = hand;
    }

    private ItemStack satchel() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private List<SeedSatchelItem.Entry> entries() {
        ItemStack s = satchel();
        return s.getItem() instanceof SeedSatchelItem ? SeedSatchelItem.entries(s) : List.of();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - (TOP + ROWS * ROW + 12)) / 2;
    }

    private void send(int index, Action action) {
        PacketDistributor.sendToServer(new SatchelActionPayload(hand, index, action));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        rowButtons.clear();
        boolean collecting = !satchel().has(ToolsModule.SATCHEL_NO_COLLECT.get());
        addRenderableWidget(Button.builder(Component.translatable(collecting ? "gui.cultivar.satchel.collect_on" : "gui.cultivar.satchel.collect_off"),
                        b -> send(0, Action.TOGGLE_COLLECT))
                .bounds(left() + 6, top() + 20, 116, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.cultivar.satchel.collect_tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cultivar.satchel.void_unassayed"), b -> send(0, Action.VOID_UNASSAYED))
                .bounds(left() + W - 122, top() + 20, 116, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.cultivar.satchel.void_unassayed_tip"))).build());
        List<SeedSatchelItem.Entry> list = entries();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - ROWS)));
        for (int r = 0; r < ROWS && scroll + r < list.size(); r++) {
            int index = scroll + r, y = top() + TOP + r * ROW;
            rowButtons.add(addRenderableWidget(Button.builder(Component.translatable("gui.cultivar.satchel.take"), b -> send(index, Action.TAKE))
                    .bounds(left() + W - 86, y, 38, 18).build()));
            rowButtons.add(addRenderableWidget(Button.builder(Component.translatable("gui.cultivar.satchel.void").withStyle(ChatFormatting.RED),
                            b -> send(index, Action.VOID))
                    .bounds(left() + W - 46, y, 40, 18).build()));
        }
        shownHash = stateHash();
    }

    private int stateHash() {
        return entries().hashCode() * 31 + scroll * 7 + (satchel().has(ToolsModule.SATCHEL_NO_COLLECT.get()) ? 1 : 0);
    }

    @Override
    public void tick() {
        if (!(satchel().getItem() instanceof SeedSatchelItem)) {
            onClose(); // satchel left the hand
            return;
        }
        if (stateHash() != shownHash) {
            rebuild();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int max = Math.max(0, entries().size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top(), h = TOP + ROWS * ROW + 12;
        g.fill(x, y, x + W, y + h, 0xE0201A14);
        g.renderOutline(x, y, W, h, 0xFFC4943D);
        super.render(g, mouseX, mouseY, partialTick);
        List<SeedSatchelItem.Entry> list = entries();
        int total = list.stream().mapToInt(SeedSatchelItem.Entry::count).sum();
        g.drawString(font, title.copy().append(" " + total + "/" + SeedSatchelItem.CAPACITY), x + 6, y + 6, 0xF4DF8D, false);
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("gui.cultivar.satchel.empty"), x + 6, y + TOP + 4, 0xA0A0A0, false);
        }
        Genetics.Expression expr = Genetics.current();
        for (int r = 0; r < ROWS && scroll + r < list.size(); r++) {
            SeedSatchelItem.Entry e = list.get(scroll + r);
            int ry = y + TOP + r * ROW;
            if ((r & 1) == 0) {
                g.fill(x + 3, ry - 2, x + W - 3, ry + ROW - 2, 0x22FFFFFF);
            }
            ItemStack icon = CultivarSeedItem.of(e.genome(), e.assayed());
            g.renderItem(icon, x + 6, ry);
            g.drawString(font, "x" + e.count(), x + 24, ry + 5, 0xFFFFFF, false);
            g.drawString(font, stats(e, expr), x + 44, ry + 5, 0xFFFFFF, false);
            if (mouseX >= x + 6 && mouseX < x + 22 && mouseY >= ry && mouseY < ry + 16) {
                g.renderTooltip(font, icon, mouseX, mouseY);
            }
        }
        if (list.size() > ROWS) {
            g.drawString(font, (scroll + 1) + "-" + Math.min(list.size(), scroll + ROWS) + " / " + list.size(),
                    x + 6, y + h - 10, 0x808080, false);
        }
    }

    /** "G 3  Gr 5  S 2  V 4" in each stat's colour, or "not assayed". */
    private static Component stats(SeedSatchelItem.Entry e, Genetics.Expression expr) {
        if (!e.assayed()) {
            return Component.translatable("gui.cultivar.satchel.unassayed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        MutableComponent out = Component.empty();
        for (var holder : CoreModule.STATS.getEntries()) {
            ResourceKey<Stat> key = holder.getKey();
            Stat stat = CultivarRegistries.STAT.get(key);
            int value = Genetics.expressed(e.genome(), key, expr);
            String label = Component.translatable("stat.cultivar." + key.location().getPath() + ".short").getString();
            out.append(Component.literal(label + " " + value + "  ").withStyle(s -> s.withColor(stat == null ? 0xFFFFFF : stat.color() & 0xFFFFFF)));
        }
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
