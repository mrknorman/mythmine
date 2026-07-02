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
	private static final List<VariantGroup> ALL = List.of(
			LOGS, WOODS, STRIPPED_LOGS, STRIPPED_WOODS, PLANKS, STAIRS, SLABS, FENCES, FENCE_GATES, DOORS,
			TRAPDOORS, PRESSURE_PLATES, BUTTONS, SHELVES, SIGNS, HANGING_SIGNS, LEAVES, STICKS,
			LADDERS, CHESTS, BOOKSHELVES, CHISELED_BOOKSHELVES, BARRELS, CRAFTING_TABLES, SAPLINGS,
			IRON_ORES, COAL_ORES, COPPER_ORES, GOLD_ORES, REDSTONE_ORES, EMERALD_ORES, LAPIS_ORES, DIAMOND_ORES);

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
	private static volatile Map<Item, String> woodKeys = Map.of();
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
		woodKeys = Map.copyOf(keys);
		membersByKey = Map.copyOf(byKey);
		MythStack.LOGGER.info("[variant-group] membership snapshot rebuilt: {} items across {} groups",
				map.size(), ALL.size());
	}

	/** The cross-group wood identity of {@code item} — "spruce" for both spruce planks and spruce stick. */
	public static String woodKey(Item item) {
		String cached = woodKeys.get(item);
		if (cached != null) {
			return cached;
		}
		VariantGroup group = of(item);
		return group == null ? null : keyFor(group, item);
	}

	/** The member of {@code group} identified by {@code woodKey}, or {@code null} (that wood lacks the form). */
	public static Item member(VariantGroup group, String woodKey) {
		Map<String, Item> byKey = membersByKey.get(group);
		return byKey == null ? null : byKey.get(woodKey);
	}

	private static String keyFor(VariantGroup group, Item item) {
		if (item == group.canonical()) {
			return "oak"; // D2: the canonical member IS the oak form (the plain stick has no wood in its path)
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
