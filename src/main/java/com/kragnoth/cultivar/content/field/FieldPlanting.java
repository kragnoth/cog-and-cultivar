package com.kragnoth.cultivar.content.field;

import com.kragnoth.cultivar.content.crop.CropInfo;
import com.kragnoth.cultivar.content.crop.plant.PlantStatus;

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
import java.util.Optional;

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
                CropInfo.warnAfterPlanting(crop.plant(), player);
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
        Optional<Component> problem = soilProblem(level, soilPos.above(), genome);
        if (problem.isEmpty()) {
            return Result.PLANTED;
        }
        if (player != null && !level.isClientSide()) {
            player.displayClientMessage(Component.translatable("cultivar.field.wrong_soil", CultivarSeedItem.plantName(genome),
                    problem.get()).withStyle(ChatFormatting.YELLOW), true);
        }
        return Result.REFUSED;
    }

    /**
     * A Cultivar seed used on bare ground that isn't a Cultivar soil (grass, dirt, sand...): say what it needs instead
     * of doing nothing. Only for plain ground with free space above, so clicking chests or machines stays silent.
     */
    public static void hintNotSoil(Level level, BlockPos pos, ItemStack stack, @Nullable Player player) {
        if (player == null || level.isClientSide() || !(stack.getItem() instanceof CultivarSeedItem)) {
            return;
        }
        var state = level.getBlockState(pos);
        boolean ground = state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.SAND)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL);
        if (ground && !(state.getBlock() instanceof PlantHolder) && !(state.getBlock() instanceof SoilHost)
                && SoilLookup.at(level, pos).isEmpty() && level.getBlockState(pos.above()).canBeReplaced()) {
            player.displayClientMessage(Component.translatable("cultivar.field.not_soil").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /** The plant's soil requirements as they would apply at {@code cropPos} (no block placed yet). */
    public static boolean soilAccepts(Level level, BlockPos cropPos, Genome genome) {
        return soilProblem(level, cropPos, genome).isEmpty();
    }

    /**
     * Why {@code genome} wouldn't take root at {@code cropPos} ("✘ Humidity: wet or wetter (here: dry)"), or empty if
     * its soil requirements are met. Light and other conditions don't stop planting, so they aren't reported here.
     */
    public static Optional<Component> soilProblem(Level level, BlockPos cropPos, Genome genome) {
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
            return Optional.of(Component.translatable("cultivar.info.no_soil"));
        }
        var ctx = probe.context(soil);
        for (GrowthCondition c : probe.requirements()) {
            if (c.isSoilProperty()) {
                GrowthResponse r = c.test(ctx, probe.strength());
                if (r != GrowthResponse.FERTILE) {
                    return CropInfo.requirementLine(new PlantStatus.Check(c, r, c.current(ctx)))
                            .map(line -> (Component) Component.literal(line.getString().strip()))
                            .or(() -> Optional.of(Component.literal("?")));
                }
            }
        }
        return Optional.empty();
    }
}
