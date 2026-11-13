package com.kragnoth.cultivar.content.soil;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.crop.PlantHolder;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Soil conditioners: Agricultural Lime, Sulfur Dust and Compost (plus any item a datapack lists in the
 * {@code soil_conditioner} registry) shift a soil block's acidity, nutrients or humidity a step per use, capped by
 * config. Used on the soil itself or on the trellis standing on it; deployers work too (they fire the same event).
 */
public final class SoilModule implements CultivarModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Cultivar.MOD_ID);

    public static final DeferredItem<Item> AGRICULTURAL_LIME = ITEMS.registerSimpleItem("agricultural_lime");
    public static final DeferredItem<Item> SULFUR_DUST = ITEMS.registerSimpleItem("sulfur_dust");
    public static final DeferredItem<Item> COMPOST = ITEMS.registerSimpleItem("compost");

    /** Per-chunk map of soil position → amendment. Only chunks that have one carry the attachment. */
    public static final Supplier<AttachmentType<Map<BlockPos, Amendment>>> AMENDMENTS = ATTACHMENTS.register("soil_amendments",
            () -> AttachmentType.<Map<BlockPos, Amendment>>builder(() -> Map.of())
                    .serialize(SoilAmendments.MAP_CODEC, map -> !map.isEmpty()).build());

    @Override
    public String id() {
        return "soil";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        // Humidity steps from conditioners apply even when environmental humidity shifts are switched off.
        com.kragnoth.cultivar.api.environment.CultivarEnvironment.addHumidityModifier(
                com.kragnoth.cultivar.api.environment.HumidityModifier.amendment((level, soilPos) -> SoilAmendments.get(level, soilPos).humidity()));
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreModule.TAB.getKey()) {
                ITEMS.getEntries().forEach(item -> e.accept(item.get()));
            }
        });
        NeoForge.EVENT_BUS.addListener(SoilModule::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> {
            if (e.getLevel() instanceof Level level) {
                SoilAmendments.clear(level, e.getPos());
            }
        });
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) ->
                e.getAffectedBlocks().forEach(pos -> SoilAmendments.clear(e.getLevel(), pos)));
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Sent e) -> {
            Map<BlockPos, Amendment> map = e.getChunk().getExistingDataOrNull(AMENDMENTS);
            if (map != null && !map.isEmpty()) {
                PacketDistributor.sendToPlayer(e.getPlayer(), new AmendmentsPayload(e.getPos(), map));
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload e) -> {
            if (e.getLevel() instanceof Level level && level.isClientSide()) {
                SoilAmendments.forgetClientChunk(level, e.getChunk().getPos());
            }
        });
    }

    @Override
    public void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(AmendmentsPayload.TYPE, AmendmentsPayload.STREAM_CODEC,
                (payload, context) -> SoilAmendments.receive(context.player().level(), payload));
    }

    /** Conditioner on a soil block, or on any plant holder (trellis segment, field crop): amend the soil under the plant. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockPos soilPos = pos;
        if (level.getBlockState(pos).getBlock() instanceof PlantHolder holder) {
            soilPos = holder.soilPos(level, pos);
        }
        SoilAmendments.Result result = SoilAmendments.tryApply(level, soilPos, event.getItemStack(), event.getEntity());
        if (result != SoilAmendments.Result.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result == SoilAmendments.Result.APPLIED
                    ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.CONSUME);
        }
    }
}
