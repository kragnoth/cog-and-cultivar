package com.kragnoth.cultivar.client.loupe;

import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropInfo;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/** HUD panel beside the crosshair while a Botanist's Loupe is held and a crop is targeted. */
public final class LoupeOverlay {
    private static final int BAR_WIDTH = 48;
    private static final int PANEL_BG = 0xB0141210;
    private static final int PANEL_EDGE = 0xFFC4943D;   // brass ramp step 3

    private LoupeOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || !(mc.player.getMainHandItem().is(ToolsModule.BOTANISTS_LOUPE.get())
                || mc.player.getOffhandItem().is(ToolsModule.BOTANISTS_LOUPE.get()))) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof CropBlockEntity crop)) {
            return;
        }
        Font font = mc.font;
        Genome genome = crop.genome();
        List<Component> lines = CropInfo.lines(crop);
        int statCount = genome == null ? 0 : CoreModule.STATS.getEntries().size();
        // Lines after the title and the per-stat lines (soil, influences, weed); stats are drawn as bars instead.
        List<Component> extra = lines.subList(Math.min(lines.size(), 1 + statCount), lines.size());

        int width = 150;
        for (Component c : extra) {
            width = Math.max(width, font.width(c) + 12);
        }
        int height = 8 + 12 + statCount * 10 + extra.size() * 10 + 4;
        int x = g.guiWidth() / 2 + 14;
        int y = g.guiHeight() / 2 - height / 2;

        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_EDGE);
        g.fill(x, y, x + width, y + height, PANEL_BG);
        int ty = y + 5;
        g.drawString(font, lines.getFirst(), x + 6, ty, 0xFFFFFF);
        ty += 12;
        if (genome != null) {
            for (var stat : CoreModule.STATS.getEntries()) {
                int value = Genetics.expressed(genome, stat.getKey());
                int max = Genetics.max(stat.getKey());
                g.drawString(font, Component.translatable("stat.cultivar." + stat.getId().getPath()), x + 6, ty, 0xBBBBBB);
                int bx = x + width - BAR_WIDTH - 22;
                g.fill(bx, ty + 2, bx + BAR_WIDTH, ty + 7, 0xFF2A2622);
                g.fill(bx, ty + 2, bx + BAR_WIDTH * value / Math.max(1, max), ty + 7, stat.get().color());
                g.drawString(font, String.valueOf(value), bx + BAR_WIDTH + 4, ty, 0xDDDDDD);
                ty += 10;
            }
        }
        for (Component c : extra) {
            g.drawString(font, c, x + 6, ty, 0xFFFFFF);
            ty += 10;
        }
    }
}
