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

	// Our own membership tag: vanilla has no "bare logs only" tag (#*_logs include wood + stripped).
	public static final TagKey<Item> WOOD_LOGS = itemTag(MythStack.id("wood/logs"));

	private static final TagKey<Item> VANILLA_PLANKS = itemTag(vanilla("planks"));
	private static final TagKey<Item> NON_FLAMMABLE_WOOD = itemTag(vanilla("non_flammable_wood"));

	public static final VariantGroup LOGS =
			new VariantGroup(MythStack.id("wood/logs"), WOOD_LOGS, Items.OAK_LOG);
	public static final VariantGroup PLANKS =
			new VariantGroup(MythStack.id("wood/planks"), VANILLA_PLANKS, Items.OAK_PLANKS);

	private static final List<VariantGroup> ALL = List.of(LOGS, PLANKS);

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
		List<Item> samples = List.of(Items.OAK_LOG, Items.BIRCH_LOG, Items.PALE_OAK_LOG,
				Items.OAK_PLANKS, Items.CRIMSON_PLANKS, Items.BAMBOO_PLANKS, Items.STONE);
		for (Item item : samples) {
			VariantGroup group = of(item);
			MythStack.LOGGER.info("[variant-group] {} -> {}",
					BuiltInRegistries.ITEM.getKey(item),
					group == null
							? "(none)"
							: group.id() + " canonical=" + BuiltInRegistries.ITEM.getKey(group.canonical()));
		}
	}

	private static TagKey<Item> itemTag(Identifier id) {
		return TagKey.create(Registries.ITEM, id);
	}

	private static Identifier vanilla(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}
}
