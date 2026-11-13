package com.kragnoth.cultivar.content.field;

import com.kragnoth.cultivar.api.condition.GrowthCondition;
import com.kragnoth.cultivar.api.condition.GrowthResponse;
import com.kragnoth.cultivar.api.crop.PlantHolder;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.plant.Plant;
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
 * the plant just won't grow until they're right (goggles say why). Trellis machines (Planter and Trial Beds) and
 * plants taller than one block (cane, bamboo, cactus...) still need a trellis.
 */
public final class FieldPlanting {
    public enum Result { PASS, PLANTED, REFUSED }

    private FieldPlanting() {}

    /** A Cultivar soil a field crop can stand on (not a trellis machine). */
    public static boolean isFieldSoil(Level level, BlockPos soilPos) {
        return !(level.getBlockState(soilPos).getBlock() instanceof SoilHost) && SoilLookup.at(level, soilPos).isPresent();
    }

    public static Result tryPlant(Level level, BlockPos soilPos, ItemStack stack, @Nullable Player player) {
        Genome genome = stack.getItem() instanceof CultivarSeedItem ? stack.get(CoreModule.GENOME.get()) : null;
        BlockPos target = soilPos.above();
        if (genome == null || level.getBlockState(soilPos).getBlock() instanceof PlantHolder
                || !isFieldSoil(level, soilPos) || !level.getBlockState(target).canBeReplaced()) {
            return Result.PASS;
        }
        Plant plant = genome.species().value();
        if (plant.height() > 1) {
            return refuse(level, player, "cultivar.field.needs_trellis", genome);
        }
        boolean assayed = stack.has(CoreModule.ASSAYED.get());
        if (!soilAccepts(level, target, genome)) {
            return refuse(level, player, "cultivar.field.wrong_soil", genome);
        }
        if (!level.isClientSide()) {
            level.setBlock(target, FieldCropModule.FIELD_CROP.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(target) instanceof FieldCropBlockEntity crop) {
                crop.plant().set(genome, 0, assayed);
            }
            stack.consume(1, player);
            level.playSound(null, target, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        }
        return Result.PLANTED;
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
