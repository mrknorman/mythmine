package com.mythstack.registry;

import com.mythstack.MythStack;
import com.mythstack.mixin.BlockEntityTypeAccessor;
import com.mythstack.mixin.PoiTypesAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

	public static final List<Block> TYPED_BOOKSHELVES = WOODS.stream()
			.map(wood -> register(wood + "_bookshelf", Block::new,
					BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF), true))
			.toList();

	public static final List<Block> TYPED_CHISELED_BOOKSHELVES = WOODS.stream()
			.map(wood -> register(wood + "_chiseled_bookshelf", ChiseledBookShelfBlock::new,
					BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_BOOKSHELF), true))
			.toList();

	public static final List<Block> TYPED_BARRELS = WOODS.stream()
			.map(wood -> register(wood + "_barrel", BarrelBlock::new,
					BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL), true))
			.toList();

	public static final List<Block> TYPED_CRAFTING_TABLES = WOODS.stream()
			.map(wood -> register(wood + "_crafting_table", CraftingTableBlock::new,
					BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE), true))
			.toList();

	// The wooden utility stations (audit follow-up): the fletching table is a plain Block in 26.2.
	public static final List<Block> TYPED_FLETCHING_TABLES = station("fletching_table", Block::new, Blocks.FLETCHING_TABLE);
	public static final List<Block> TYPED_CARTOGRAPHY_TABLES = station("cartography_table", CartographyTableBlock::new, Blocks.CARTOGRAPHY_TABLE);
	public static final List<Block> TYPED_SMITHING_TABLES = station("smithing_table", SmithingTableBlock::new, Blocks.SMITHING_TABLE);
	public static final List<Block> TYPED_LOOMS = station("loom", LoomBlock::new, Blocks.LOOM);
	public static final List<Block> TYPED_LECTERNS = station("lectern", LecternBlock::new, Blocks.LECTERN);
	public static final List<Block> TYPED_COMPOSTERS = station("composter", ComposterBlock::new, Blocks.COMPOSTER);
	public static final List<Block> TYPED_NOTE_BLOCKS = station("note_block", NoteBlock::new, Blocks.NOTE_BLOCK);
	public static final List<Block> TYPED_JUKEBOXES = station("jukebox", JukeboxBlock::new, Blocks.JUKEBOX);
	public static final List<Block> TYPED_BEEHIVES = station("beehive", BeehiveBlock::new, Blocks.BEEHIVE);

	/** canonical vanilla block -> its typed variants, insertion-ordered for creative-tab placement. */
	public static final Map<Block, List<Block>> TYPED_FAMILIES = typedFamilies();

	private static Map<Block, List<Block>> typedFamilies() {
		Map<Block, List<Block>> families = new java.util.LinkedHashMap<>();
		families.put(Blocks.LADDER, TYPED_LADDERS);
		families.put(Blocks.CHEST, TYPED_CHESTS);
		families.put(Blocks.BOOKSHELF, TYPED_BOOKSHELVES);
		families.put(Blocks.CHISELED_BOOKSHELF, TYPED_CHISELED_BOOKSHELVES);
		families.put(Blocks.BARREL, TYPED_BARRELS);
		families.put(Blocks.CRAFTING_TABLE, TYPED_CRAFTING_TABLES);
		families.put(Blocks.FLETCHING_TABLE, TYPED_FLETCHING_TABLES);
		families.put(Blocks.CARTOGRAPHY_TABLE, TYPED_CARTOGRAPHY_TABLES);
		families.put(Blocks.SMITHING_TABLE, TYPED_SMITHING_TABLES);
		families.put(Blocks.LOOM, TYPED_LOOMS);
		families.put(Blocks.LECTERN, TYPED_LECTERNS);
		families.put(Blocks.COMPOSTER, TYPED_COMPOSTERS);
		families.put(Blocks.NOTE_BLOCK, TYPED_NOTE_BLOCKS);
		families.put(Blocks.JUKEBOX, TYPED_JUKEBOXES);
		families.put(Blocks.BEEHIVE, TYPED_BEEHIVES);
		return java.util.Collections.unmodifiableMap(families);
	}

	/** The sawmill: the stonecutter for wood (one block; the woodwork is in the recipes). */
	public static final Block SAWMILL = register("sawmill", com.mythstack.block.SawmillBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONECUTTER), true);

	private static List<Block> station(String name, Function<BlockBehaviour.Properties, Block> factory, Block canonical) {
		return WOODS.stream()
				.map(wood -> register(wood + "_" + name, factory,
						BlockBehaviour.Properties.ofFullCopy(canonical), true))
				.toList();
	}

	static Block register(String name,
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
		StoneKit.initialize();
		widen(net.minecraft.world.level.block.entity.BlockEntityTypes.FURNACE,
				StoneKit.FUNCTIONAL_ANCHORS.getOrDefault(net.minecraft.world.item.Items.FURNACE, List.of()));
		// Typed block-entity blocks join their vanilla types: without this a placed block's entity is
		// rejected as invalid and the block simply doesn't function.
		widen(BlockEntityTypes.CHEST, TYPED_CHESTS);
		widen(BlockEntityTypes.CHISELED_BOOKSHELF, TYPED_CHISELED_BOOKSHELVES);
		widen(BlockEntityTypes.BARREL, TYPED_BARRELS);
		widen(BlockEntityTypes.LECTERN, TYPED_LECTERNS);
		widen(BlockEntityTypes.JUKEBOX, TYPED_JUKEBOXES);
		widen(BlockEntityTypes.BEEHIVE, TYPED_BEEHIVES);

		// Typed blocks inherit their canonical's POI role (job sites, bee homes) — POI discovery is
		// keyed by block STATE. Missing vanilla mappings degrade silently (the block still works,
		// villagers/bees just won't claim it).
		Map<BlockState, Holder<PoiType>> byState = new HashMap<>(PoiTypesAccessor.mythstack$typeByState());
		boolean changed = false;
		Map<Block, List<Block>> poiFamilies = new HashMap<>();
		poiFamilies.put(Blocks.BARREL, TYPED_BARRELS);              // fisherman
		poiFamilies.put(Blocks.FLETCHING_TABLE, TYPED_FLETCHING_TABLES); // fletcher
		poiFamilies.put(Blocks.CARTOGRAPHY_TABLE, TYPED_CARTOGRAPHY_TABLES); // cartographer
		poiFamilies.put(Blocks.SMITHING_TABLE, TYPED_SMITHING_TABLES); // toolsmith
		poiFamilies.put(Blocks.LOOM, TYPED_LOOMS);                  // shepherd
		poiFamilies.put(Blocks.LECTERN, TYPED_LECTERNS);            // librarian
		poiFamilies.put(Blocks.COMPOSTER, TYPED_COMPOSTERS);        // farmer
		poiFamilies.put(Blocks.BEEHIVE, TYPED_BEEHIVES);            // bee home
		for (Map.Entry<Block, List<Block>> family : poiFamilies.entrySet()) {
			Holder<PoiType> poi = byState.get(family.getKey().defaultBlockState());
			if (poi == null) {
				continue;
			}
			for (Block typed : family.getValue()) {
				for (BlockState state : typed.getStateDefinition().getPossibleStates()) {
					byState.put(state, poi);
				}
			}
			changed = true;
		}
		if (changed) {
			PoiTypesAccessor.mythstack$setTypeByState(byState);
		}
	}

	private static void widen(BlockEntityType<?> type, List<Block> blocks) {
		BlockEntityTypeAccessor accessor = (BlockEntityTypeAccessor) (Object) type;
		Set<Block> widened = new HashSet<>(accessor.mythstack$validBlocks());
		widened.addAll(blocks);
		accessor.mythstack$setValidBlocks(Set.copyOf(widened));
	}
}
