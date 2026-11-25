package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Mutation;
import com.kragnoth.cultivar.api.plant.Plant;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Botanical Ledger: records species. Use it on a planted trellis to record that species; the Assayers and
 * Trial Bed record into a Ledger in their slot. Carrying or reading a Ledger merges it with the holder's
 * {@link Knowledge} both ways, so the player keeps what they learned and a shared Ledger teaches its reader.
 * A mutation shows up once you know its child and both parents, so discovery stays progressive.
 */
public class LedgerItem extends Item {
    /**
     * A half-known cross: the child and one parent ({@code shown}) are revealed, the other parent stays a "?" until the
     * player learns it. Left by an Assayer's family-tree discovery for crosses a plant leads to.
     */
    public record Hint(ResourceKey<Mutation> cross, ResourceKey<Plant> shown) {
        public static final Codec<Hint> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(CultivarRegistries.MUTATION).fieldOf("cross").forGetter(Hint::cross),
                ResourceKey.codec(CultivarRegistries.PLANT).fieldOf("shown").forGetter(Hint::shown)
        ).apply(i, Hint::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hint> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(CultivarRegistries.MUTATION), Hint::cross,
                ResourceKey.streamCodec(CultivarRegistries.PLANT), Hint::shown, Hint::new);
    }

    /**
     * Recorded species, plus half-known crosses ({@link Hint}). Stored by key so removed datapack plants just drop out
     * of the count. Old saves (a bare list of species) still load.
     */
    public record Data(Set<ResourceKey<Plant>> researched, Set<Hint> hints) {
        public static final Data EMPTY = new Data(Set.of(), Set.of());
        private static final Codec<Data> FULL = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(CultivarRegistries.PLANT).listOf().fieldOf("plants").forGetter(d -> List.copyOf(d.researched())),
                Hint.CODEC.listOf().optionalFieldOf("hints", List.of()).forGetter(d -> List.copyOf(d.hints()))
        ).apply(i, (plants, hints) -> new Data(Set.copyOf(plants), Set.copyOf(hints))));
        private static final Codec<Data> LEGACY = ResourceKey.codec(CultivarRegistries.PLANT).listOf()
                .xmap(list -> new Data(Set.copyOf(list), Set.of()), d -> List.copyOf(d.researched()));
        public static final Codec<Data> CODEC = Codec.withAlternative(FULL, LEGACY);
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(CultivarRegistries.PLANT).apply(ByteBufCodecs.list()), d -> List.copyOf(d.researched()),
                Hint.STREAM_CODEC.apply(ByteBufCodecs.list()), d -> List.copyOf(d.hints()),
                (plants, hints) -> new Data(Set.copyOf(plants), Set.copyOf(hints)));

        public Data {
            researched = Set.copyOf(researched);
            hints = Set.copyOf(hints);
        }

        public Data(Set<ResourceKey<Plant>> researched) {
            this(researched, Set.of());
        }

        public boolean knows(ResourceKey<Plant> plant) {
            return researched.contains(plant);
        }

        public Data with(ResourceKey<Plant> plant) {
            Set<ResourceKey<Plant>> copy = new HashSet<>(researched);
            copy.add(plant);
            return new Data(copy, hints);
        }

        public Data withHints(java.util.Collection<Hint> more) {
            Set<Hint> copy = new HashSet<>(hints);
            copy.addAll(more);
            return new Data(researched, copy);
        }

        /** Both sides' species and hints. */
        public Data merge(Data other) {
            Set<ResourceKey<Plant>> plants = new HashSet<>(researched);
            plants.addAll(other.researched);
            Set<Hint> all = new HashSet<>(hints);
            all.addAll(other.hints);
            return new Data(plants, all);
        }

        /** The hint for {@code cross}, if any. */
        public java.util.Optional<Hint> hintFor(ResourceKey<Mutation> cross) {
            return hints.stream().filter(h -> h.cross().equals(cross)).findFirst();
        }
    }

    /** Set by the client module to open the ledger screen; a no-op on dedicated servers. */
    public static Consumer<ItemStack> openScreen = stack -> {};

    public LedgerItem(Properties properties) {
        super(properties);
    }

    public static Data data(ItemStack stack) {
        return stack.getOrDefault(LedgerModule.LEDGER_DATA.get(), Data.EMPTY);
    }

    /** Adds half-known crosses; returns how many were new. Server-side. */
    public static int hint(ItemStack ledger, java.util.Collection<Hint> hints) {
        Data d = data(ledger);
        Data next = d.withHints(hints);
        int added = next.hints().size() - d.hints().size();
        if (added > 0) {
            ledger.set(LedgerModule.LEDGER_DATA.get(), next);
        }
        return added;
    }

    /** Records a species; returns true if it was new. Server-side. */
    public static boolean record(ItemStack ledger, ResourceKey<Plant> plant) {
        Data d = data(ledger);
        if (d.knows(plant)) {
            return false;
        }
        ledger.set(LedgerModule.LEDGER_DATA.get(), d.with(plant));
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(CropBlockEntity.resolve(level, ctx.getClickedPos()).orElse(null) instanceof CropBlockEntity crop) || crop.genome() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            var genome = crop.genome();
            genome.species().unwrapKey().ifPresent(key -> {
                boolean added = record(ctx.getItemInHand(), key);
                if (ctx.getPlayer() instanceof ServerPlayer sp) {
                    Knowledge.exchange(sp, ctx.getItemInHand());
                }
                if (ctx.getPlayer() != null) {
                    ctx.getPlayer().displayClientMessage(Component.translatable(added ? "item.cultivar.botanical_ledger.recorded"
                            : "item.cultivar.botanical_ledger.known", CultivarSeedItem.plantName(genome)), true);
                }
                if (added) {
                    level.playSound(null, ctx.getClickedPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
                }
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Knowledge.exchange(sp, stack);
        } else if (level.isClientSide()) {
            player.playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
            openScreen.accept(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** A carried Ledger keeps in step with its holder, once a second. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (entity instanceof ServerPlayer sp && (level.getGameTime() + entity.getId()) % 20 == 0) {
            Knowledge.exchange(sp, stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cultivar.botanical_ledger.count", data(stack).researched().size())
                .withStyle(ChatFormatting.GRAY));
    }
}
