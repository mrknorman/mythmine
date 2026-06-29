package com.mythstack.variant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The overlay component carried by a mixed pile — the "ghost" data (plan §6.2).
 *
 * <p>{@code contents} is the true composition in canonical order; its counts sum to the host
 * {@code ItemStack}'s count (the {@code sum==count} invariant, maintained by {@link VariantPiles}).
 * {@code selected} is the variant index highlighted in the separation UI (-1 = none). The host item
 * is the variant group's canonical member, so to vanilla the pile simply <em>is</em> a stack of
 * canonical items.
 */
public record VariantPile(List<Entry> contents, int selected) {

	public record Entry(Item item, int count) {
	}

	public int total() {
		int sum = 0;
		for (Entry entry : contents) {
			sum += entry.count();
		}
		return sum;
	}

	public static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Entry::item),
			Codec.INT.fieldOf("count").forGetter(Entry::count)
	).apply(instance, Entry::new));

	public static final Codec<VariantPile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ENTRY_CODEC.listOf().fieldOf("contents").forGetter(VariantPile::contents),
			Codec.INT.optionalFieldOf("selected", -1).forGetter(VariantPile::selected)
	).apply(instance, VariantPile::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.registry(Registries.ITEM), Entry::item,
			ByteBufCodecs.VAR_INT, Entry::count,
			Entry::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, VariantPile> STREAM_CODEC = StreamCodec.composite(
			ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list()), VariantPile::contents,
			ByteBufCodecs.VAR_INT, VariantPile::selected,
			VariantPile::new);
}
