package com.mythstack.registry;

import com.mythstack.MythStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

/**
 * Block registry for the mod. Right now this is just a single white test block to
 * prove the toolchain and creative-menu wiring (build phase 1). Real content arrives later.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final Block WHITE_BLOCK = register(
			"white_block",
			Block::new,
			BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.STONE),
			true
	);

	private static Block register(String name,
			Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties,
			boolean registerItem) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MythStack.id(name));
		Block block = factory.apply(properties.setId(blockKey));

		if (registerItem) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MythStack.id(name));
			BlockItem blockItem = new BlockItem(block,
					new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
			Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
		}

		return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
	}

	/** Called from {@link MythStack#onInitialize()} to force class-load so the static fields register. */
	public static void initialize() {
	}
}
