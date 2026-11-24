package com.kragnoth.cultivar.content.devtest;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.bed.PlanterBedBlockEntity;
import com.kragnoth.cultivar.content.bed.PlanterBedModule;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.content.field.FieldCropBlockEntity;
import com.kragnoth.cultivar.content.field.FieldCropModule;
import com.kragnoth.cultivar.content.irrigation.IrrigationModule;
import com.kragnoth.cultivar.content.irrigation.OllaBlock;
import com.kragnoth.cultivar.content.soil.Amendment;
import com.kragnoth.cultivar.content.soil.SoilAmendments;
import com.kragnoth.cultivar.content.tools.SeedSatchelItem;
import com.kragnoth.cultivar.content.trial.TrialModule;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /test cultivarshowcase}: builds a labelled gallery of everything that only a person can judge (rendering,
 * tints, overlays, glow, Jade and goggles text) so one walk past it covers the visual checks. Rows, front to back:
 * every plant at every stage as field crops; every plant ripe on trellises; every blight; cloches; conditioner
 * dusting; the Olla, a grafting frame, the machines and a chest kit of every Cultivar item. Random ticks are paused
 * while it stands so nothing grows; {@code /test cultivarclear} removes it and restores them.
 */
final class Showcase {
    private static final int HEIGHT = 7;
    @Nullable
    private static ServerLevel builtIn;
    @Nullable
    private static AABB bounds;
    private static int savedTickSpeed = -1;
    private static final List<BlockPos> amended = new ArrayList<>();

    private Showcase() {}

    static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        clear(source, false);
        List<Holder.Reference<Plant>> plants = level.registryAccess().registryOrThrow(CultivarRegistries.PLANT).holders()
                .sorted((a, b) -> a.key().location().toString().compareTo(b.key().location().toString())).toList();
        List<Holder.Reference<Weed>> weeds = level.registryAccess().registryOrThrow(CultivarRegistries.WEED).holders().toList();
        BlockPos o = BlockPos.containing(source.getPosition()).offset(3, 0, 0);
        int width = Math.max(2 * plants.size(), 40) + 4;
        int depth = 36;
        builtIn = level;
        bounds = new AABB(o.getX() - 3, o.getY() - 3, o.getZ() - 2, o.getX() + width, o.getY() + HEIGHT, o.getZ() + depth);

        // Pause growth so the stages stay put; restored by /test cultivarclear.
        var rule = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
        savedTickSpeed = rule.get();
        rule.set(0, level.getServer());

        prepareGround(level, o, width, depth);
        Holder<Plant> wheat = plants.stream().filter(p -> p.key().location().equals(ResourceLocation.withDefaultNamespace("wheat")))
                .findFirst().map(p -> (Holder<Plant>) p).orElse(plants.getFirst());

