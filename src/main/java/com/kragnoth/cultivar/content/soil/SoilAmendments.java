package com.kragnoth.cultivar.content.soil;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.soil.SoilConditioner;
import com.kragnoth.cultivar.api.soil.SoilLevels;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soil amendments: the steps soil conditioners have shifted a soil block's properties by.
 *
 * <p>Stored per block position in a chunk attachment (saved with the chunk), so an amendment belongs to the soil, not
 * the trellis on it: breaking and replanting keeps it, breaking the soil block clears it. Clients get a copy per
 * watched chunk ({@link AmendmentsPayload}) for the indicator overlay, Jade, goggles and particles; lookups on a
 * client-side level read that copy, so server growth checks and client readouts agree.
 */
public final class SoilAmendments {
    record Entry(BlockPos pos, Amendment steps) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Amendment.CODEC.fieldOf("steps").forGetter(Entry::steps)
        ).apply(i, Entry::new));
    }

    /** Chunk attachment codec: a list of (pos, steps) entries. */
    public static final Codec<Map<BlockPos, Amendment>> MAP_CODEC = Entry.CODEC.listOf().xmap(
            list -> {
                Map<BlockPos, Amendment> map = new HashMap<>();
                list.forEach(e -> map.put(e.pos(), e.steps()));
                return Map.copyOf(map);
            },
            map -> map.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());

    /** Client copy, keyed by dimension and chunk. Inner maps are immutable, so render threads can read safely. */
    private record ChunkKey(ResourceKey<Level> dimension, long chunk) {}
    private static final Map<ChunkKey, Map<BlockPos, Amendment>> CLIENT = new ConcurrentHashMap<>();

    public enum Result { PASS, APPLIED, SATURATED }

    private SoilAmendments() {}

    // ---------------------------------------------------------------- lookups

    /** The amendment at a soil position; {@link Amendment#NONE} if none or the level isn't a real level. */
    public static Amendment get(@Nullable BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) {
            return Amendment.NONE;
        }
        if (level.isClientSide()) {
            return client(level.dimension(), pos);
        }
        if (!level.hasChunkAt(pos)) {
            return Amendment.NONE;
        }
        Map<BlockPos, Amendment> map = level.getChunkAt(pos).getExistingDataOrNull(SoilModule.AMENDMENTS);
        return map == null ? Amendment.NONE : map.getOrDefault(pos, Amendment.NONE);
    }

    /** Client copy lookup; safe from chunk-meshing threads. */
    public static Amendment client(ResourceKey<Level> dimension, BlockPos pos) {
        Map<BlockPos, Amendment> map = CLIENT.get(new ChunkKey(dimension, ChunkPos.asLong(pos)));
        return map == null ? Amendment.NONE : map.getOrDefault(pos, Amendment.NONE);
    }

    /** Every amended position the client knows of in one chunk (for ambient particles). */
    public static Map<BlockPos, Amendment> clientChunk(ResourceKey<Level> dimension, long chunk) {
        return CLIENT.getOrDefault(new ChunkKey(dimension, chunk), Map.of());
    }

    public static SoilLevels.Acidity acidity(@Nullable BlockGetter level, BlockPos soilPos, SoilLevels.Acidity base) {
        return shift(SoilLevels.Acidity.values(), base.ordinal(), get(level, soilPos).acidity());
    }

    public static SoilLevels.Nutrients nutrients(@Nullable BlockGetter level, BlockPos soilPos, SoilLevels.Nutrients base) {
        return shift(SoilLevels.Nutrients.values(), base.ordinal(), get(level, soilPos).nutrients());
    }

    private static <T> T shift(T[] all, int base, int steps) {
        return all[Math.clamp(base + steps, 0, all.length - 1)];
    }

    public static Optional<SoilConditioner> conditionerFor(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return level.registryAccess().registryOrThrow(CultivarRegistries.SOIL_CONDITIONER).stream()
                .filter(c -> c.items().contains(stack.getItemHolder())).findFirst();
    }

    public static int maxSteps() {
        return CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.CONDITIONER_MAX_STEPS.get() : 2;
    }

    // ---------------------------------------------------------------- applying

    /**
     * Uses {@code stack} as a conditioner on the soil at {@code soilPos}. PASS if the item isn't a conditioner or the
     * block isn't a Cultivar soil; SATURATED if every property it shifts is already at the cap. Runs the same check on
     * the client (it has the amendment copy and the synced server config) so the hand swings only when it works.
     */
    public static Result tryApply(Level level, BlockPos soilPos, ItemStack stack, @Nullable Player player) {
        Optional<SoilConditioner> found = conditionerFor(level, stack);
        if (found.isEmpty() || com.kragnoth.cultivar.content.core.lookup.SoilLookup.at(level, soilPos).isEmpty()) {
            return Result.PASS;
        }
        SoilConditioner c = found.get();
        Amendment current = get(level, soilPos);
        Amendment next = current.plus(c.acidity(), c.nutrients(), c.humidity(), maxSteps());
        if (next.equals(current)) {
            if (player != null && !level.isClientSide()) {
                player.displayClientMessage(Component.translatable("cultivar.amendment.saturated").withStyle(ChatFormatting.YELLOW), true);
            }
            return Result.SATURATED;
        }
        if (level instanceof ServerLevel server) {
            ItemStack dust = stack.copyWithCount(1);
            set(server, soilPos, next);
            stack.consume(1, player);
            server.playSound(null, soilPos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1f, 0.9f);
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, dust),
                    soilPos.getX() + 0.5, soilPos.getY() + 1.05, soilPos.getZ() + 0.5, 14, 0.3, 0.05, 0.3, 0.05);
            if (player != null) {
                player.displayClientMessage(describe(next), true);
            }
        }
        return Result.APPLIED;
    }

    public static void set(ServerLevel level, BlockPos pos, Amendment amendment) {
        LevelChunk chunk = level.getChunkAt(pos);
        Map<BlockPos, Amendment> existing = chunk.getExistingDataOrNull(SoilModule.AMENDMENTS);
        Map<BlockPos, Amendment> map = new HashMap<>(existing == null ? Map.of() : existing);
        if (amendment.isEmpty()) {
            if (map.remove(pos) == null) {
                return;
            }
        } else {
            map.put(pos.immutable(), amendment);
        }
        if (map.isEmpty()) {
            chunk.removeData(SoilModule.AMENDMENTS);
        } else {
            chunk.setData(SoilModule.AMENDMENTS, Map.copyOf(map));
        }
        chunk.setUnsaved(true);
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk.getPos(), new AmendmentsPayload(chunk.getPos(), Map.copyOf(map)));
    }

    public static void clear(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server && !get(server, pos).isEmpty()) {
            set(server, pos, Amendment.NONE);
        }
    }

    // ---------------------------------------------------------------- client copy

    /** Replaces the client copy of one chunk and re-renders every soil whose amendment changed (and the block above). */
    static void receive(Level level, AmendmentsPayload payload) {
        ChunkKey key = new ChunkKey(level.dimension(), payload.chunk().toLong());
        Map<BlockPos, Amendment> before = payload.amendments().isEmpty() ? CLIENT.remove(key)
                : CLIENT.put(key, Map.copyOf(payload.amendments()));
        List<BlockPos> changed = new ArrayList<>(payload.amendments().keySet());
        if (before != null) {
            changed.addAll(before.keySet());
        }
        for (BlockPos pos : changed) {
            for (BlockPos p : new BlockPos[]{pos, pos.above()}) {
                var state = level.getBlockState(p);
                level.sendBlockUpdated(p, state, state, 8);
            }
        }
    }

    static void forgetClientChunk(Level level, ChunkPos chunk) {
        CLIENT.remove(new ChunkKey(level.dimension(), chunk.toLong()));
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    // ---------------------------------------------------------------- text

    /** "Amended: 2 alkaline, 1 richer". */
    public static MutableComponent describe(Amendment a) {
        return Component.translatable("cultivar.amendment", steps(a)).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** "2 alkaline, 1 richer" (empty amendment → empty component). */
    public static MutableComponent steps(Amendment a) {
        List<Component> parts = new ArrayList<>();
        part(parts, a.acidity(), "alkaline", "acidic");
        part(parts, a.nutrients(), "rich", "lean");
        part(parts, a.humidity(), "wet", "dry");
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(parts.get(i));
        }
        return joined;
    }

    private static void part(List<Component> out, int steps, String up, String down) {
        if (steps != 0) {
            out.add(Component.translatable("cultivar.amendment." + (steps > 0 ? up : down), Math.abs(steps)));
        }
    }
}
