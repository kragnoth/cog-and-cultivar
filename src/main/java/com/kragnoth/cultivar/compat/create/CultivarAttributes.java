package com.kragnoth.cultivar.compat.create;

import com.kragnoth.cultivar.Cultivar;
import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Create filter attributes for seeds and cuttings (spec §3.13): "species is X", "Stat ≥ N" (assayed items only,
 * so filters can't read unassayed stats) and "is assayed". Attribute filters on funnels/brass tunnels/the stock
 * system then sort harvests by genetics. Lang: {@code create.item_attributes.<key>[.inverted]}.
 */
public final class CultivarAttributes {
    public static final DeferredRegister<ItemAttributeType> TYPES =
            DeferredRegister.create(CreateRegistries.ITEM_ATTRIBUTE_TYPE, Cultivar.MOD_ID);

    public static final DeferredHolder<ItemAttributeType, ItemAttributeType> SPECIES = TYPES.register("species", () -> new ItemAttributeType() {
        @Override
        public ItemAttribute createAttribute() {
            return new Species(ResourceLocation.withDefaultNamespace("wheat"));
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            Genome g = stack.get(CoreModule.GENOME.get());
            return g == null ? List.of() : g.species().unwrapKey().<List<ItemAttribute>>map(k -> List.of(new Species(k.location()))).orElse(List.of());
        }

        @Override
        public MapCodec<? extends ItemAttribute> codec() {
            return Species.CODEC;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
            return Species.STREAM_CODEC;
        }
    });

    public static final DeferredHolder<ItemAttributeType, ItemAttributeType> STAT_AT_LEAST = TYPES.register("stat_at_least", () -> new ItemAttributeType() {
        @Override
        public ItemAttribute createAttribute() {
            return new StatAtLeast(CoreModule.GROWTH.getId(), 5);
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            Genome g = stack.get(CoreModule.GENOME.get());
            if (g == null || !CultivarSeedItem.statsVisible(stack)) {
                return List.of();
            }
            List<ItemAttribute> out = new ArrayList<>();
            var expression = Genetics.current();
            CoreModule.STATS.getEntries().forEach(stat ->
                    out.add(new StatAtLeast(stat.getId(), Genetics.expressed(g, stat.getKey(), expression))));
            return out;
        }

        @Override
        public MapCodec<? extends ItemAttribute> codec() {
            return StatAtLeast.CODEC;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
            return StatAtLeast.STREAM_CODEC;
        }
    });

    public static final DeferredHolder<ItemAttributeType, ItemAttributeType> ASSAYED = TYPES.register("assayed", () -> new ItemAttributeType() {
        @Override
        public ItemAttribute createAttribute() {
            return Assayed.INSTANCE;
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            return stack.has(CoreModule.GENOME.get()) ? List.of(Assayed.INSTANCE) : List.of();
        }

        @Override
        public MapCodec<? extends ItemAttribute> codec() {
            return Assayed.CODEC;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
            return Assayed.STREAM_CODEC;
        }
    });

    private CultivarAttributes() {}

    public record Species(ResourceLocation species) implements ItemAttribute {
        static final MapCodec<Species> CODEC = ResourceLocation.CODEC.xmap(Species::new, Species::species).fieldOf("species");
        static final StreamCodec<RegistryFriendlyByteBuf, Species> STREAM_CODEC =
                ResourceLocation.STREAM_CODEC.map(Species::new, Species::species).cast();

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            Genome g = stack.get(CoreModule.GENOME.get());
            return g != null && g.species().unwrapKey().map(k -> k.location().equals(species)).orElse(false);
        }

        @Override
        public ItemAttributeType getType() {
            return SPECIES.get();
        }

        @Override
        public String getTranslationKey() {
            return "cultivar_species";
        }

        @Override
        public Object[] getTranslationParameters() {
            return new Object[]{CultivarSeedItem.plantName(species)};
        }
    }

    public record StatAtLeast(ResourceLocation stat, int value) implements ItemAttribute {
        static final MapCodec<StatAtLeast> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("stat").forGetter(StatAtLeast::stat),
                Codec.INT.fieldOf("value").forGetter(StatAtLeast::value)
        ).apply(i, StatAtLeast::new));
        static final StreamCodec<RegistryFriendlyByteBuf, StatAtLeast> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, StatAtLeast::stat, ByteBufCodecs.VAR_INT, StatAtLeast::value, StatAtLeast::new);

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            Genome g = stack.get(CoreModule.GENOME.get());
            if (g == null || !CultivarSeedItem.statsVisible(stack)) {
                return false;
            }
            ResourceKey<Stat> key = ResourceKey.create(CultivarRegistries.STAT_KEY, stat);
            return Genetics.expressed(g, key, Genetics.current()) >= value;
        }

        @Override
        public ItemAttributeType getType() {
            return STAT_AT_LEAST.get();
        }

        @Override
        public String getTranslationKey() {
            return "cultivar_stat_at_least";
        }

        @Override
        public Object[] getTranslationParameters() {
            return new Object[]{net.minecraft.network.chat.Component.translatable("stat." + stat.getNamespace() + "." + stat.getPath()), value};
        }
    }

    public enum Assayed implements ItemAttribute {
        INSTANCE;
        static final MapCodec<Assayed> CODEC = MapCodec.unit(INSTANCE);
        static final StreamCodec<RegistryFriendlyByteBuf, Assayed> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            return stack.has(CoreModule.ASSAYED.get());
        }

        @Override
        public ItemAttributeType getType() {
            return ASSAYED.get();
        }

        @Override
        public String getTranslationKey() {
            return "cultivar_assayed";
        }
    }
}