        // Row 1: field crops, every plant at every stage (stage = rows going back).
        sign(level, o.offset(-2, 0, 0), "Field crops", "every plant,", "every stage", "(textures, tints)");
        for (int i = 0; i < plants.size(); i++) {
            Plant plant = plants.get(i).value();
            for (int s = 0; s <= plant.matureStage() && s < 9; s++) {
                fieldCrop(level, o.offset(2 * i, 0, s), plants.get(i), s);
            }
        }
        // Row 2: every plant ripe on a trellis (stacked for tall plants).
        int z = 10;
        sign(level, o.offset(-2, 0, z), "Trellis crops", "every plant ripe", "(frame + plant)", "");
        for (int i = 0; i < plants.size(); i++) {
            trellisCrop(level, o.offset(2 * i, 0, z), plants.get(i), plants.get(i).value().matureStage(), null);
        }
        // Row 3: blights at full growth.
        z = 15;
        sign(level, o.offset(-2, 0, z), "Blights", "full grown", "", "");
        for (int i = 0; i < weeds.size(); i++) {
            BlockPos p = o.offset(3 * i, 0, z);
            trellisCrop(level, p, null, 0, null);
            if (level.getBlockEntity(p) instanceof CropBlockEntity crop) {
                crop.setWeed(weeds.get(i), weeds.get(i).value().stages() - 1);
            }
        }
        // Row 4: cloches on ripe wheat; iron trellis.
        z = 19;
        sign(level, o.offset(-2, 0, z), "Cloches", "bell, shade, lamp,", "none, iron trellis", "(glow, Jade light)");
        Item[] cloches = {ClocheModule.BELL_CLOCHE.get(), ClocheModule.SHADE_CLOCHE.get(), ClocheModule.LAMP_CLOCHE.get()};
        for (int i = 0; i < cloches.length; i++) {
            trellisCrop(level, o.offset(2 * i, 0, z), wheat, wheat.value().matureStage(),
                    ((com.kragnoth.cultivar.content.cloche.BellClocheItem) cloches[i]));
        }
        trellisCrop(level, o.offset(6, 0, z), wheat, wheat.value().matureStage(), null);
        level.setBlock(o.offset(8, 0, z), CropModule.IRON_TRELLIS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(o.offset(8, 0, z)) instanceof CropBlockEntity crop) {
            crop.setDisplay(Genome.base(wheat), wheat.value().matureStage());
        }
        // Row 5: conditioner dusting at 1 and 2 steps: on a trellis, under a field crop, and on bare farmland (puffs).
        z = 23;
        sign(level, o.offset(-2, 0, z), "Conditioners", "+/-acid, nutrients,", "humidity x1, x2:", "trellis/field/bare");
        Amendment[] kinds = {new Amendment(1, 0, 0), new Amendment(-1, 0, 0), new Amendment(0, 1, 0),
                new Amendment(0, -1, 0), new Amendment(0, 0, 1), new Amendment(0, 0, -1)};
        int col = 0;
        for (Amendment k : kinds) {
            for (int steps = 1; steps <= 2; steps++, col++) {
                Amendment a = new Amendment(k.acidity() * steps, k.nutrients() * steps, k.humidity() * steps);
                BlockPos t = o.offset(2 * col, 0, z), f = o.offset(2 * col, 0, z + 2), b = o.offset(2 * col, 0, z + 4);
                trellisCrop(level, t, wheat, 2, null);
                fieldCrop(level, f, wheat, 2);
                for (BlockPos p : new BlockPos[]{t, f, b}) {
                    SoilAmendments.set(level, p.below(), a);
                    amended.add(p.below());
                }
            }
        }
        // Row 6: Olla under a 9x9 field (dark, moist farmland, no water), grafting frame, machines, item kit.
        z = 30;
        sign(level, o.offset(-2, 0, z), "Olla, frame,", "machines, kit", "(moist inside 4,", "dirt past it)");
        // Dry farmland 4 either side of a buried, filled Olla, plus two columns past its reach. Each gets vanilla's own
        // random tick (through the moisture mixin): inside the reach it turns moist, past it it dries back to dirt.
        BlockPos centre = o.offset(4, -1, z + 4);
        for (int dx = -4; dx <= 6; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlock(centre.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0), Block.UPDATE_ALL);
            }
        }
        level.setBlock(centre.below(), IrrigationModule.OLLA.get().defaultBlockState().setValue(OllaBlock.FILLED, true), Block.UPDATE_ALL);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-4, 0, -4), centre.offset(6, 0, 4))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof FarmBlock) {
                s.randomTick(level, p.immutable(), level.random);
            }
        }
        BlockPos frame = o.offset(14, 0, z);
        trellisCrop(level, frame, wheat, wheat.value().matureStage(), null);
        level.setBlock(frame.east(), CropModule.TIMBER_TRELLIS.get().defaultBlockState().setValue(TrellisBlock.CROSS, true), Block.UPDATE_ALL);
        trellisCrop(level, frame.east(2), wheat, wheat.value().matureStage(), null);
        level.setBlock(o.offset(19, 0, z), PlanterBedModule.BED.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(o.offset(19, 0, z)) instanceof PlanterBedBlockEntity bed) {
            bed.setSoil(Blocks.FARMLAND.defaultBlockState());
        }
        level.setBlock(o.offset(21, 0, z), TrialModule.TRIAL_BED.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.offset(23, 0, z), AssayModule.ENGINE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.offset(25, 0, z), IrrigationModule.SPRINKLER.get().defaultBlockState(), Block.UPDATE_ALL);
        kit(level, o.offset(14, 0, z + 4), plants);

        source.sendSuccess(() -> Component.literal("Cultivar showcase built east of you. Random ticks are paused until "
                + "/test cultivarclear. Walk the rows front to back; signs say what each shows.").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static void prepareGround(ServerLevel level, BlockPos o, int width, int depth) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-3, -3, -2), o.offset(width, HEIGHT, depth))) {
            int dy = p.getY() - o.getY();
            BlockState s = dy < -1 ? Blocks.STONE.defaultBlockState() : dy == -1 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(p, s, Block.UPDATE_CLIENTS);
        }
    }

    private static void soil(ServerLevel level, BlockPos crop) {
        level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
    }

    private static void fieldCrop(ServerLevel level, BlockPos at, Holder<Plant> plant, int stage) {
        soil(level, at);
        level.setBlock(at, FieldCropModule.FIELD_CROP.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof FieldCropBlockEntity crop) {
            crop.plant().set(Genome.base(plant), stage, true);
        }
    }

    private static void trellisCrop(ServerLevel level, BlockPos at, @Nullable Holder<Plant> plant, int stage,
                                     @Nullable com.kragnoth.cultivar.content.cloche.BellClocheItem cloche) {
        soil(level, at);
        int height = plant == null ? 1 : Math.max(1, plant.value().blocksAt(stage));
        for (int i = height - 1; i >= 0; i--) { // top first so each segment finds its base already there
            BlockState t = CropModule.TIMBER_TRELLIS.get().defaultBlockState();
            level.setBlock(at.above(i), i == height - 1 && cloche != null ? cloche.fit(t) : t, Block.UPDATE_ALL);
        }
        if (plant != null && level.getBlockEntity(at) instanceof CropBlockEntity crop) {
            crop.setDisplay(Genome.base(plant), stage);
        }
    }

    private static void sign(ServerLevel level, BlockPos at, String... lines) {
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            SignText text = sign.getFrontText();
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                text = text.setMessage(i, Component.literal(lines[i]));
            }
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Chests holding one of every Cultivar item, an assayed seed of every plant, and a filled satchel. */
    private static void kit(ServerLevel level, BlockPos at, List<Holder.Reference<Plant>> plants) {
        List<ItemStack> stacks = new ArrayList<>();
        BuiltInRegistries.ITEM.entrySet().stream()
                .filter(e -> e.getKey().location().getNamespace().equals(com.kragnoth.cultivar.Cultivar.MOD_ID))
                .map(e -> new ItemStack(e.getValue()))
                .filter(s -> !(s.getItem() instanceof CultivarSeedItem))
                .forEach(stacks::add);
        ItemStack satchel = new ItemStack(com.kragnoth.cultivar.content.tools.ToolsModule.SEED_SATCHEL.get());
        if (!plants.isEmpty()) {
            SeedSatchelItem.insert(satchel, CultivarSeedItem.of(Genome.base(plants.getFirst()), true).copyWithCount(16));
        }
        stacks.add(satchel);
        plants.forEach(p -> stacks.add(CultivarSeedItem.of(Genome.base(p), true).copyWithCount(8)));
        for (int c = 0; c * 27 < stacks.size(); c++) {
            BlockPos p = at.offset(2 * c, 0, 0); // a gap between chests so they don't join
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
                for (int i = 0; i < 27 && c * 27 + i < stacks.size(); i++) {
                    chest.setItem(i, stacks.get(c * 27 + i));
                }
            }
        }
    }

    /** Removes the showcase (flat stone floor left behind) and restores random ticks. */
    static int clear(CommandSourceStack source, boolean say) {
        if (builtIn == null || bounds == null) {
            if (say) {
                source.sendSuccess(() -> Component.literal("No showcase to clear."), false);
            }
            return 0;
        }
        ServerLevel level = builtIn;
        for (BlockPos p : amended) {
            SoilAmendments.set(level, p, Amendment.NONE);
        }
        amended.clear();
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ), max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (int y = max.getY(); y >= min.getY(); y--) { // top down, so plants go before their soil
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(min.getX(), y, min.getZ()), new BlockPos(max.getX(), y, max.getZ()))) {
                if (level.getBlockEntity(p) instanceof Container c) {
                    c.clearContent();
                }
                BlockState s = y < min.getY() + 2 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                level.setBlock(p, s, Block.UPDATE_CLIENTS);
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, bounds.inflate(2)).forEach(ItemEntity::discard);
        if (savedTickSpeed >= 0) {
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(savedTickSpeed, level.getServer());
        }
        savedTickSpeed = -1;
        builtIn = null;
        bounds = null;
        if (say) {
            source.sendSuccess(() -> Component.literal("Showcase removed; random ticks restored."), false);
        }
        return 1;
    }
}
