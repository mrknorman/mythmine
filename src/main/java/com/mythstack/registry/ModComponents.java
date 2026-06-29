package com.mythstack.registry;

import com.mythstack.MythStack;
import com.mythstack.variant.VariantPile;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Data components registered by the mod. There is one — {@code mythstack:variant_pile} — the overlay
 * carried by a mixed pile (hosted on a vanilla item, so no new item type is registered).
 */
public final class ModComponents {
	private ModComponents() {
	}

	public static final DataComponentType<VariantPile> VARIANT_PILE = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			MythStack.id("variant_pile"),
			DataComponentType.<VariantPile>builder()
					.persistent(VariantPile.CODEC)
					.networkSynchronized(VariantPile.STREAM_CODEC)
					.build());

	/** Forces class-load so the static field registers. Called from {@link MythStack#onInitialize()}. */
	public static void initialize() {
	}
}
