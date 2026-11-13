package com.kragnoth.cultivar.client.soil;

import com.kragnoth.cultivar.client.ClientModule;
import com.kragnoth.cultivar.content.soil.Amendment;
import com.kragnoth.cultivar.content.soil.SoilAmendments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Client side of soil conditioners: drops the amendment copy on logout, and puffs a faint coloured dust off amended
 * soil nearby (white lime, yellow sulfur, dark compost...) so bare soil shows it too, not just soil under a trellis.
 */
public final class SoilClientModule implements ClientModule {
    private static final int RADIUS_CHUNKS = 2;

    @Override
    public String id() {
        return "soil";
    }

    @Override
    public void onConstructClient(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> SoilAmendments.clearClient());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ambient());
    }

    private static void ambient() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || level.getGameTime() % 10 != 0) {
            return;
        }
        RandomSource rand = level.random;
        ChunkPos centre = mc.player.chunkPosition();
        for (int dx = -RADIUS_CHUNKS; dx <= RADIUS_CHUNKS; dx++) {
            for (int dz = -RADIUS_CHUNKS; dz <= RADIUS_CHUNKS; dz++) {
                Map<BlockPos, Amendment> chunk = SoilAmendments.clientChunk(level.dimension(), ChunkPos.asLong(centre.x + dx, centre.z + dz));
                chunk.forEach((pos, a) -> {
                    if (rand.nextInt(4) == 0) {
                        level.addParticle(new DustParticleOptions(colour(a, rand), 0.7f),
                                pos.getX() + 0.15 + rand.nextDouble() * 0.7, pos.getY() + 1.05, pos.getZ() + 0.15 + rand.nextDouble() * 0.7,
                                0, 0.02, 0);
                    }
                });
            }
        }
    }

    /** One of the amended properties, picked at random when several are set. */
    private static Vector3f colour(Amendment a, RandomSource rand) {
        int[] steps = {a.acidity(), a.nutrients(), a.humidity()};
        int pick;
        do {
            pick = rand.nextInt(3);
        } while (steps[pick] == 0);
        boolean up = steps[pick] > 0;
        return switch (pick) {
            case 0 -> up ? new Vector3f(0.95f, 0.95f, 0.88f) : new Vector3f(0.93f, 0.84f, 0.2f);
            case 1 -> up ? new Vector3f(0.33f, 0.2f, 0.1f) : new Vector3f(0.72f, 0.66f, 0.5f);
            default -> up ? new Vector3f(0.35f, 0.55f, 0.95f) : new Vector3f(0.86f, 0.68f, 0.42f);
        };
    }
}
