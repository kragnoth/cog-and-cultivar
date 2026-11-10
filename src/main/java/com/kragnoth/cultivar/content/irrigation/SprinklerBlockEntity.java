package com.kragnoth.cultivar.content.irrigation;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Holds 1000 mB of water. While it holds any water it runs, spending 1 mB per {@code ticksPerMillibucket} ticks
 * of running; with an empty tank it does nothing. Every {@code interval} ticks while running it sprays:
 * picks {@code sprinklerAttempts} random positions in the spray area and, for each,
 * <ul>
 *   <li>random-ticks it if it's a randomly ticking growable (crops, bushes, saplings, cane, cactus, vines,
 *       Cultivar trellises, anything in {@code #cultivar:sprinkler_growables});</li>
 *   <li>with {@code sprinklerBonemealChance}, bone-meals it if it's bone-mealable (moss spreads, so moss farms
 *       work), firing NeoForge's BonemealEvent so other mods can react or cancel;</li>
 *   <li>moistens farmland.</li>
 * </ul>
 * Blocks in {@code #cultivar:sprinkler_ignored} (grass, nylium: bone-mealing them carpets the area) are skipped.
 * Area: ±radius horizontally; 5 blocks below when hanging (nozzle down), else 2 below to 1 above.
 */
public class SprinklerBlockEntity extends BlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int CAPACITY = 1000;
    public static final TagKey<Block> GROWABLES = TagKey.create(Registries.BLOCK, Cultivar.id("sprinkler_growables"));
    public static final TagKey<Block> IGNORED = TagKey.create(Registries.BLOCK, Cultivar.id("sprinkler_ignored"));

    /** Water only; filled by pipes through the fluid capability, never drained by them. */
    public final FluidTank tank = new FluidTank(CAPACITY, fs -> fs.getFluid().is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            needsSync = true;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    private int cooldown;
    private int ticksSinceDrain;
    private boolean needsSync;

    public SprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(IrrigationModule.SPRINKLER_BE.get(), pos, state);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (needsSync && server.getGameTime() % 20 == (worldPosition.asLong() & 15)) {
            needsSync = false;
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        // A redstone signal switches it off (no spraying, no water use).
        boolean active = !tank.isEmpty() && !server.hasNeighborSignal(worldPosition);
        BlockState state = getBlockState();
        if (state.getValue(SprinklerBlock.ACTIVE) != active) {
            server.setBlock(worldPosition, state.setValue(SprinklerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (!active) {
            return;
        }
        if (++ticksSinceDrain >= CultivarServerConfig.SPRINKLER_TICKS_PER_MB.get()) {
            ticksSinceDrain = 0;
            tank.setFluid(tank.getFluid().copyWithAmount(tank.getFluidAmount() - 1));
        }
        if (--cooldown <= 0) {
            cooldown = CultivarServerConfig.SPRINKLER_INTERVAL.get();
            pulse(server, server.random, CultivarServerConfig.SPRINKLER_ATTEMPTS.get(), CultivarServerConfig.SPRINKLER_BONEMEAL_CHANCE.get());
        }
    }

    /** One spray: public so game tests can drive it without waiting on the cooldown. */
    public void pulse(ServerLevel level, RandomSource random, int attempts, double bonemealChance) {
        int r = CultivarServerConfig.SPRINKLER_RADIUS.get();
        boolean hanging = getBlockState().getValue(SprinklerBlock.FACING) == Direction.DOWN;
        int yMin = hanging ? -5 : -2, yMax = hanging ? -1 : 1;
        for (int i = 0; i < attempts; i++) {
            BlockPos pos = worldPosition.offset(random.nextInt(2 * r + 1) - r, yMin + random.nextInt(yMax - yMin + 1), random.nextInt(2 * r + 1) - r);
            if (!level.isLoaded(pos)) {
                continue;
            }
            water(level, pos, random, bonemealChance);
        }
    }

    private static void water(ServerLevel level, BlockPos pos, RandomSource random, double bonemealChance) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(IGNORED)) {
            return;
        }
        if (state.getBlock() instanceof FarmBlock) {
            if (state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
            }
            return;
        }
        if (state.isRandomlyTicking() && isGrowable(state)) {
            state.randomTick(level, pos, random);
            state = level.getBlockState(pos);
        }
        if (bonemealChance > 0 && random.nextDouble() < bonemealChance && state.getBlock() instanceof BonemealableBlock b) {
            var event = EventHooks.fireBonemealEvent(null, level, pos, state, new ItemStack(Items.BONE_MEAL));
            if (event.isCanceled() || event.isSuccessful()) {
                return; // another mod handled (or vetoed) it
            }
            if (b.isValidBonemealTarget(level, pos, state) && b.isBonemealSuccess(level, random, pos, state)) {
                b.performBonemeal(level, random, pos, state);
                level.levelEvent(1505, pos, 0); // happy-villager sparkles, as bone meal does
            }
        }
    }

    static boolean isGrowable(BlockState state) {
        Block b = state.getBlock();
        return b instanceof BonemealableBlock || b instanceof BushBlock || b instanceof SugarCaneBlock || b instanceof CactusBlock
                || b instanceof ChorusFlowerBlock || b instanceof GrowingPlantBlock || b instanceof VineBlock
                || b instanceof TrellisBlock || state.is(GROWABLES);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override
    public @org.jetbrains.annotations.Nullable net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        var gray = net.minecraft.ChatFormatting.GRAY;
        tooltip.add(net.minecraft.network.chat.Component.translatable("block.cultivar.sprinkler").withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(net.minecraft.network.chat.Component.translatable("cultivar.sprinkler.water", tank.getFluidAmount(), CAPACITY).withStyle(gray));
        boolean powered = level != null && level.hasNeighborSignal(worldPosition);
        tooltip.add(net.minecraft.network.chat.Component.translatable(powered ? "cultivar.sprinkler.off"
                : getBlockState().getValue(SprinklerBlock.ACTIVE) ? "cultivar.sprinkler.running" : "cultivar.sprinkler.dry").withStyle(gray));
        int r = CultivarServerConfig.SPRINKLER_RADIUS.get();
        tooltip.add(net.minecraft.network.chat.Component.translatable("cultivar.sprinkler.area", 2 * r + 1, 2 * r + 1).withStyle(gray));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
