package com.kragnoth.cultivar.content.ledger;

import com.kragnoth.cultivar.api.plant.Plant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code cultivar:learned_plant}: fires when a player's knowledge grows (and on login, so old saves catch up).
 * Conditions, all optional: {@code plant} (that species was learned), {@code bred} (a species that is some cross's
 * child was learned), {@code complete} (the player knows every loaded species). Pack makers can use it in their own
 * advancements and quests.
 */
public final class LearnedPlantTrigger extends SimpleCriterionTrigger<LearnedPlantTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Set<ResourceKey<Plant>> learned, boolean anyBred, boolean complete) {
        trigger(player, i -> i.matches(learned, anyBred, complete));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> plant, boolean bred,
                           boolean complete) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                ResourceLocation.CODEC.optionalFieldOf("plant").forGetter(Instance::plant),
                Codec.BOOL.optionalFieldOf("bred", false).forGetter(Instance::bred),
                Codec.BOOL.optionalFieldOf("complete", false).forGetter(Instance::complete)
        ).apply(i, Instance::new));

        boolean matches(Set<ResourceKey<Plant>> learned, boolean anyBred, boolean isComplete) {
            if (learned.isEmpty() && !complete) {
                return false;
            }
            return plant.map(id -> learned.stream().anyMatch(k -> k.location().equals(id))).orElse(true)
                    && (!bred || anyBred) && (!complete || isComplete);
        }
    }
}
