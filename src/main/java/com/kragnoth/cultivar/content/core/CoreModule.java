package com.kragnoth.cultivar.content.core;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.content.core.condition.CoreConditions;
import com.kragnoth.cultivar.content.core.influence.CoreInfluence;
import com.kragnoth.cultivar.core.module.CultivarModule;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Things every other module depends on: the six core stats, the genome component, the creative tab. */
public final class CoreModule implements CultivarModule {
    public static final DeferredRegister<Stat> STATS = DeferredRegister.create(CultivarRegistries.STAT_KEY, Cultivar.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Cultivar.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Cultivar.MOD_ID);

    // Core stats (spec §2.2). Colours are ARGB.
    public static final DeferredHolder<Stat, Stat> GAIN = STATS.register("gain", () -> new Stat(1, 10, 0xFF3F6FD8));
    public static final DeferredHolder<Stat, Stat> GROWTH = STATS.register("growth", () -> new Stat(1, 10, 0xFF4FAE4A));
    public static final DeferredHolder<Stat, Stat> STRENGTH = STATS.register("strength", () -> new Stat(1, 10, 0xFFC0473B));
    public static final DeferredHolder<Stat, Stat> RESISTANCE = STATS.register("resistance", () -> new Stat(1, 10, 0xFFD6B055));
    public static final DeferredHolder<Stat, Stat> FERTILITY = STATS.register("fertility", () -> new Stat(1, 10, 0xFFDD8B63));
    public static final DeferredHolder<Stat, Stat> MUTATIVITY = STATS.register("mutativity", () -> new Stat(1, 10, 0xFF5FB3B0));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Genome>> GENOME =
            COMPONENTS.registerComponentType("genome", b -> b.persistent(Genome.CODEC).networkSynchronized(Genome.STREAM_CODEC));

    /** Present on seed items whose stats have been revealed by an Assaying Engine. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> ASSAYED =
            COMPONENTS.registerComponentType("assayed", b -> b.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cultivar"))
            .icon(() -> new ItemStack(Items.WHEAT_SEEDS))
            .build());

    @Override
    public String id() {
        return "core";
    }

    @Override
    public void onConstruct(IEventBus modBus, ModContainer container) {
        STATS.register(modBus);
        CoreConditions.TYPES.register(modBus);
        CoreInfluence.SCALING_TYPES.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
    }
}
