package com.kragnoth.cultivar.content.devtest;

import com.kragnoth.cultivar.core.module.CultivarModule;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * In-game test commands (op level 2), under vanilla's {@code /test} like Creative Engineering's demos, so one
 * session covers what CI can't see:
 * <ul>
 *   <li>{@code /test cultivar [filter]}: every Cultivar game test, run live in this world with this modpack;</li>
 *   <li>{@code /test cultivarshowcase}: a labelled gallery of everything to judge by eye;</li>
 *   <li>{@code /test cultivarharvester [regrow]}: bearing rotation-farm stations with Create harvesters and glued
 *       barrels over every plant (field and trellis); {@code regrow} ripens them for another pass;</li>
 *   <li>{@code /test cultivarchecklist}: the by-eye checks, each with a click that sets it up;</li>
 *   <li>{@code /test cultivarclear}: removes test plots and the showcase, restores time and random ticks;</li>
 *   <li>{@code /test cultivaraudit} (client side): textures, models, names, Ponder, JEI, creative tab.</li>
 * </ul>
 * Meant for a creative test world: tests and the showcase flatten the ground next to you.
 */
public final class DevTestModule implements CultivarModule {
    @Override
    public String id() {
        return "devtest";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(DevTestModule::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> LiveTestRunner.serverTick());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("test")
                .then(Commands.literal("cultivar").requires(s -> s.hasPermission(2))
                        .executes(ctx -> LiveTestRunner.run(ctx.getSource(), null))
                        .then(Commands.argument("filter", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(LiveTestRunner.tests(null).stream()
                                        .map(t -> t.batchName().equals("defaultBatch") ? t.testName() : t.batchName()).distinct(), b))
                                .executes(ctx -> LiveTestRunner.run(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))))
                .then(Commands.literal("cultivarshowcase").requires(s -> s.hasPermission(2))
                        .executes(ctx -> Showcase.build(ctx.getSource())))
                .then(Commands.literal("cultivarharvester").requires(s -> s.hasPermission(2))
                        .executes(ctx -> HarvesterRig.build(ctx.getSource()))
                        .then(Commands.literal("regrow").executes(ctx -> HarvesterRig.regrow(ctx.getSource()))))
                .then(Commands.literal("cultivarclear").requires(s -> s.hasPermission(2))
                        .executes(ctx -> LiveTestRunner.clear(ctx.getSource(), true) + Showcase.clear(ctx.getSource(), true)
                                + HarvesterRig.clear(ctx.getSource(), true)))
                .then(Commands.literal("cultivarchecklist").requires(s -> s.hasPermission(2))
                        .executes(DevTestModule::checklist)));
    }

    /** What a game test can't judge: each line says what to look for, and clicking it sets that up. */
    private static int checklist(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        s.sendSystemMessage(Component.literal("Cultivar by-eye checklist (click a line to set it up):").withStyle(ChatFormatting.GOLD));
        line(s, "/test cultivaraudit", "Resource audit: no missing textures, models, names; Ponder and JEI loaded");
        line(s, "/test cultivar", "All game tests pass in this world (with every other mod loaded)");
        line(s, "/test cultivarshowcase", "Gallery: stages, trellis + field rendering, blight art, cloche glow, dusting, Olla");
        line(s, "/give @s cultivar:seed_satchel", "Satchel: right-click opens the screen; Take / Void / Void unassayed / collect toggle");
        line(s, "/give @s cultivar:botanical_ledger", "Ledger: opens, lists known plants; JEI hides unknown crosses");
        line(s, "/give @s cultivar:trial_bed", "Ponder: hover a Cultivar item, hold W; scenes play without errors");
        line(s, "/time set midnight", "Shade/Lamp Cloche: Jade shows the light the crop reads; lamp glows at night");
        line(s, "/give @s cultivar:agricultural_lime 16", "Conditioner: use on soil or trellis; dusting appears, puffs, Jade 'Amended' line");
        line(s, "/give @s create:goggles", "Goggles on a crop: stats only once assayed; soil, light and amendment lines");
        line(s, "/test cultivarharvester", "Harvester stations, every plant: produce lands in the arm barrels; regrow for another pass");
        return 1;
    }

    private static void line(CommandSourceStack s, String command, String what) {
        MutableComponent c = Component.literal(" ☐ ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(what).withStyle(style -> style.withColor(ChatFormatting.WHITE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
        s.sendSystemMessage(c);
    }
}
