package com.mythstack.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mythstack.MythStack;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom item-model {@code select} property — {@code mythstack:content_variant} — that returns the
 * item at slot {@code index} of a pile's {@link VariantPile} contents (or AIR if there are fewer).
 * The pile model selects each of its (up to 3) layers on this property, so the icon shows the actual
 * contained woods. Registered into {@code SelectItemModelProperties.ID_MAPPER} (see access-widener).
 */
public record ContentVariantProperty(int index) implements SelectItemModelProperty<Item> {

	public static final SelectItemModelProperty.Type<ContentVariantProperty, Item> TYPE =
			SelectItemModelProperty.Type.create(
					RecordCodecBuilder.mapCodec(instance -> instance.group(
							Codec.INT.fieldOf("index").forGetter(ContentVariantProperty::index)
					).apply(instance, ContentVariantProperty::new)),
					BuiltInRegistries.ITEM.byNameCodec());

	/** Diagnostic: each distinct (index, size, result) is logged once so we can see what the model selects. */
	private static final Set<String> DEBUG_SEEN = ConcurrentHashMap.newKeySet();

	@Override
	public Item get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		Item result = (pile == null || index < 0 || index >= pile.contents().size())
				? Items.AIR
				: pile.contents().get(index).item();
		String key = index + "/" + (pile == null ? -1 : pile.contents().size()) + "/" + BuiltInRegistries.ITEM.getKey(result);
		if (DEBUG_SEEN.add(key)) {
			MythStack.LOGGER.info("[content_variant] index={} size={} ctx={} -> {}",
					index, pile == null ? -1 : pile.contents().size(), context, BuiltInRegistries.ITEM.getKey(result));
		}
		return result;
	}

	@Override
	public Codec<Item> valueCodec() {
		return BuiltInRegistries.ITEM.byNameCodec();
	}

	@Override
	public SelectItemModelProperty.Type<ContentVariantProperty, Item> type() {
		return TYPE;
	}
}
