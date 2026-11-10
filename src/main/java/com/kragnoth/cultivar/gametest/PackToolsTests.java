package com.kragnoth.cultivar.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.packtools.CropExporter;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;

@GameTestHolder(Cultivar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackToolsTests {
    private PackToolsTests() {}

    @GameTest(template = "empty")
    public static void exportFindsVanillaCrops(GameTestHelper h) throws Exception {
        CropExporter.Result r = CropExporter.export(h.getLevel().getServer(), 20);
        JsonObject root = JsonParser.parseString(Files.readString(r.dir().resolve("crops.json"))).getAsJsonObject();
        boolean wheat = false, wart = false;
        for (var e : root.getAsJsonArray("crops")) {
            JsonObject c = e.getAsJsonObject();
            if (c.get("id").getAsString().equals("minecraft:wheat")) {
                wheat = c.has("covered_by") && c.get("seed").getAsString().equals("minecraft:wheat_seeds")
                        && c.getAsJsonArray("placeable_on").toString().contains("minecraft:farmland")
                        && c.getAsJsonArray("yields").toString().contains("minecraft:wheat\"");
            }
            if (c.get("id").getAsString().equals("minecraft:nether_wart")) {
                wart = c.getAsJsonArray("placeable_on").toString().contains("minecraft:soul_sand");
            }
        }
        h.assertTrue(wheat, "wheat exported with seed, soil, yield and coverage");
        h.assertTrue(wart, "nether wart's soil probed through its own placement rule");
        h.assertTrue(Files.exists(r.dir().resolve("cultivar_scaffold.py")), "scaffold script shipped next to the export");
        h.assertTrue(root.getAsJsonObject("mutations").size() >= 15, "mutations included");
        h.succeed();
    }
}
