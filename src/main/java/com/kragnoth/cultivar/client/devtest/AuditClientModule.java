package com.kragnoth.cultivar.client.devtest;

import com.kragnoth.cultivar.client.ClientModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /test cultivaraudit}: a client command (other {@code /test cultivar...} commands still go to the server)
 * that checks the client's loaded resources and prints a summary, with the full report in
 * {@code logs/cultivar-audit.txt}.
 */
public final class AuditClientModule implements ClientModule {
    private static final int SHOWN = 12;

    @Override
    public String id() {
        return "devtest";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(Commands.literal("test")
                .then(Commands.literal("cultivaraudit").executes(ctx -> {
                    report(ResourceAudit.run());
                    return 1;
                }))));
    }

    private static void report(ResourceAudit a) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Path file = FMLPaths.GAMEDIR.get().resolve("logs/cultivar-audit.txt");
        List<String> lines = new ArrayList<>();
        lines.add("Cultivar resource audit, " + LocalDateTime.now());
        lines.add("");
        lines.add("ERRORS (" + a.errors.size() + ")");
        a.errors.forEach(s -> lines.add("  " + s));
        lines.add("");
        lines.add("WARNINGS (" + a.warnings.size() + ")");
        a.warnings.forEach(s -> lines.add("  " + s));
        lines.add("");
        lines.add("CHECKED");
        a.info.forEach(s -> lines.add("  " + s));
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Couldn't write " + file + ": " + e.getMessage()).withStyle(ChatFormatting.RED));
        }

        player.sendSystemMessage(Component.literal("Cultivar audit: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(a.errors.size() + " errors").withStyle(a.errors.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(", " + a.warnings.size() + " warnings").withStyle(a.warnings.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                .append(Component.literal(" (" + String.join("; ", a.info) + ")").withStyle(ChatFormatting.GRAY)));
        a.errors.stream().limit(SHOWN).forEach(s -> player.sendSystemMessage(Component.literal(" ✗ " + s).withStyle(ChatFormatting.RED)));
        if (a.errors.size() > SHOWN) {
            player.sendSystemMessage(Component.literal(" ... " + (a.errors.size() - SHOWN) + " more errors in the report").withStyle(ChatFormatting.RED));
        }
        player.sendSystemMessage(Component.literal(" [open full report]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toAbsolutePath().toString()))));
    }
}
