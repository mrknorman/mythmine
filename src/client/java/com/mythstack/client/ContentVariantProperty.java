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
 * A custom item-model {@code select} property — {@code mythstack:content_variant} — returning the
 * id <em>string</em> of the item at slot {@code index} of a pile's {@link VariantPile} contents (or
 * {@code minecraft:air} if there are fewer). The pile model selects each of its (up to 3) layers on
 * this property, so the icon shows the actual contained woods.
 *
 * <p>The value type is a plain {@code String} (not {@code Item}) on purpose: a registry value type
 * routes the select through {@code SelectItemModel}'s {@code RegistryContextSwapper} remapping path,
 * which silently drops/mis-maps cases — every vanilla select uses strings/enums. Using a string keeps
 * us on the simple, correct path.
 */
public record ContentVariantProperty(int index) implements SelectItemModelProperty<String> {

	public static final SelectItemModelProperty.Type<ContentVariantProperty, String> TYPE =
			SelectItemModelProperty.Type.create(
					RecordCodecBuilder.mapCodec(instance -> instance.group(
							Codec.INT.fieldOf("index").forGetter(ContentVariantProperty::index)
					).apply(instance, ContentVariantProperty::new)),
					Codec.STRING);

	/** Diagnostic: the (index=id) selected on the most recent call, read by the render diagnostic. */
	public static final ThreadLocal<String> LAST_SELECTED = new ThreadLocal<>();
	private static final Set<String> DEBUG_SEEN = ConcurrentHashMap.newKeySet();

	@Override
	public String get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		Item item = (pile == null || index < 0 || index >= pile.contents().size())
				? Items.AIR
				: pile.contents().get(index).item();
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		LAST_SELECTED.set(index + "=" + id);
		return id;
	}

	@Override
	public Codec<String> valueCodec() {
		return Codec.STRING;
	}

	@Override
	public SelectItemModelProperty.Type<ContentVariantProperty, String> type() {
		return TYPE;
	}
}
