package com.mythstack.registry;

import com.mythstack.MythStack;
import com.mythstack.mixin.BlockEntityTypeAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Block registry. The typed ladders and chests (spec §13 fan-out): one per non-oak family wood, with
 * the vanilla block as the oak/canonical member — same shape as the typed sticks. Chests share
 * {@code BlockEntityType.CHEST} (its valid-blocks set is widened at init), which buys the whole
 * vanilla chest stack — renderer (classic look = the default texture for now), double-chest merging,
 * hoppers, comparators — with zero new block-entity code.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	/** The non-oak family woods, canonical order (matches the group tags). */
	private static final List<String> WOODS = List.of("spruce", "birch", "jungle", "acacia", "dark_oak",
			"mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped");

	public static final Block WHITE_BLOCK = register(
			"white_block",
			Block::new,
			BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.STONE),
			true
	);

	public static final List<Block> TYPED_LADDERS = WOODS.stream()
			.map(wood -> register(wood + "_ladder", LadderBlock::new,
					BlockBehaviour.Properties.ofFullCopy(Blocks.LADDER), true))
			.toList();

	public static final List<Block> TYPED_CHESTS = WOODS.stream()
			.map(wood -> register(wood + "_chest",
					properties -> new ChestBlock(() -> BlockEntityTypes.CHEST,
							SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties),
					BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST), true))
			.toList();

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

	/** True if {@code block} is a nether wood's (crimson/warped) — not furnace fuel, like its planks. */
	public static boolean netherWood(Block block) {
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return path.startsWith("crimson_") || path.startsWith("warped_");
	}

	/** Called from {@link MythStack#onInitialize()} to force class-load so the static fields register. */
	public static void initialize() {
		// The typed chests join the vanilla chest block-entity type: without this the placed block's
		// entity is rejected as invalid and the chest simply doesn't function.
		BlockEntityTypeAccessor chestType = (BlockEntityTypeAccessor) (Object) BlockEntityTypes.CHEST;
		Set<Block> widened = new HashSet<>(chestType.mythstack$validBlocks());
		widened.addAll(TYPED_CHESTS);
		chestType.mythstack$setValidBlocks(Set.copyOf(widened));
	}
}
