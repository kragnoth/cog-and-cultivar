package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.api.CultivarRegistries;
import com.kragnoth.cultivar.api.plant.Plant;
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
 * Botanical Ledger: records species. Use it on a planted trellis to record that species; the Assaying Engine and
 * Trial Bed record into a Ledger in their slot. Carrying or reading a Ledger merges it with the holder's
 * {@link Knowledge} both ways, so the player keeps what they learned and a shared Ledger teaches its reader.
 * A mutation shows up once you know its child and both parents, so discovery stays progressive.
 */
public class LedgerItem extends Item {
    /** Recorded species. Stored by key so removed datapack plants just drop out of the count. */
    public record Data(Set<ResourceKey<Plant>> researched) {
        public static final Data EMPTY = new Data(Set.of());
        public static final Codec<Data> CODEC = ResourceKey.codec(CultivarRegistries.PLANT).listOf()
                .xmap(list -> new Data(Set.copyOf(list)), d -> List.copyOf(d.researched()));
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC =
                ResourceKey.streamCodec(CultivarRegistries.PLANT).apply(ByteBufCodecs.list())
                        .<Data>map(list -> new Data(Set.copyOf(list)), d -> List.copyOf(d.researched())).cast();

        public Data {
            researched = Set.copyOf(researched);
        }

        public boolean knows(ResourceKey<Plant> plant) {
            return researched.contains(plant);
        }

        public Data with(ResourceKey<Plant> plant) {
            Set<ResourceKey<Plant>> copy = new HashSet<>(researched);
            copy.add(plant);
            return new Data(copy);
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
