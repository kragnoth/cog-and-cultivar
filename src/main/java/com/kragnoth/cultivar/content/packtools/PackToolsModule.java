package com.kragnoth.cultivar.content.packtools;

import com.kragnoth.cultivar.core.module.CultivarModule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Pack-maker tooling. {@code /cultivar export [samples]} (op level 2) writes {@code cultivar_export/} in the
 * server (or game) directory: crops.json plus cultivar_scaffold.py. See docs/PACKMAKERS.md.
 */
public final class PackToolsModule implements CultivarModule {
    @Override
    public String id() {
        return "packtools";
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("export")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> export(ctx, 200))
                .then(Commands.argument("samples", IntegerArgumentType.integer(1, 10_000))
                        .executes(ctx -> export(ctx, IntegerArgumentType.getInteger(ctx, "samples")))));
    }

    private static int export(CommandContext<CommandSourceStack> ctx, int samples) {
        CommandSourceStack source = ctx.getSource();
        try {
            CropExporter.Result r = CropExporter.export(source.getServer(), samples);
            source.sendSuccess(() -> Component.translatableWithFallback("commands.cultivar.export.done", "Exported %s crops (%s not yet covered) to %s",
                    r.crops(), r.uncovered(), r.dir().toAbsolutePath().toString()), true);
            return r.crops();
        } catch (Exception e) {
            source.sendFailure(Component.translatableWithFallback("commands.cultivar.export.failed", "Export failed: %s", String.valueOf(e.getMessage())));
            return 0;
        }
    }
}
