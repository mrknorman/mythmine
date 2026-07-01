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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declares the wood {@link VariantGroup}s and resolves an item to its group.
 *
 * <p>MVP scope (build phase 2): just {@code logs} and {@code planks}. Planks reuse the vanilla
 * {@code #minecraft:planks} tag directly; logs use our own tag because vanilla conflates
 * log / wood / stripped under {@code #*_logs}. Deferred materials (nether + bamboo, decision
 * D4) are filtered out at lookup, reusing vanilla's {@code #non_flammable_wood} for the
 * nether half so we don't hand-list it.
 *
 * <p>Resolution goes through a {@link #membership} snapshot rebuilt whenever tags load/sync
 * ({@code CommonLifecycleEvents.TAGS_LOADED}). A per-call {@code holder.is(tag)} check is unreliable
 * during client-side container prediction (the tag bindings aren't always present on the client),
 * which previously made {@link #of} return null and pile hosts collapse onto the top wood.
 */
public final class VariantGroups {
	private VariantGroups() {
	}

	private static final TagKey<Item> NON_FLAMMABLE_WOOD = itemTag(vanilla("non_flammable_wood"));

	// One group per wood FORM, keyed by wood type, canonical = the oak member (spec §D2). Membership comes
	// from a tag: a vanilla #wooden_*/family tag where one cleanly exists (so modded woods tag in for free),
	// or our own #mythstack:wood/* tag for the raw log forms (vanilla #*_logs conflate log/wood/stripped).
	// Deferred materials (nether via #non_flammable_wood, bamboo by name — decision D4) are filtered at
	// snapshot time, so a family tag that happens to include them is fine.
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
	// Typed sticks (spec §13): vanilla stick is the oak/canonical member; the others are ours.
	public static final VariantGroup STICKS = group("sticks", custom("wood/sticks"), Items.STICK);

	// The pile cap is per-group ({@link VariantGroup#cap} = the canonical's max stack size), so signs
	// (cap 16) pile fine. Boats are intentionally absent: they stack to 1, so a pile (which needs >= 2 of
	// an item in one stack) can never form — there's nothing to pile.
	private static final List<VariantGroup> ALL = List.of(
			LOGS, WOODS, STRIPPED_LOGS, STRIPPED_WOODS, PLANKS, STAIRS, SLABS, FENCES, FENCE_GATES, DOORS,
			TRAPDOORS, PRESSURE_PLATES, BUTTONS, SHELVES, SIGNS, HANGING_SIGNS, LEAVES, STICKS);

	/** item -> group, snapshotted from the tags when they are loaded/synced (bindings reliable on both sides). */
	private static volatile Map<Item, VariantGroup> membership = Map.of();

	/** The group {@code item} belongs to, or {@code null} if none (or a deferred material). */
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
		Set<Item> nonFlammable = new HashSet<>();
		for (Holder<Item> holder : items.getTagOrEmpty(NON_FLAMMABLE_WOOD)) {
			nonFlammable.add(holder.value());
		}
		Map<Item, VariantGroup> map = new HashMap<>();
		for (VariantGroup group : ALL) {
			for (Holder<Item> holder : items.getTagOrEmpty(group.members())) {
				Item item = holder.value();
				if (!nonFlammable.contains(item) && !isBamboo(item)) {
					map.putIfAbsent(item, group);
				}
			}
		}
		membership = Map.copyOf(map);
		MythStack.LOGGER.info("[variant-group] membership snapshot rebuilt: {} items across {} groups",
				map.size(), ALL.size());
	}

	/** Materials deferred out of the MVP (D4): nether wood (crimson/warped) and bamboo. */
	public static boolean isDeferredMaterial(Item item) {
		if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(NON_FLAMMABLE_WOOD)) {
			return true; // crimson / warped
		}
		return isBamboo(item);
	}

	private static boolean isBamboo(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return "minecraft".equals(id.getNamespace()) && id.getPath().startsWith("bamboo_");
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
