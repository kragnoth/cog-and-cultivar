package com.kragnoth.cultivar.content.tools;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.genetics.Genome;
import com.kragnoth.cultivar.api.genetics.Stat;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.core.genetics.Genetics;
import com.kragnoth.cultivar.content.crop.CropBlockEntity;
import com.kragnoth.cultivar.content.crop.CultivarSeedItem;
import com.kragnoth.cultivar.content.crop.TrellisBlock;
import com.kragnoth.cultivar.core.config.CultivarServerConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Holds up to {@link #CAPACITY} seeds of one species, bundle-style: right-click seeds onto it (or it onto
 * seeds) to store, right-click an empty slot or with an empty cursor to take one out. Used on a trellis it
 * plants a stored seed, and picked-up seeds of its species are collected automatically. With a Brass Dial fitted it picks the best seed for the dial's stat; sneak-use in
 * the air cycles the stat.
 */
public class SeedSatchelItem extends Item {
    public static final int CAPACITY = 64;

    public record Entry(Genome genome, int count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Genome.CODEC.fieldOf("genome").forGetter(Entry::genome),
                Codec.intRange(1, CAPACITY).fieldOf("count").forGetter(Entry::count)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Genome.STREAM_CODEC, Entry::genome, ByteBufCodecs.VAR_INT, Entry::count, Entry::new);
    }

    public record Contents(List<Entry> entries) {
        public static final Contents EMPTY = new Contents(List.of());
        public static final Codec<Contents> CODEC = Entry.CODEC.listOf().xmap(Contents::new, Contents::entries);
        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> STREAM_CODEC =
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Contents::new, Contents::entries);

        public Contents {
            entries = List.copyOf(entries);
        }

        public int total() {
            return entries.stream().mapToInt(Entry::count).sum();
        }

        public Optional<Genome> species() {
            return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getFirst().genome());
        }
    }

    /** Fitted Brass Dial: which stat to sort by. */
    public record Dial(ResourceKey<Stat> stat) {
        public static final Codec<Dial> CODEC = ResourceKey.codec(CultivarRegistries.STAT_KEY).xmap(Dial::new, Dial::stat);
        public static final StreamCodec<RegistryFriendlyByteBuf, Dial> STREAM_CODEC =
                ResourceKey.streamCodec(CultivarRegistries.STAT_KEY).map(Dial::new, Dial::stat).cast();
    }

    public SeedSatchelItem(Properties properties) {
        super(properties);
    }

    private static Contents contents(ItemStack satchel) {
        return satchel.getOrDefault(ToolsModule.SATCHEL_CONTENTS.get(), Contents.EMPTY);
    }

    // ---------------------------------------------------------------- store / take

    /** Inserts as many of {@code seeds} as fit; returns how many were taken. */
    public static int insert(ItemStack satchel, ItemStack seeds) {
        Genome genome = seeds.get(CoreModule.GENOME.get());
        if (genome == null) {
            return 0;
        }
        Contents c = contents(satchel);
        if (c.species().isPresent() && !c.species().get().species().is(genome.species())) {
            return 0;
        }
        int room = CAPACITY - c.total();
        int moved = Math.min(room, seeds.getCount());
        if (moved <= 0) {
            return 0;
        }
        List<Entry> list = new ArrayList<>(c.entries());
        boolean merged = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).genome().equals(genome)) {
                list.set(i, new Entry(genome, list.get(i).count() + moved));
                merged = true;
                break;
            }
        }
        if (!merged) {
            list.add(new Entry(genome, moved));
        }
        satchel.set(ToolsModule.SATCHEL_CONTENTS.get(), new Contents(list));
        return moved;
    }

    /** Removes one seed, chosen by the dial (best for its stat) or oldest first without one. */
    public static Optional<Genome> takeOne(ItemStack satchel) {
        Contents c = contents(satchel);
        if (c.entries().isEmpty()) {
            return Optional.empty();
        }
        Dial dial = satchel.get(ToolsModule.SATCHEL_DIAL.get());
        int index = 0;
        if (dial != null) {
            Genetics.Expression expr = Genetics.Expression.MAX;
            List<Entry> entries = c.entries();
            index = entries.indexOf(entries.stream()
                    .max(Comparator.comparingInt(e -> Genetics.expressed(e.genome(), dial.stat(), expr))).orElseThrow());
        }
        List<Entry> list = new ArrayList<>(c.entries());
        Entry e = list.get(index);
        if (e.count() > 1) {
            list.set(index, new Entry(e.genome(), e.count() - 1));
        } else {
            list.remove(index);
        }
        satchel.set(ToolsModule.SATCHEL_CONTENTS.get(), new Contents(list));
        return Optional.of(e.genome());
    }

    /** Satchel on the cursor, clicked onto a slot: right-click stores that slot's seeds, or drops one into an empty slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack satchel, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack other = slot.getItem();
        if (other.isEmpty()) {
            takeOne(satchel).ifPresent(g -> slot.safeInsert(CultivarSeedItem.of(g)));
            return true;
        }
        if (other.has(CoreModule.GENOME.get())) {
            int moved = insert(satchel, other);
            if (moved > 0) {
                slot.safeTake(moved, moved, player);
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 1f);
            }
            return true;
        }
        return false;
    }

    /** Something on the cursor, right-clicked onto the satchel: store seeds, fit a dial, or take a seed out. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack satchel, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess cursor) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        if (other.isEmpty()) {
            takeOne(satchel).ifPresent(g -> cursor.set(CultivarSeedItem.of(g)));
            return true;
        }
        if (other.is(ToolsModule.BRASS_DIAL.get()) && !satchel.has(ToolsModule.SATCHEL_DIAL.get())) {
            satchel.set(ToolsModule.SATCHEL_DIAL.get(), new Dial(CoreModule.GROWTH.getKey()));
            other.shrink(1);
            player.playSound(SoundEvents.SMITHING_TABLE_USE, 0.6f, 1.4f);
            return true;
        }
        if (other.has(CoreModule.GENOME.get())) {
            int moved = insert(satchel, other);
            if (moved > 0) {
                other.shrink(moved);
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 1f);
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- auto-pickup

    /**
     * Picked-up seeds go straight into a satchel already holding that species (first match in the inventory).
     * Empty satchels don't grab seeds, so players choose what each satchel collects.
     */
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity entity = event.getItemEntity();
        ItemStack seeds = entity.getItem();
        Genome genome = seeds.get(CoreModule.GENOME.get());
        Player player = event.getPlayer();
        if (genome == null || player.level().isClientSide() || entity.hasPickUpDelay()) {
            return;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !seeds.isEmpty(); i++) {
            ItemStack satchel = inv.getItem(i);
            if (satchel.getItem() instanceof SeedSatchelItem && contents(satchel).species()
                    .map(g -> g.species().is(genome.species())).orElse(false)) {
                int moved = insert(satchel, seeds);
                if (moved > 0) {
                    player.take(entity, moved);
                    seeds.shrink(moved);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.4f, 1.2f);
                }
            }
        }
        if (seeds.isEmpty()) {
            entity.discard();
        }
    }

    // ---------------------------------------------------------------- world use

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof CropBlockEntity crop) || crop.hasPlant()
                || level.getBlockState(ctx.getClickedPos()).getValue(TrellisBlock.CROSS)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack satchel = ctx.getItemInHand();
        Optional<Genome> seed = takeOne(satchel);
        if (seed.isEmpty()) {
            return InteractionResult.PASS;
        }
        crop.plant(seed.get());
        level.playSound(null, ctx.getClickedPos(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack satchel = player.getItemInHand(hand);
        Dial dial = satchel.get(ToolsModule.SATCHEL_DIAL.get());
        if (!player.isShiftKeyDown() || dial == null) {
            return InteractionResultHolder.pass(satchel);
        }
        if (!level.isClientSide()) {
            List<ResourceKey<Stat>> stats = CoreModule.STATS.getEntries().stream().map(h -> h.getKey()).toList();
            int next = (stats.indexOf(dial.stat()) + 1) % stats.size();
            satchel.set(ToolsModule.SATCHEL_DIAL.get(), new Dial(stats.get(next)));
            player.displayClientMessage(Component.translatable("item.cultivar.seed_satchel.sorting",
                    Component.translatable("stat.cultivar." + stats.get(next).location().getPath())), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return InteractionResultHolder.sidedSuccess(satchel, level.isClientSide());
    }

    // ---------------------------------------------------------------- display

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return contents(stack).total() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * contents(stack).total() / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xD6B055;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Contents c = contents(stack);
        c.species().ifPresentOrElse(
                g -> tooltip.add(CultivarSeedItem.plantName(g).copy().append(" " + c.total() + "/" + CAPACITY).withStyle(ChatFormatting.GRAY)),
                () -> tooltip.add(Component.translatable("item.cultivar.seed_satchel.empty").withStyle(ChatFormatting.DARK_GRAY)));
        Dial dial = stack.get(ToolsModule.SATCHEL_DIAL.get());
        if (dial != null) {
            tooltip.add(Component.translatable("item.cultivar.seed_satchel.sorting",
                    Component.translatable("stat.cultivar." + dial.stat().location().getPath())).withStyle(ChatFormatting.GOLD));
        }
    }
}
