package com.kragnoth.cultivar.content.field;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.crop.PlantHolder;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.soil.SoilHost;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.lookup.SoilLookup;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.plant.PlantCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Planting a Cultivar seed straight on soil. Like a vanilla seed, it only takes on soil it can grow in: the plant's
 * soil requirements (humidity, acidity, nutrients, after water, cloches and conditioners) must be met, with Strength
 * widening the tolerance and a Hardy plant taking any Cultivar soil. Light and other conditions aren't checked here;
 * the plant just won't grow until they're right (goggles say why). Tall plants (cane, bamboo, cactus...) stack
 * field-crop segments as they grow, like vanilla cane. Trellis machines (Planter and Trial Beds) keep trellises.
 */
public final class FieldPlanting {
    public enum Result { PASS, PLANTED, REFUSED }

    private FieldPlanting() {}

    /** A Cultivar soil a field crop can stand on (not a trellis machine). */
    public static boolean isFieldSoil(Level level, BlockPos soilPos) {
        return !(level.getBlockState(soilPos).getBlock() instanceof SoilHost) && SoilLookup.at(level, soilPos).isPresent();
    }

    /** A Cultivar seed used on the top of {@code soilPos}: plants it as a field crop if the soil suits it. */
    public static Result tryPlant(Level level, BlockPos soilPos, ItemStack stack, @Nullable Player player) {
        Genome genome = stack.getItem() instanceof CultivarSeedItem ? stack.get(CoreModule.GENOME.get()) : null;
        if (genome == null) {
            return Result.PASS;
        }
        Result result = plant(level, soilPos, genome, 0, stack.has(CoreModule.ASSAYED.get()), player);
        if (result == Result.PLANTED && !level.isClientSide()) {
            stack.consume(1, player);
        }
        return result;
    }

    /**
     * Plants {@code genome} at {@code stage} on top of {@code soilPos} (seeds, satchels, spades, cuttings all come
     * through here). PASS if this isn't a free spot on field soil, REFUSED (with a message) if the soil doesn't suit
     * the plant. Consumes nothing: callers spend their own item on PLANTED.
     */
    public static Result plant(Level level, BlockPos soilPos, Genome genome, int stage, boolean assayed, @Nullable Player player) {
        Result check = check(level, soilPos, genome, player);
        if (check != Result.PLANTED) {
            return check;
        }
        BlockPos target = soilPos.above();
        if (!level.isClientSide()) {
            level.setBlock(target, FieldCropModule.FIELD_CROP.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(target) instanceof FieldCropBlockEntity crop) {
                crop.plant().set(genome, stage, assayed);
                crop.plant().clampToSpace();
            }
            level.playSound(null, target, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        }
        return Result.PLANTED;
    }

    /**
     * Whether {@code genome} could be planted on top of {@code soilPos}: PLANTED if yes (nothing placed), PASS if this
     * isn't a free spot on field soil, REFUSED (with a message to {@code player}) if the soil doesn't suit it.
     */
    public static Result check(Level level, BlockPos soilPos, Genome genome, @Nullable Player player) {
        if (level.getBlockState(soilPos).getBlock() instanceof PlantHolder
                || !isFieldSoil(level, soilPos) || !level.getBlockState(soilPos.above()).canBeReplaced()) {
            return Result.PASS;
        }
        return soilAccepts(level, soilPos.above(), genome) ? Result.PLANTED : refuse(level, player, "cultivar.field.wrong_soil", genome);
    }

    /** The plant's soil requirements as they would apply at {@code cropPos} (no block placed yet). */
    public static boolean soilAccepts(Level level, BlockPos cropPos, Genome genome) {
        PlantCore probe = new PlantCore(new PlantCore.Host() {
            @Override
            public Level level() {
                return level;
            }

            @Override
            public BlockPos rootPos() {
                return cropPos;
            }

            @Override
            public void changed() {}
        });
        probe.set(genome, 0, false);
        var soil = probe.soil();
        if (soil.isEmpty()) {
            return false;
        }
        List<GrowthCondition> needs = probe.requirements().stream().filter(GrowthCondition::isSoilProperty).toList();
        return GrowthCondition.testAll(needs, probe.context(soil), probe.strength()) == GrowthResponse.FERTILE;
    }

    private static Result refuse(Level level, @Nullable Player player, String key, Genome genome) {
        if (player != null && !level.isClientSide()) {
            player.displayClientMessage(Component.translatable(key, CultivarSeedItem.plantName(genome)).withStyle(ChatFormatting.YELLOW), true);
        }
        return Result.REFUSED;
    }
}
