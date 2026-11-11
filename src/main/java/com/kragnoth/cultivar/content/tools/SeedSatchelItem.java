package com.kragnoth.cultivar.content.tools;

import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

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

    /** One stored genome; {@code assayed} keeps the seed's assay status so it comes back out the same. */
    public record Entry(Genome genome, int count, boolean assayed) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Genome.CODEC.fieldOf("genome").forGetter(Entry::genome),
                Codec.intRange(1, CAPACITY).fieldOf("count").forGetter(Entry::count),
                Codec.BOOL.optionalFieldOf("assayed", false).forGetter(Entry::assayed)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Genome.STREAM_CODEC, Entry::genome, ByteBufCodecs.VAR_INT, Entry::count, ByteBufCodecs.BOOL, Entry::assayed, Entry::new);

        public ItemStack toStack() {
            return CultivarSeedItem.of(genome, assayed);
        }
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

    /** Seed chance for a satchel harvest: base + perVigour * Vigour (server config). */
    public static double seedChance(Genome genome) {
        int vigour = Genetics.expressed(genome, CoreModule.VIGOUR.getKey(), Genetics.current());
        double base = CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.SATCHEL_SEED_BASE.get() : 0.25;
        double per = CultivarServerConfig.SPEC.isLoaded() ? CultivarServerConfig.SATCHEL_SEED_PER_VIGOUR.get() : 0.05;
        return Math.min(1.0, base + per * vigour);
    }

    /**
     * Harvests a mature crop for the satchel: produce goes to {@code drops}; with {@link #seedChance} one unassayed
     * seed with the plant's exact genome goes through {@code bag}, which returns whatever didn't fit (dropped too).
     * Returns whether a seed was produced.
     */
    public static boolean gatherSeeds(CropBlockEntity crop, net.minecraft.util.RandomSource random,
                                      java.util.function.Consumer<ItemStack> drops,
                                      java.util.function.UnaryOperator<ItemStack> bag) {
        Genome genome = crop.genome();
        if (genome == null || !crop.harvest(drops)) {
            return false;
        }
        if (random.nextDouble() >= seedChance(genome)) {
            return false;
        }
        ItemStack rest = bag.apply(CultivarSeedItem.of(genome, crop.isAssayed()));
        if (!rest.isEmpty()) {
            drops.accept(rest);
        }
        return true;
    }

    /** Inserts as many of {@code seeds} as fit; returns how many were taken. */
    public static int insert(ItemStack satchel, ItemStack seeds) {
        Genome genome = seeds.get(CoreModule.GENOME.get());
        if (genome == null || !(seeds.getItem() instanceof CultivarSeedItem)) {
            return 0; // seeds only: cuttings must not turn into reliable seeds
        }
        boolean assayed = seeds.has(CoreModule.ASSAYED.get());
        Contents c = contents(satchel);
        if (c.species().isPresent() && !c.species().get().species().is(genome.species())) {
            return 0;
        }
        int moved = Math.min(CAPACITY - c.total(), seeds.getCount());
        if (moved <= 0) {
            return 0;
        }
        List<Entry> list = new ArrayList<>(c.entries());
        boolean merged = false;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (e.genome().equals(genome) && e.assayed() == assayed) {
                list.set(i, new Entry(genome, e.count() + moved, assayed));
                merged = true;
                break;
            }
        }
        if (!merged) {
            list.add(new Entry(genome, moved, assayed));
        }
        satchel.set(ToolsModule.SATCHEL_CONTENTS.get(), new Contents(list));
        return moved;
    }

    /** Dial value of an entry; unassayed seeds rank below every assayed one (their stats are unknown). */
    private static int rank(Entry e, Dial dial, Genetics.Expression expr) {
        return e.assayed() ? Genetics.expressed(e.genome(), dial.stat(), expr) : Integer.MIN_VALUE;
    }

    /** Removes one seed, chosen by the dial (best assayed seed for its stat) or oldest first without one. */
    public static Optional<ItemStack> takeOne(ItemStack satchel) {
        Contents c = contents(satchel);
        if (c.entries().isEmpty()) {
            return Optional.empty();
        }
        Dial dial = satchel.get(ToolsModule.SATCHEL_DIAL.get());
        int index = 0;
        if (dial != null) {
            Genetics.Expression expr = Genetics.current();
            List<Entry> entries = c.entries();
            index = entries.indexOf(entries.stream().max(Comparator.comparingInt(e -> rank(e, dial, expr))).orElseThrow());
        }
        return Optional.of(removeOne(satchel, c, index).toStack());
    }

    /** Seeds within this many points of the best (on the dial's stat) are keepers and never culled. */
    public static final int KEEP_WITHIN = 2;

    /**
     * Removes the single worst assayed seed that is not a keeper, or empty if none qualifies or there is no dial.
     * Keepers are within {@link #KEEP_WITHIN} of the best assayed seed. Unassayed seeds are never culled.
     */
    public static Optional<ItemStack> cullOne(ItemStack satchel) {
        Dial dial = satchel.get(ToolsModule.SATCHEL_DIAL.get());
        Contents c = contents(satchel);
        if (dial == null || c.entries().isEmpty()) {
            return Optional.empty();
        }
        Genetics.Expression expr = Genetics.current();
        List<Entry> assayed = c.entries().stream().filter(Entry::assayed).toList();
        int best = assayed.stream().mapToInt(e -> rank(e, dial, expr)).max().orElse(Integer.MIN_VALUE);
        Optional<Entry> worst = assayed.stream()
                .filter(e -> rank(e, dial, expr) < best - KEEP_WITHIN)
                .min(Comparator.comparingInt(e -> rank(e, dial, expr)));
        return worst.map(w -> removeOne(satchel, c, c.entries().indexOf(w)).toStack());
    }

    private static Entry removeOne(ItemStack satchel, Contents c, int index) {
        List<Entry> list = new ArrayList<>(c.entries());
        Entry e = list.get(index);
        if (e.count() > 1) {
            list.set(index, new Entry(e.genome(), e.count() - 1, e.assayed()));
        } else {
            list.remove(index);
        }
        satchel.set(ToolsModule.SATCHEL_CONTENTS.get(), new Contents(list));
        return e;
    }

    /** Satchel on the cursor, clicked onto a slot: right-click stores that slot's seeds, or drops one into an empty slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack satchel, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack other = slot.getItem();
        if (other.isEmpty()) {
            takeOne(satchel).ifPresent(slot::safeInsert);
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
            takeOne(satchel).ifPresent(cursor::set);
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
            if (satchel.getItem() instanceof SeedSatchelItem && !satchel.has(ToolsModule.SATCHEL_NO_COLLECT.get())
                    && contents(satchel).species()
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
        BlockState clicked = level.getBlockState(ctx.getClickedPos());
        if (clicked.is(Blocks.COMPOSTER) && ctx.isSecondaryUseActive()) {
            return cullIntoComposter(ctx, clicked);
        }
        if (!(CropBlockEntity.resolve(level, ctx.getClickedPos()).orElse(null) instanceof CropBlockEntity crop)) {
            return InteractionResult.PASS;
        }
        // On a mature plant: harvest it and gather seed. Players bag the seed; Deployers drop it for funnels.
        if (crop.isMature()) {
            if (!level.isClientSide()) {
                BlockPos at = crop.getBlockPos();
                boolean deployer = ctx.getPlayer() instanceof net.neoforged.neoforge.common.util.FakePlayer || ctx.getPlayer() == null;
                ItemStack satchel = ctx.getItemInHand();
                gatherSeeds(crop, level.random, drop -> Block.popResource(level, at, drop),
                        seed -> deployer ? seed : seed.copyWithCount(seed.getCount() - insert(satchel, seed)));
                level.playSound(null, at, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (crop.hasPlant() || level.getBlockState(ctx.getClickedPos()).getValue(TrellisBlock.CROSS)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack satchel = ctx.getItemInHand();
        Optional<ItemStack> seed = takeOne(satchel);
        if (seed.isEmpty()) {
            return InteractionResult.PASS;
        }
        crop.plant(seed.get().get(CoreModule.GENOME.get()), seed.get());
        level.playSound(null, ctx.getClickedPos(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        return InteractionResult.CONSUME;
    }

    /** Sneak-use on a composter: composts the worst non-keeper seeds until the composter is full. */
    private InteractionResult cullIntoComposter(UseOnContext ctx, BlockState composter) {
        if (!ctx.getItemInHand().has(ToolsModule.SATCHEL_DIAL.get())) {
            return InteractionResult.PASS;
        }
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockState state = composter;
        int culled = 0;
        while (state.getValue(ComposterBlock.LEVEL) < ComposterBlock.READY - 1) {
            Optional<ItemStack> worst = cullOne(ctx.getItemInHand());
            if (worst.isEmpty()) {
                break;
            }
            state = ComposterBlock.insertItem(ctx.getPlayer(), state, level, worst.get(), ctx.getClickedPos());
            culled++;
        }
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("item.cultivar.seed_satchel.culled", culled), true);
        }
        return culled > 0 ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack satchel = player.getItemInHand(hand);
        Dial dial = satchel.get(ToolsModule.SATCHEL_DIAL.get());
        if (!player.isShiftKeyDown() || dial == null) {
            if (level.isClientSide()) {
                openScreen.accept(hand); // see what's inside, take or void seeds, toggle auto-collect
            }
            return InteractionResultHolder.sidedSuccess(satchel, level.isClientSide());
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

    // ---------------------------------------------------------------- satchel screen

    /** Set by the client module: opens the satchel screen for the satchel in that hand. */
    public static java.util.function.Consumer<InteractionHand> openScreen = hand -> {};

    public static List<Entry> entries(ItemStack satchel) {
        return contents(satchel).entries();
    }

    /** Applies a screen action server-side, after checking the player really holds a satchel in that hand. */
    public static void apply(net.minecraft.server.level.ServerPlayer player, SatchelActionPayload p) {
        ItemStack satchel = player.getItemInHand(p.hand());
        if (!(satchel.getItem() instanceof SeedSatchelItem)) {
            return;
        }
        List<Entry> list = new ArrayList<>(contents(satchel).entries());
        switch (p.action()) {
            case TOGGLE_COLLECT -> {
                if (satchel.has(ToolsModule.SATCHEL_NO_COLLECT.get())) {
                    satchel.remove(ToolsModule.SATCHEL_NO_COLLECT.get());
                } else {
                    satchel.set(ToolsModule.SATCHEL_NO_COLLECT.get(), net.minecraft.util.Unit.INSTANCE);
                }
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.4f);
                return;
            }
            case VOID_UNASSAYED -> list.removeIf(e -> !e.assayed());
            case TAKE, VOID -> {
                if (p.index() < 0 || p.index() >= list.size()) {
                    return;
                }
                Entry e = list.remove(p.index());
                if (p.action() == SatchelActionPayload.Action.TAKE) {
                    player.getInventory().placeItemBackInInventory(e.toStack().copyWithCount(e.count()));
                }
            }
        }
        satchel.set(ToolsModule.SATCHEL_CONTENTS.get(), new Contents(list));
        player.playNotifySound(p.action() == SatchelActionPayload.Action.TAKE ? SoundEvents.BUNDLE_REMOVE_ONE : SoundEvents.COMPOSTER_FILL,
                SoundSource.PLAYERS, 0.6f, 1f);
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
        if (stack.has(ToolsModule.SATCHEL_NO_COLLECT.get())) {
            tooltip.add(Component.translatable("item.cultivar.seed_satchel.no_collect").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
