package com.kragnoth.cultivar.client.devtest;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.api.plant.PlantRender;
import com.kragnoth.cultivar.api.plant.Weed;
import com.kragnoth.cultivar.content.core.CoreModule;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side resource audit ({@code /test cultivaraudit}): finds what would only show up as a pink-black cube, a
 * raw translation key or a broken screen, without walking around. Errors are things that are visibly broken;
 * warnings are gaps worth a look (an item without a Create tooltip, a plant without its own name).
 */
final class ResourceAudit {
    final List<String> errors = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    final List<String> info = new ArrayList<>();
    private final Minecraft mc = Minecraft.getInstance();
    private final BakedModel missingModel = mc.getModelManager().getMissingModel();
    private final RandomSource rand = RandomSource.create(42L);

    private ResourceAudit() {}

    static ResourceAudit run() {
        ResourceAudit a = new ResourceAudit();
        a.blocks();
        a.items();
        a.plants();
        a.weeds();
        a.overlays();
        a.names();
        a.ponder();
        a.creativeTab();
        if (ModList.get().isLoaded("jei")) {
            JeiAudit.check(a);
        } else {
            a.info.add("JEI not installed: JEI pages not checked");
        }
        return a;
    }

    private static boolean ours(ResourceLocation id) {
        return id.getNamespace().equals(Cultivar.MOD_ID);
    }

    // ---------------------------------------------------------------- models and textures

