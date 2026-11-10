package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.server.level.ServerPlayer;

/** Botanical Ledger (spec §3.12) and the per-player knowledge it feeds. */
public final class LedgerModule implements CultivarModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cultivar.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Cultivar.MOD_ID);
    public static final DeferredRegister<net.minecraft.advancements.CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Cultivar.MOD_ID);
    public static final DeferredHolder<net.minecraft.advancements.CriterionTrigger<?>, LearnedPlantTrigger> LEARNED_PLANT =
            TRIGGERS.register("learned_plant", LearnedPlantTrigger::new);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Cultivar.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LedgerItem.Data>> LEDGER_DATA =
            COMPONENTS.registerComponentType("ledger", b -> b.persistent(LedgerItem.Data.CODEC)
                    .networkSynchronized(LedgerItem.Data.STREAM_CODEC));
    public static final DeferredItem<LedgerItem> LEDGER = ITEMS.registerItem("botanical_ledger",
            LedgerItem::new, new Item.Properties().stacksTo(1));
    /** The player's knowledge; kept through death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LedgerItem.Data>> KNOWLEDGE =
            ATTACHMENTS.register("knowledge", () -> AttachmentType.builder(() -> LedgerItem.Data.EMPTY)
                    .serialize(LedgerItem.Data.CODEC).copyOnDeath().build());

    @Override
    public String id() {
        return "ledger";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        TRIGGERS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CoreModule.TAB.getKey()) {
                event.accept(LEDGER.get());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            sync(e);
            if (e.getEntity() instanceof ServerPlayer player) {
                Knowledge.award(player, Knowledge.get(player).researched()); // old saves catch up on advancements
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> sync(e));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sync(e));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> e.getDispatcher().register(KnowledgeCommand.build()));
    }

    @Override
    public void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(KnowledgePayload.TYPE, KnowledgePayload.STREAM_CODEC,
                (payload, context) -> ClientKnowledge.set(payload.data()));
    }

    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Knowledge.sync(player);
        }
    }
}
