package com.kragnoth.cultivar.client.crop;

import com.kragnoth.cultivar.api.plant.Plant;
import com.kragnoth.cultivar.content.core.CoreModule;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Seed item tint per species: the plant's {@code seed_color}, else the average colour of its first product's sprite
 * (weighted toward saturated pixels, so wheat reads gold and lapis reads blue), else a colour hashed from the id.
 * Follows the resource pack; cached until textures reload or the player joins a world.
 */
public final class SeedColors {
    private static final Map<ResourceLocation, Integer> CACHE = new ConcurrentHashMap<>();

    private SeedColors() {}

    public static void clear() {
        CACHE.clear();
    }

    public static int of(ItemStack stack) {
        var genome = stack.get(CoreModule.GENOME.get());
        if (genome == null) {
            return -1;
        }
        Holder<Plant> species = genome.species();
        ResourceLocation id = species.unwrapKey().map(k -> k.location()).orElse(null);
        if (id == null) {
            return -1;
        }
        return CACHE.computeIfAbsent(id, k -> 0xFF000000 | compute(k, species.value()));
    }

    private static int compute(ResourceLocation id, Plant plant) {
        if (plant.seedColor().isPresent()) {
            return plant.seedColor().get();
        }
        if (!plant.products().isEmpty()) {
            int sampled = sample(new ItemStack(plant.products().getFirst().item()));
            if (sampled >= 0) {
                return sampled;
            }
        }
        float hue = (id.hashCode() & 0xFFFF) / 65535f;
        return net.minecraft.util.Mth.hsvToRgb(hue, 0.45f, 0.8f) & 0xFFFFFF;
    }

    /** Saturation-weighted average of the item's sprite, lifted so dark products still tint visibly. */
    @SuppressWarnings("deprecation")
    private static int sample(ItemStack stack) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0).getParticleIcon();
        var contents = sprite.contents();
        double r = 0, g = 0, b = 0, w = 0;
        for (int y = 0; y < contents.height(); y++) {
            for (int x = 0; x < contents.width(); x++) {
                int abgr = sprite.getPixelRGBA(0, x, y);
                if ((abgr >>> 24) < 128) {
                    continue;
                }
                int pr = abgr & 0xFF, pg = (abgr >> 8) & 0xFF, pb = (abgr >> 16) & 0xFF;
                int max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
                double weight = 0.15 + (max == 0 ? 0 : (max - min) / (double) max);
                r += pr * weight;
                g += pg * weight;
                b += pb * weight;
                w += weight;
            }
        }
        if (w == 0) {
            return -1;
        }
        r /= w;
        g /= w;
        b /= w;
        double peak = Math.max(r, Math.max(g, b));
        if (peak < 110) { // lift dark products (coal, ink) so the seed isn't a black blot
            double k = 110 / Math.max(1, peak);
            r *= k;
            g *= k;
            b *= k;
        }
        return ((int) Math.min(255, r) << 16) | ((int) Math.min(255, g) << 8) | (int) Math.min(255, b);
    }
}