    private void blocks() {
        int states = 0;
        for (var e : BuiltInRegistries.BLOCK.entrySet()) {
            if (!ours(e.getKey().location())) {
                continue;
            }
            Block block = e.getValue();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                states++;
                BakedModel model = mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
                if (model == missingModel) {
                    errors.add("Block state has no model: " + state);
                    continue;
                }
                checkSprites("block " + state, model, state);
            }
        }
        info.add(states + " block states checked");
    }

    private void items() {
        int n = 0;
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            if (!ours(e.getKey().location())) {
                continue;
            }
            n++;
            ItemStack stack = new ItemStack(e.getValue());
            BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);
            if (model == missingModel) {
                errors.add("Item has no model: " + e.getKey().location());
            } else {
                checkSprites("item " + e.getKey().location(), model, null);
            }
        }
        info.add(n + " items checked");
    }

    private void checkSprites(String what, BakedModel model, BlockState state) {
        Set<ResourceLocation> missing = new LinkedHashSet<>();
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, rand, ModelData.EMPTY, null));
        for (Direction d : Direction.values()) {
            quads.addAll(model.getQuads(state, d, rand, ModelData.EMPTY, null));
        }
        for (BakedQuad q : quads) {
            if (isMissing(q.getSprite())) {
                missing.add(q.getSprite().contents().name());
            }
        }
        if (isMissing(model.getParticleIcon(ModelData.EMPTY))) {
            missing.add(ResourceLocation.withDefaultNamespace("particle"));
        }
        if (!missing.isEmpty()) {
            errors.add("Missing texture in " + what + (missing.contains(MissingTextureAtlasSprite.getLocation()) ? "" : " " + missing));
        }
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    private boolean spriteMissing(ResourceLocation texture) {
        return isMissing(mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(texture));
    }

    private void plants() {
        if (mc.level == null) {
            return;
        }
        var registry = mc.level.registryAccess().registryOrThrow(CultivarRegistries.PLANT);
        int n = 0;
        for (var e : registry.entrySet()) {
            n++;
            ResourceLocation id = e.getKey().location();
            Plant plant = e.getValue();
            if (plant.render().isEmpty()) {
                errors.add("Plant " + id + " has no render: invisible on trellises and in fields");
                continue;
            }
            PlantRender r = plant.render().get();
            for (ResourceLocation t : r.textures()) {
                if (spriteMissing(t)) {
                    errors.add("Plant " + id + ": missing texture " + t);
                }
            }
            for (ResourceLocation t : r.top()) {
                if (spriteMissing(t)) {
                    errors.add("Plant " + id + ": missing top texture " + t);
                }
            }
            for (ResourceLocation m : com.google.common.collect.Iterables.concat(r.models(), r.trellisModels())) {
                BakedModel model = mc.getModelManager().getModel(ModelResourceLocation.standalone(m));
                if (model == missingModel) {
                    errors.add("Plant " + id + ": missing model " + m);
                } else {
                    checkSprites("plant model " + m, model, null);
                }
            }
        }
        info.add(n + " plants checked");
    }

    private void weeds() {
        if (mc.level == null) {
            return;
        }
        for (var e : mc.level.registryAccess().registryOrThrow(CultivarRegistries.WEED).entrySet()) {
            Weed w = e.getValue();
            if (w.textures().isEmpty()) {
                errors.add("Blight " + e.getKey().location() + " has no textures: invisible");
            }
            for (ResourceLocation t : w.textures()) {
                if (spriteMissing(t)) {
                    errors.add("Blight " + e.getKey().location() + ": missing texture " + t);
                }
            }
        }
    }

    private void overlays() {
        for (String kind : List.of("alkaline", "acidic", "rich", "lean", "wet", "dry")) {
            for (int d = 1; d <= 2; d++) {
                ResourceLocation t = Cultivar.id("block/amendment/" + kind + "_" + d);
                if (spriteMissing(t)) {
                    errors.add("Missing conditioner overlay " + t);
                }
            }
        }
    }

    // ---------------------------------------------------------------- text

    private void names() {
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            if (!ours(e.getKey().location())) {
                continue;
            }
            String key = e.getValue().getDescriptionId();
            if (!I18n.exists(key)) {
                errors.add("No name for item " + e.getKey().location() + " (" + key + ")");
            }
            if (!I18n.exists(key + ".tooltip.summary")) {
                warnings.add("No Create tooltip summary for " + e.getKey().location());
            }
        }
        for (var e : BuiltInRegistries.BLOCK.entrySet()) {
            if (ours(e.getKey().location()) && !I18n.exists(e.getValue().getDescriptionId())) {
                warnings.add("No name for block " + e.getKey().location() + " (shown by Jade)");
            }
        }
        CoreModule.STATS.getEntries().forEach(stat -> {
            if (!I18n.exists("stat." + Cultivar.MOD_ID + "." + stat.getId().getPath())) {
                errors.add("No name for stat " + stat.getId());
            }
        });
        if (mc.level == null) {
            return;
        }
        for (var e : mc.level.registryAccess().registryOrThrow(CultivarRegistries.PLANT).entrySet()) {
            ResourceLocation id = e.getKey().location();
            if (!I18n.exists("plant." + id.getNamespace() + "." + id.getPath())) {
                warnings.add("Plant " + id + " has no lang name (falls back to \"" + id.getPath().replace('_', ' ') + "\")");
            }
        }
        for (var e : mc.level.registryAccess().registryOrThrow(CultivarRegistries.WEED).entrySet()) {
            ResourceLocation id = e.getKey().location();
            if (!I18n.exists("weed." + id.getNamespace() + "." + id.getPath())) {
                warnings.add("Blight " + id + " has no lang name");
            }
        }
        for (var e : mc.level.registryAccess().registryOrThrow(CultivarRegistries.CROP_INFLUENCE).entrySet()) {
            ResourceLocation id = e.getKey().location();
            if (!I18n.exists("influence." + id.getNamespace() + "." + id.getPath())) {
                warnings.add("Crop influence " + id + " has no lang name");
            }
        }
    }

    // ---------------------------------------------------------------- Ponder, creative tab

    /** Compiles every Cultivar Ponder scene (builds its world from the schematic) and reports any that throw. */
    private void ponder() {
        var access = PonderIndex.getSceneAccess();
        int withScenes = 0, scenes = 0;
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation id = e.getKey().location();
            if (!ours(id) || !access.doScenesExistForId(id)) {
                continue;
            }
            withScenes++;
            try {
                scenes += access.compile(id).size();
            } catch (Throwable t) {
                errors.add("Ponder scenes for " + id + " fail to build: " + t);
            }
        }
        info.add(withScenes + " items have Ponder (" + scenes + " scene builds)");
    }

    private void creativeTab() {
        if (mc.level == null || mc.player == null) {
            return;
        }
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
        Set<Item> inTab = new LinkedHashSet<>();
        CoreModule.TAB.get().getDisplayItems().forEach(s -> inTab.add(s.getItem()));
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            if (ours(e.getKey().location()) && !inTab.contains(e.getValue())) {
                warnings.add("Not in the Cultivar creative tab: " + e.getKey().location());
            }
        }
    }
}
