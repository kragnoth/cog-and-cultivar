package com.kragnoth.cultivar.content.packtools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.soil.Soil;
import com.kragnoth.cultivar.mixin.BushBlockInvoker;
import com.kragnoth.cultivar.mixin.StemBlockAccessor;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes {@code cultivar_export/crops.json}: every crop-like block in the game (vanilla and modded) with its seed,
 * max age, accepted soils and sampled mature yields, plus the current Cultivar plants, mutations and soils.
 * Pack makers feed it to the shipped {@code cultivar_scaffold.py}, which is written next to it.
 * Server-side only; reads registries and loot tables, never touches the world.
 */
public final class CropExporter {
    /** Soils probed with each bush's own placement rule. Cultivar soil blocks are added at runtime. */
    private static final List<Block> PROBE_SOILS = List.of(Blocks.FARMLAND, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL,
            Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.CLAY, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
            Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.NETHERRACK, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM,
            Blocks.END_STONE, Blocks.STONE);

    private CropExporter() {}

    public record Result(Path dir, int crops, int uncovered) {}

    public static Result export(MinecraftServer server, int samples) throws IOException {
        ServerLevel level = server.overworld();
        var access = server.registryAccess();
        Registry<Plant> plants = access.registryOrThrow(CultivarRegistries.PLANT);
        Registry<Soil> soils = access.registryOrThrow(CultivarRegistries.SOIL);

        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("cultivar_version", ModList.get().getModContainerById(Cultivar.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        root.addProperty("exported", Instant.now().toString());

        List<Block> probes = new java.util.ArrayList<>(PROBE_SOILS);
        soils.stream().forEach(s -> s.blocks().stream().map(Holder::value).filter(b -> !probes.contains(b)).forEach(probes::add));

        JsonArray crops = new JsonArray();
        int uncovered = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            Optional<IntegerProperty> age = ageProperty(block);
            if (!isCrop(block, age)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            BlockState mature = age.map(a -> block.defaultBlockState().setValue(a, max(a))).orElse(block.defaultBlockState());
            Item seed = seedOf(block, level, mature);

            JsonObject crop = new JsonObject();
            crop.addProperty("id", id.toString());
            crop.addProperty("mod", id.getNamespace());
            crop.addProperty("kind", kindOf(block));
            crop.addProperty("max_age", age.map(CropExporter::max).orElse(0));
            crop.addProperty("seed", BuiltInRegistries.ITEM.getKey(seed).toString());

            JsonArray on = new JsonArray();
            if (block instanceof BushBlock) {
                for (Block soil : probes) {
                    try {
                        if (((BushBlockInvoker) block).cultivar$mayPlaceOn(soil.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                            on.add(BuiltInRegistries.BLOCK.getKey(soil).toString());
                        }
                    } catch (RuntimeException ignored) {
                        // Some modded rules need a real level; leave them for the pack maker.
                    }
                }
            }
            crop.add("placeable_on", on);
            Block harvest = block;
            BlockState harvestState = mature;
            if (block instanceof StemBlock) {
                // A stem's own loot is its seeds; the crop is the fruit block it grows.
                Optional<Block> fruit = BuiltInRegistries.BLOCK.getOptional(((StemBlockAccessor) block).cultivar$fruit());
                if (fruit.isPresent()) {
                    harvest = fruit.get();
                    harvestState = harvest.defaultBlockState();
                    crop.addProperty("fruit", BuiltInRegistries.BLOCK.getKey(harvest).toString());
                }
            }
            crop.add("yields", yields(server, level, harvest, harvestState, samples));

            Optional<ResourceLocation> covered = plants.entrySet().stream()
                    .filter(e -> e.getValue().seeds().map(set -> set.contains(seed.builtInRegistryHolder())).orElse(false))
                    .map(e -> e.getKey().location()).findFirst();
            covered.ifPresent(p -> crop.addProperty("covered_by", p.toString()));
            if (covered.isEmpty()) {
                uncovered++;
            }
            crops.add(crop);
        }
        root.add("crops", crops);
        root.add("plants", encodeAll(plants, Plant.CODEC, access));
        root.add("mutations", encodeAll(access.registryOrThrow(CultivarRegistries.MUTATION), Mutation.CODEC, access));
        root.add("soils", encodeAll(soils, Soil.CODEC, access));

        Path dir = server.getServerDirectory().resolve("cultivar_export");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("crops.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
        try (InputStream script = CropExporter.class.getResourceAsStream("/cultivar_tools/cultivar_scaffold.py")) {
            if (script != null) {
                Files.write(dir.resolve("cultivar_scaffold.py"), script.readAllBytes());
            }
        }
        return new Result(dir, crops.size(), uncovered);
    }

    private static Optional<IntegerProperty> ageProperty(Block block) {
        for (Property<?> p : block.getStateDefinition().getProperties()) {
            if (p instanceof IntegerProperty ip && p.getName().equals("age")) {
                return Optional.of(ip);
            }
        }
        return Optional.empty();
    }

    private static int max(IntegerProperty p) {
        return p.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Aged bushes and crops (covers most modded crops), the vanilla oddballs, and small mushrooms. */
    private static boolean isCrop(Block block, Optional<IntegerProperty> age) {
        if (block instanceof SaplingBlock) {
            return false;
        }
        if (block instanceof MushroomBlock) {
            return true;
        }
        if (age.isEmpty()) {
            return false;
        }
        return block instanceof BushBlock || block instanceof CropBlock || block instanceof CactusBlock
                || block instanceof SugarCaneBlock || block instanceof CocoaBlock || block instanceof BambooStalkBlock
                || block instanceof GrowingPlantHeadBlock || block.defaultBlockState().is(BlockTags.CROPS);
    }

    private static String kindOf(Block block) {
        Class<?> c = block.getClass();
        // Report the nearest vanilla type so the script can apply sensible defaults to modded subclasses.
        while (c != null && !c.getName().startsWith("net.minecraft.")) {
            c = c.getSuperclass();
        }
        return c == null ? block.getClass().getSimpleName() : c.getSimpleName();
    }

    private static Item seedOf(Block block, ServerLevel level, BlockState state) {
        try {
            ItemStack clone = block.getCloneItemStack(level, BlockPos.ZERO, state);
            if (!clone.isEmpty()) {
                return clone.getItem();
            }
        } catch (RuntimeException ignored) {
            // Fall through to the block's own item.
        }
        Item item = block.asItem();
        return item == Items.AIR ? Items.AIR : item;
    }

    /** Rolls the mature block's loot table {@code samples} times (no tool, no fortune) and records min/max/avg. */
    private static JsonArray yields(MinecraftServer server, ServerLevel level, Block block, BlockState mature, int samples) {
        record Stat(int[] minMax, double[] total) {}
        Map<Item, Stat> stats = new LinkedHashMap<>();
        LootTable table = server.reloadableRegistries().getLootTable(block.getLootTable());
        for (int i = 0; i < samples; i++) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, mature)
                    .create(LootContextParamSets.BLOCK);
            Map<Item, Integer> roll = new LinkedHashMap<>();
            try {
                table.getRandomItems(params).stream().filter(s -> !s.isEmpty())
                        .forEach(s -> roll.merge(s.getItem(), s.getCount(), Integer::sum));
            } catch (RuntimeException e) {
                break;
            }
            for (Item item : stats.keySet()) {
                roll.putIfAbsent(item, 0);
            }
            final int done = i;
            roll.forEach((item, n) -> {
                Stat s = stats.computeIfAbsent(item, k -> new Stat(new int[]{done > 0 ? 0 : Integer.MAX_VALUE, 0}, new double[1]));
                s.minMax()[0] = Math.min(s.minMax()[0], n);
                s.minMax()[1] = Math.max(s.minMax()[1], n);
                s.total()[0] += n;
            });
        }
        JsonArray out = new JsonArray();
        stats.forEach((item, s) -> {
            JsonObject y = new JsonObject();
            y.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
            y.addProperty("min", s.minMax()[0]);
            y.addProperty("max", s.minMax()[1]);
            y.addProperty("avg", Math.round(s.total()[0] / samples * 100) / 100.0);
            out.add(y);
        });
        return out;
    }

    private static <T> JsonObject encodeAll(Registry<T> registry, com.mojang.serialization.Codec<T> codec,
                                            net.minecraft.core.HolderLookup.Provider access) {
        JsonObject out = new JsonObject();
        var ops = access.createSerializationContext(JsonOps.INSTANCE);
        registry.entrySet().forEach(e -> codec.encodeStart(ops, e.getValue()).result()
                .ifPresent(json -> out.add(e.getKey().location().toString(), json)));
        return out;
    }
}
