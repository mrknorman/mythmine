package com.mythstack.variant;

import com.mythstack.MythStack;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declares the wood {@link VariantGroup}s and resolves an item to its group.
 *
 * <p>Planks and most forms reuse vanilla family tags directly; the raw log forms use our own
 * tags because vanilla conflates log / wood / stripped under {@code #*_logs}. All vanilla wood
 * materials are in — including nether (crimson/warped) and bamboo (D4 reversed): bamboo_block
 * takes the log slot, forms a wood lacks (bamboo hyphae, nether boats) are simply absent, and
 * the crafting layer handles the gaps (unproductive woods never receive attribution).
 *
 * <p>Resolution goes through a {@link #membership} snapshot rebuilt whenever tags load/sync
 * ({@code CommonLifecycleEvents.TAGS_LOADED}). A per-call {@code holder.is(tag)} check is unreliable
 * during client-side container prediction (the tag bindings aren't always present on the client),
 * which previously made {@link #of} return null and pile hosts collapse onto the top wood.
 */
public final class VariantGroups {
	private VariantGroups() {
	}

	// One group per wood FORM, keyed by wood type, canonical = the oak member (spec §D2). Membership comes
	// from a tag: a vanilla #wooden_*/family tag where one cleanly exists (so modded woods tag in for free),
	// or our own #mythstack:wood/* tag for the raw log forms (vanilla #*_logs conflate log/wood/stripped).
	public static final VariantGroup LOGS = group("logs", custom("wood/logs"), Items.OAK_LOG);
	public static final VariantGroup WOODS = group("wood", custom("wood/wood"), Items.OAK_WOOD);
	public static final VariantGroup STRIPPED_LOGS = group("stripped_logs", custom("wood/stripped_logs"), Items.STRIPPED_OAK_LOG);
	public static final VariantGroup STRIPPED_WOODS = group("stripped_wood", custom("wood/stripped_wood"), Items.STRIPPED_OAK_WOOD);
	public static final VariantGroup PLANKS = group("planks", vanillaTag("planks"), Items.OAK_PLANKS);
	public static final VariantGroup STAIRS = group("stairs", vanillaTag("wooden_stairs"), Items.OAK_STAIRS);
	public static final VariantGroup SLABS = group("slabs", vanillaTag("wooden_slabs"), Items.OAK_SLAB);
	public static final VariantGroup FENCES = group("fences", vanillaTag("wooden_fences"), Items.OAK_FENCE);
	public static final VariantGroup FENCE_GATES = group("fence_gates", vanillaTag("fence_gates"), Items.OAK_FENCE_GATE);
	public static final VariantGroup DOORS = group("doors", vanillaTag("wooden_doors"), Items.OAK_DOOR);
	public static final VariantGroup TRAPDOORS = group("trapdoors", vanillaTag("wooden_trapdoors"), Items.OAK_TRAPDOOR);
	public static final VariantGroup PRESSURE_PLATES = group("pressure_plates", vanillaTag("wooden_pressure_plates"), Items.OAK_PRESSURE_PLATE);
	public static final VariantGroup BUTTONS = group("buttons", vanillaTag("wooden_buttons"), Items.OAK_BUTTON);
	public static final VariantGroup SHELVES = group("shelves", vanillaTag("wooden_shelves"), Items.OAK_SHELF);
	public static final VariantGroup SIGNS = group("signs", vanillaTag("signs"), Items.OAK_SIGN);
	public static final VariantGroup HANGING_SIGNS = group("hanging_signs", vanillaTag("hanging_signs"), Items.OAK_HANGING_SIGN);
	// Leaves: #minecraft:leaves also tags azalea / flowering-azalea (not a wood, but harmless to pile in).
	public static final VariantGroup LEAVES = group("leaves", vanillaTag("leaves"), Items.OAK_LEAVES);
	// Typed sticks / ladders / chests (spec §13): the vanilla item is the oak/canonical member; the
	// other family woods' variants are ours.
	public static final VariantGroup STICKS = group("sticks", custom("wood/sticks"), Items.STICK);
	public static final VariantGroup LADDERS = group("ladders", custom("wood/ladders"), Items.LADDER);
	public static final VariantGroup CHESTS = group("chests", custom("wood/chests"), Items.CHEST);
	public static final VariantGroup BOOKSHELVES = group("bookshelves", custom("wood/bookshelves"), Items.BOOKSHELF);
	public static final VariantGroup CHISELED_BOOKSHELVES = group("chiseled_bookshelves", custom("wood/chiseled_bookshelves"), Items.CHISELED_BOOKSHELF);
	public static final VariantGroup BARRELS = group("barrels", custom("wood/barrels"), Items.BARREL);
	public static final VariantGroup CRAFTING_TABLES = group("crafting_tables", custom("wood/crafting_tables"), Items.CRAFTING_TABLE);
	public static final VariantGroup FLETCHING_TABLES = group("fletching_tables", custom("wood/fletching_tables"), Items.FLETCHING_TABLE);
	public static final VariantGroup CARTOGRAPHY_TABLES = group("cartography_tables", custom("wood/cartography_tables"), Items.CARTOGRAPHY_TABLE);
	public static final VariantGroup SMITHING_TABLES = group("smithing_tables", custom("wood/smithing_tables"), Items.SMITHING_TABLE);
	public static final VariantGroup LOOMS = group("looms", custom("wood/looms"), Items.LOOM);
	public static final VariantGroup LECTERNS = group("lecterns", custom("wood/lecterns"), Items.LECTERN);
	public static final VariantGroup COMPOSTERS = group("composters", custom("wood/composters"), Items.COMPOSTER);
	public static final VariantGroup NOTE_BLOCKS = group("note_blocks", custom("wood/note_blocks"), Items.NOTE_BLOCK);
	public static final VariantGroup JUKEBOXES = group("jukeboxes", custom("wood/jukeboxes"), Items.JUKEBOX);
	public static final VariantGroup BEEHIVES = group("beehives", custom("wood/beehives"), Items.BEEHIVE);
	// Saplings: #minecraft:saplings also tags azalea/flowering azalea (like leaves — harmless to pile).
	public static final VariantGroup SAPLINGS = group("saplings", vanillaTag("saplings"), Items.OAK_SAPLING);

	// The ORE families — the first non-wood groups: each ore's stone + deepslate (+ nether, for gold)
	// variants pile together, canonical = the stone form. Vanilla ships the membership tags. The
	// pile-aware furnace already smelts these per element (phase 8), so a mixed ore pile just works.
	public static final VariantGroup IRON_ORES = ores("iron_ores", Items.IRON_ORE);
	public static final VariantGroup COAL_ORES = ores("coal_ores", Items.COAL_ORE);
	public static final VariantGroup COPPER_ORES = ores("copper_ores", Items.COPPER_ORE);
	public static final VariantGroup GOLD_ORES = ores("gold_ores", Items.GOLD_ORE);
	public static final VariantGroup REDSTONE_ORES = ores("redstone_ores", Items.REDSTONE_ORE);
	public static final VariantGroup EMERALD_ORES = ores("emerald_ores", Items.EMERALD_ORE);
	public static final VariantGroup LAPIS_ORES = ores("lapis_ores", Items.LAPIS_ORE);
	public static final VariantGroup DIAMOND_ORES = ores("diamond_ores", Items.DIAMOND_ORE);

	// The pile cap is per-group ({@link VariantGroup#cap} = the canonical's max stack size), so signs
	// (cap 16) pile fine. Boats are intentionally absent: they stack to 1, so a pile (which needs >= 2 of
	// an item in one stack) can never form — there's nothing to pile.
	/**
	 * S2 (STONE_PHASE.md): the 27 stone form-groups — one per fine-grained form across materials
	 * (brick stairs never group with plain stairs). Canonical = stone's version of each form.
	 */
	private static final List<VariantGroup> STONE_GROUPS = stoneGroups();

	private static List<VariantGroup> stoneGroups() {
		Map<String, String> canonicals = new java.util.LinkedHashMap<>();
		canonicals.put("raw", "stone");
		canonicals.put("raw_stairs", "stone_stairs");
		canonicals.put("raw_slab", "stone_slab");
		canonicals.put("raw_wall", "stone_wall");
		canonicals.put("cobbled", "cobblestone");
		canonicals.put("cobbled_stairs", "cobblestone_stairs");
		canonicals.put("cobbled_slab", "cobblestone_slab");
		canonicals.put("cobbled_wall", "cobblestone_wall");
		canonicals.put("polished", "smooth_stone");
		canonicals.put("polished_stairs", "smooth_stone_stairs");
		canonicals.put("polished_slab", "smooth_stone_slab");
		canonicals.put("polished_wall", "smooth_stone_wall");
		canonicals.put("bricks", "stone_bricks");
		canonicals.put("brick_stairs", "stone_brick_stairs");
		canonicals.put("brick_slab", "stone_brick_slab");
		canonicals.put("brick_wall", "stone_brick_wall");
		canonicals.put("cracked_bricks", "cracked_stone_bricks");
		canonicals.put("chiseled", "chiseled_stone_bricks");
		canonicals.put("pillar", "stone_pillar");
		canonicals.put("mossy_cobbled", "mossy_cobblestone");
		canonicals.put("mossy_cobbled_stairs", "mossy_cobblestone_stairs");
		canonicals.put("mossy_cobbled_slab", "mossy_cobblestone_slab");
		canonicals.put("mossy_cobbled_wall", "mossy_cobblestone_wall");
		canonicals.put("mossy_bricks", "mossy_stone_bricks");
		canonicals.put("mossy_brick_stairs", "mossy_stone_brick_stairs");
		canonicals.put("mossy_brick_slab", "mossy_stone_brick_slab");
		canonicals.put("mossy_brick_wall", "mossy_stone_brick_wall");
		canonicals.put("button", "stone_button");
		canonicals.put("pressure_plate", "stone_pressure_plate");
		canonicals.put("furnace", "furnace");
		canonicals.put("piston", "piston");
		canonicals.put("sticky_piston", "sticky_piston");
		List<VariantGroup> groups = new java.util.ArrayList<>();
		for (var entry : canonicals.entrySet()) {
			groups.add(new VariantGroup(MythStack.id("stone/" + entry.getKey()),
					custom("stone/" + entry.getKey()), stoneItem(entry.getValue())));
		}
		return List.copyOf(groups);
	}

	private static Item stoneItem(String name) {
		Identifier vanilla = Identifier.withDefaultNamespace(name);
		return BuiltInRegistries.ITEM.containsKey(vanilla)
				? BuiltInRegistries.ITEM.getValue(vanilla)
				: BuiltInRegistries.ITEM.getValue(MythStack.id(name));
	}

	private static final List<VariantGroup> WOOD_AND_ORE_GROUPS = List.of(
			LOGS, WOODS, STRIPPED_LOGS, STRIPPED_WOODS, PLANKS, STAIRS, SLABS, FENCES, FENCE_GATES, DOORS,
			TRAPDOORS, PRESSURE_PLATES, BUTTONS, SHELVES, SIGNS, HANGING_SIGNS, LEAVES, STICKS,
			LADDERS, CHESTS, BOOKSHELVES, CHISELED_BOOKSHELVES, BARRELS, CRAFTING_TABLES, SAPLINGS,
			FLETCHING_TABLES, CARTOGRAPHY_TABLES, SMITHING_TABLES, LOOMS, LECTERNS, COMPOSTERS, NOTE_BLOCKS,
			JUKEBOXES, BEEHIVES,
			IRON_ORES, COAL_ORES, COPPER_ORES, GOLD_ORES, REDSTONE_ORES, EMERALD_ORES, LAPIS_ORES, DIAMOND_ORES);

	private static final List<VariantGroup> ALL = java.util.stream.Stream
			.concat(WOOD_AND_ORE_GROUPS.stream(), STONE_GROUPS.stream()).toList();

	/** item -> group, snapshotted from the tags when they are loaded/synced (bindings reliable on both sides). */
	private static volatile Map<Item, VariantGroup> membership = Map.of();

	// The cross-group wood identity (spec §13 phase B): "spruce_planks" and "spruce_stick" both key as
	// "spruce", so the transmuter can substitute per SLOT per GROUP (plank slots get the wood's planks,
	// stick slots its stick). Matched longest-first so dark/pale oak never collide with oak; the
	// "stripped_" prefix is peeled before matching; a group's canonical member always keys "oak" (the
	// vanilla stick has no wood in its path). Members with no recognizable wood (modded, azalea leaves)
	// get a per-item key that only resolves within their own group — single-group transmuting keeps
	// working for them, cross-group propagation simply doesn't apply.
	private static final List<String> WOOD_KEYS = List.of("dark_oak", "pale_oak", "oak", "spruce", "birch",
			"jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped");
	private static volatile Map<Item, String> variantKeys = Map.of();
	private static volatile Map<VariantGroup, Map<String, Item>> membersByKey = Map.of();

	/** The group {@code item} belongs to, or {@code null} if none. */
	public static VariantGroup of(Item item) {
		VariantGroup cached = membership.get(item);
		if (cached != null) {
			return cached;
		}
		// Pre-snapshot safety net only (e.g. before the first TAGS_LOADED): a live tag check, which may be
		// unreliable client-side — the snapshot above is the authoritative path once tags have loaded.
		for (VariantGroup group : ALL) {
			if (group.contains(item)) {
				return group;
			}
		}
		return null;
	}

	/**
	 * Rebuild the {@link #membership} snapshot from {@code registries}, whose tags are freshly loaded/synced
	 * (so reliably bound). Everything is resolved from {@code registries} or from registry keys (both stable)
	 * — never from a per-item {@code holder.is(tag)} call. Wired to {@code CommonLifecycleEvents.TAGS_LOADED}.
	 */
	public static void rebuildMembership(RegistryAccess registries) {
		Registry<Item> items = registries.lookup(Registries.ITEM).orElseThrow();
		Map<Item, VariantGroup> map = new HashMap<>();
		Map<Item, String> keys = new HashMap<>();
		Map<VariantGroup, Map<String, Item>> byKey = new HashMap<>();
		for (VariantGroup group : ALL) {
			Map<String, Item> groupByKey = byKey.computeIfAbsent(group, unused -> new HashMap<>());
			for (Holder<Item> holder : items.getTagOrEmpty(group.members())) {
				Item item = holder.value();
				map.putIfAbsent(item, group);
				String key = keyFor(group, item);
				keys.putIfAbsent(item, key);
				groupByKey.putIfAbsent(key, item); // first-wins on collisions (e.g. bamboo mosaic forms)
			}
		}
		membership = Map.copyOf(map);
		variantKeys = Map.copyOf(keys);
		membersByKey = Map.copyOf(byKey);
		MythStack.LOGGER.info("[variant-group] membership snapshot rebuilt: {} items across {} groups",
				map.size(), ALL.size());
	}

	/** The cross-group wood identity of {@code item} — "spruce" for both spruce planks and spruce stick. */
	public static String variantKey(Item item) {
		String cached = variantKeys.get(item);
		if (cached != null) {
			return cached;
		}
		VariantGroup group = of(item);
		return group == null ? null : keyFor(group, item);
	}

	/** The member of {@code group} identified by {@code variantKey}, or {@code null} (that wood lacks the form). */
	/**
	 * True when {@code key} names a member of {@code group}'s FAMILY (wood vs stone) — the
	 * transmuter keeps other-family slots as-is instead of failing the substitution (a piston
	 * mixes planks with cobbled stone; "granite" can never fill a planks slot, and shouldn't try).
	 */
	public static boolean sameFamily(VariantGroup group, String key) {
		String groupFamily = group.id().getPath().split("/")[0];
		String keyFamily = stoneMaterialNames().contains(key) ? "stone" : "wood";
		return groupFamily.equals(keyFamily);
	}

	private static volatile java.util.Set<String> stoneMaterialNamesCache;

	private static java.util.Set<String> stoneMaterialNames() {
		java.util.Set<String> names = stoneMaterialNamesCache;
		if (names == null) {
			names = com.mythstack.registry.StoneKit.MATERIALS.stream()
					.map(com.mythstack.registry.StoneKit.Material::name)
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
			stoneMaterialNamesCache = names;
		}
		return names;
	}

	public static Item member(VariantGroup group, String variantKey) {
		Map<String, Item> byKey = membersByKey.get(group);
		return byKey == null ? null : byKey.get(variantKey);
	}

	private static volatile Map<Item, String> stoneMaterialKeys;

	private static String keyFor(VariantGroup group, Item item) {
		if (item == group.canonical()) {
			return "oak"; // D2: the canonical member IS the oak form (the plain stick has no wood in its path)
		}
		// Stone identity is an exact-id map from the kit's naming tables (S2) — no substring
		// matching, so "sandstone"/"end_stone"/"cobblestone" never collide with "stone".
		Map<Item, String> stoneKeys = stoneMaterialKeys;
		if (stoneKeys == null) {
			stoneKeys = com.mythstack.registry.StoneKit.materialItemKeys();
			stoneMaterialKeys = stoneKeys;
		}
		String stoneKey = stoneKeys.get(item);
		if (stoneKey != null) {
			return stoneKey;
		}
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (path.startsWith("stripped_")) {
			path = path.substring("stripped_".length());
		}
		for (String key : WOOD_KEYS) {
			if (path.equals(key) || path.startsWith(key + "_")) {
				return key;
			}
		}
		return "item:" + BuiltInRegistries.ITEM.getKey(item); // unknown wood: identity within its own group only
	}

	/** Dev-only sanity dump (build phase 2). Logged on server start in the dev environment. */
	public static void logSampleResolutions() {
		List<Item> samples = List.of(Items.OAK_LOG, Items.OAK_WOOD, Items.STRIPPED_OAK_LOG,
				Items.OAK_STAIRS, Items.OAK_SLAB, Items.OAK_DOOR, Items.OAK_SIGN, Items.OAK_SHELF,
				Items.OAK_LEAVES, Items.AZALEA_LEAVES, Items.CRIMSON_STAIRS, Items.BAMBOO_PLANKS, Items.STONE);
		for (Item item : samples) {
			VariantGroup group = of(item);
			MythStack.LOGGER.info("[variant-group] {} -> {}",
					BuiltInRegistries.ITEM.getKey(item),
					group == null
							? "(none)"
							: group.id() + " canonical=" + BuiltInRegistries.ITEM.getKey(group.canonical()));
		}
	}

	private static VariantGroup group(String form, TagKey<Item> members, Item canonical) {
		return new VariantGroup(MythStack.id("wood/" + form), members, canonical);
	}

	private static VariantGroup ores(String form, Item canonical) {
		return new VariantGroup(MythStack.id("ores/" + form), vanillaTag(form), canonical);
	}

	private static TagKey<Item> custom(String path) {
		return itemTag(MythStack.id(path));
	}

	private static TagKey<Item> vanillaTag(String path) {
		return itemTag(vanilla(path));
	}

	private static TagKey<Item> itemTag(Identifier id) {
		return TagKey.create(Registries.ITEM, id);
	}

	private static Identifier vanilla(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}
}
