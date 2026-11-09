package com.kragnoth.cultivar.api.plant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** One possible drop: {@code chance} to drop between {@code min} and {@code max} of {@code item}, per roll. */
public record Product(Holder<Item> item, int min, int max, float chance) {
    public static final Codec<Product> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(Product::item),
            Codec.intRange(0, 64).optionalFieldOf("min", 1).forGetter(Product::min),
            Codec.intRange(0, 64).optionalFieldOf("max", 1).forGetter(Product::max),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(Product::chance)
    ).apply(i, Product::new));
}
