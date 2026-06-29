package com.mythstack.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

	@Override
	public Item get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null || index < 0 || index >= pile.contents().size()) {
			return Items.AIR;
		}
		return pile.contents().get(index).item();
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
