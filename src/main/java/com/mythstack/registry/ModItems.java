package com.mythstack.registry;

import com.mythstack.MythStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Item registry — the typed sticks (spec §13, phase A). One stick per family wood so stick-consuming
 * crafts can carry wood identity; vanilla {@code minecraft:stick} is the oak/canonical member of the
 * STICKS variant group, so no oak duplicate exists. All use the vanilla stick texture for now.
 */
public final class ModItems {
	private ModItems() {
	}

	public static final Item SPRUCE_STICK = stick("spruce_stick");
	public static final Item BIRCH_STICK = stick("birch_stick");
	public static final Item JUNGLE_STICK = stick("jungle_stick");
	public static final Item ACACIA_STICK = stick("acacia_stick");
	public static final Item DARK_OAK_STICK = stick("dark_oak_stick");
	public static final Item MANGROVE_STICK = stick("mangrove_stick");
	public static final Item CHERRY_STICK = stick("cherry_stick");
	public static final Item PALE_OAK_STICK = stick("pale_oak_stick");

	/** The typed sticks in canonical wood order (vanilla stick — oak — is the group's canonical). */
	public static final List<Item> TYPED_STICKS = List.of(SPRUCE_STICK, BIRCH_STICK, JUNGLE_STICK,
			ACACIA_STICK, DARK_OAK_STICK, MANGROVE_STICK, CHERRY_STICK, PALE_OAK_STICK);

	private static Item stick(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MythStack.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/** Called from {@link MythStack#onInitialize()} to force class-load so the static fields register. */
	public static void initialize() {
	}
}
