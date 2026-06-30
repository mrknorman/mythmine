package com.mythstack.variant;

import com.mythstack.MythStack;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Minecraft adapter around {@link CanonicalPacking} — the only place that reads/writes the
 * {@link VariantPile} component and converts between {@code ItemStack}s and the pure algorithm.
 *
 * <p>Invariant maintained: a pile's component {@code contents} sum equals the host stack's count, and
 * the host item is the variant group's canonical member. Single-variant results collapse to a plain
 * stack when the surviving variant equals the host (see {@link #applyContents}); a single
 * <em>non-host</em> remainder (only reachable by passive draining) keeps its component and stays
 * canonical-hosted — a benign cosmetic edge case (it extracts as its real variant). See plan §6.4.
 */
public final class VariantPiles {
	/** Default per-stack cap (most wood forms). Per-group caps come from {@link VariantGroup#cap()} —
	 *  e.g. signs cap at 16 — and per-stack caps from {@link ItemStack#getMaxStackSize()}. */
	public static final int CAP = 64;

	private VariantPiles() {
	}

	public static boolean isPile(ItemStack stack) {
		return stack.has(ModComponents.VARIANT_PILE);
	}

	/**
	 * Ensure {@code stack}'s component agrees with its count ({@code sum==count}) and collapse if the
	 * remainder is a single host-equal variant. Mutates {@code stack}; returns the effective pile, or
	 * {@code null} if it is (now) a plain stack.
	 */
	public static VariantPile reconcile(ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return null;
		}
		int count = stack.getCount();
		List<Entry> contents = pile.contents();
		if (pile.total() > count) {
			// Vanilla consumed some (shrink) — peel the difference canonical-first and discard it.
			contents = peel(contents, pile.total() - count).remainder();
		}
		return applyContents(stack, contents);
	}

	/**
	 * Write {@code contents} onto {@code stack}: strip the component if empty or a single host-equal
	 * variant (it becomes a plain vanilla stack), otherwise store the pile. Returns the stored pile or
	 * {@code null} if collapsed. Does not change the host item or the count.
	 */
	private static VariantPile applyContents(ItemStack stack, List<Entry> contents) {
		if (contents.isEmpty() || stack.getCount() <= 0) {
			stack.remove(ModComponents.VARIANT_PILE);
			return null;
		}
		if (contents.size() == 1 && contents.get(0).item() == stack.getItem()) {
			stack.remove(ModComponents.VARIANT_PILE);
			return null;
		}
		VariantPile pile = new VariantPile(List.copyOf(contents), preserveSelected(stack, contents),
				preserveManual(stack));
		stack.set(ModComponents.VARIANT_PILE, pile);
		return pile;
	}

	/** Keep the old pile's {@code manual} flag through an in-place rebuild (reconcile / split remainder). */
	private static boolean preserveManual(ItemStack stack) {
		VariantPile old = stack.get(ModComponents.VARIANT_PILE);
		return old != null && old.manual();
	}

	/** Whether {@code stack} is a pile the player built deliberately (vs auto-formed / compression). */
	public static boolean isManual(ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		return pile != null && pile.manual();
	}

	/** Set the {@code manual} flag on a pile — no-op on a plain stack or if already set. */
	public static void markManual(ItemStack stack, boolean manual) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile != null && pile.manual() != manual) {
			stack.set(ModComponents.VARIANT_PILE, new VariantPile(pile.contents(), pile.selected(), manual));
		}
	}

	/** Keep the old pile's active wood if it survives into {@code contents}, otherwise none. */
	private static Optional<Item> preserveSelected(ItemStack stack, List<Entry> contents) {
		VariantPile old = stack.get(ModComponents.VARIANT_PILE);
		if (old == null || old.selected().isEmpty()) {
			return Optional.empty();
		}
		Item active = old.selected().get();
		for (Entry entry : contents) {
			if (entry.item() == active) {
				return old.selected();
			}
		}
		return Optional.empty();
	}

	/** Result of splitting an ordered content list: the peeled-off front and the leftover remainder. */
	private record Peel(List<Entry> peeled, List<Entry> remainder) {
	}

	/** Peel {@code n} items off the front (canonical-first) of {@code ordered}, preserving order. */
	private static Peel peel(List<Entry> ordered, int n) {
		List<Entry> peeled = new ArrayList<>();
		List<Entry> remainder = new ArrayList<>();
		int need = n;
		for (Entry entry : ordered) {
			if (need <= 0) {
				remainder.add(entry);
				continue;
			}
			int take = Math.min(need, entry.count());
			if (take > 0) {
				peeled.add(new Entry(entry.item(), take));
			}
			int left = entry.count() - take;
			if (left > 0) {
				remainder.add(new Entry(entry.item(), left));
			}
			need -= take;
		}
		return new Peel(peeled, remainder);
	}

	/**
	 * Split {@code amount} off a pile (delegate for the {@code ItemStack#split} mixin). Peels
	 * canonical-first: the returned stack carries the peeled variants (a plain stack if single), and
	 * {@code stack} keeps the remainder (host unchanged).
	 */
	public static ItemStack splitPile(ItemStack stack, int amount) {
		VariantPile pile = reconcile(stack);
		if (pile == null) {
			// Collapsed to a plain stack during reconcile — the mixin will now no-op and vanilla runs.
			return stack.split(amount);
		}
		int count = stack.getCount();
		int n = Math.min(Math.max(amount, 0), count);
		if (n <= 0) {
			return ItemStack.EMPTY;
		}
		Peel split = peel(pile.contents(), n);
		stack.setCount(count - n);
		applyContents(stack, split.remainder());

		VariantGroup group = VariantGroups.of(stack.getItem());
		ItemStack peeled = stackOf(group, split.peeled());
		// The peeled portion (e.g. the whole pile on a full pickup) inherits the active wood if it's still
		// in there — otherwise picking a pile up or moving it would silently reset the selection.
		pile.selected().ifPresent(active -> seed(peeled, active));
		markManual(peeled, pile.manual()); // a split of a curated pile stays curated
		return peeled;
	}

	/**
	 * Like {@link #splitPile} but peels {@code preferred} variants off the front first (then
	 * canonical order). Used when depositing onto a <em>pure</em> stack of {@code preferred}: spending
	 * the pile's matching wood first lets the target stay a plain stack until that wood is exhausted,
	 * instead of polluting it into a pile. The remainder kept on {@code stack} is re-canonicalised so
	 * the {@code sum==count} / canonical-order invariant holds.
	 */
	public static ItemStack splitPilePreferring(ItemStack stack, int amount, Item preferred) {
		VariantPile pile = reconcile(stack);
		if (pile == null) {
			return stack.split(amount); // plain stack — nothing to prioritise
		}
		int count = stack.getCount();
		int n = Math.min(Math.max(amount, 0), count);
		if (n <= 0) {
			return ItemStack.EMPTY;
		}
		List<Entry> ordered = new ArrayList<>();
		for (Entry entry : pile.contents()) {
			if (entry.item() == preferred) {
				ordered.add(entry);
			}
		}
		for (Entry entry : pile.contents()) {
			if (entry.item() != preferred) {
				ordered.add(entry);
			}
		}
		Peel split = peel(ordered, n);
		stack.setCount(count - n);
		VariantGroup group = VariantGroups.of(stack.getItem());
		applyContents(stack, canonicalOrder(split.remainder(), group));
		ItemStack peeled = stackOf(group, canonicalOrder(split.peeled(), group));
		pile.selected().ifPresent(active -> seed(peeled, active)); // peeled keeps the active wood if present
		markManual(peeled, pile.manual()); // and the curated/auto status
		return peeled;
	}

	/** Sort entries into canonical order (group canonical first, then by item id). */
	private static List<Entry> canonicalOrder(List<Entry> entries, VariantGroup group) {
		List<Entry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator
				.comparingInt((Entry e) -> group != null && e.item() == group.canonical() ? 0 : 1)
				.thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.item()).toString()));
		return sorted;
	}

	/** How much of {@code wood} is held in {@code stack}'s pile contents (0 if not a pile / none present). */
	public static int countOf(ItemStack stack, Item wood) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return 0;
		}
		int count = 0;
		for (Entry entry : pile.contents()) {
			if (entry.item() == wood) {
				count += entry.count();
			}
		}
		return count;
	}

	/**
	 * The pile's <em>active wood</em> — its {@link VariantPile#selected} if that wood is still present,
	 * else the first (canonical) wood. Mirrors vanilla {@code BundleContents.Mutable.removeOne}'s
	 * "selected, or index 0" rule. Returns {@code null} on a plain stack / empty pile.
	 */
	public static Item activeWood(ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null || pile.contents().isEmpty()) {
			return null;
		}
		if (pile.selected().isPresent()) {
			Item sel = pile.selected().get();
			for (Entry entry : pile.contents()) {
				if (entry.item() == sel) {
					return sel;
				}
			}
		}
		return pile.contents().get(0).item();
	}

	/**
	 * Peel up to {@code amount} of a single {@code wood} out of {@code stack}'s pile, mutating the pile
	 * in place (contents minus the removed amount, count adjusted; the canonical host is unchanged — a
	 * single-variant remainder is collapsed later by {@link #collapseToReal}). The selection is kept if
	 * any of {@code wood} remains, else cleared. Returns the removed stack ({@code wood} × taken), or
	 * {@link ItemStack#EMPTY}. Unlike {@link #splitPilePreferring} this never touches other woods.
	 */
	public static ItemStack removeWood(ItemStack stack, Item wood, int amount) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null || amount <= 0) {
			return ItemStack.EMPTY;
		}
		List<Entry> remaining = new ArrayList<>(pile.contents().size());
		int taken = 0;
		boolean keptWood = false;
		for (Entry entry : pile.contents()) {
			if (entry.item() == wood) {
				taken = Math.min(amount, entry.count());
				int keep = entry.count() - taken;
				if (keep > 0) {
					remaining.add(new Entry(wood, keep)); // preserve position (canonical order)
					keptWood = true;
				}
			} else {
				remaining.add(entry);
			}
		}
		if (taken <= 0) {
			return ItemStack.EMPTY;
		}
		setContents(stack, remaining, keptWood ? Optional.of(wood) : Optional.empty());
		return new ItemStack(wood, taken);
	}

	/**
	 * Remove the whole of the pile's {@link #activeWood} onto a new stack, mutating the pile in place —
	 * the direct analogue of vanilla {@code BundleContents.Mutable.removeOne()} (which removes the entire
	 * selected stack and clears the selection). Returns the removed wood stack, or {@link ItemStack#EMPTY}.
	 */
	public static ItemStack removeSelectedStack(ItemStack stack) {
		Item active = activeWood(stack);
		if (active == null) {
			return ItemStack.EMPTY;
		}
		return removeWood(stack, active, countOf(stack, active));
	}

	/** Write {@code entries} (+ {@code selected}) onto {@code stack} in place — host unchanged; empties if none. */
	private static void setContents(ItemStack stack, List<Entry> entries, Optional<Item> selected) {
		int total = 0;
		for (Entry entry : entries) {
			total += entry.count();
		}
		if (total <= 0) {
			stack.setCount(0); // becomes empty — never reached for a valid (>=2 wood) pile losing one wood
			return;
		}
		stack.set(ModComponents.VARIANT_PILE, new VariantPile(List.copyOf(entries), selected, preserveManual(stack)));
		stack.setCount(total);
	}

	/**
	 * Record {@code seed} as the pile's active/selected variant ({@link VariantPile#selected}, the index
	 * of that wood in the contents). No-op on a plain stack or if {@code seed} isn't present. Display /
	 * placement effects of the selection are a later step; this just stores the intent.
	 */
	public static void seed(ItemStack stack, Item seed) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return;
		}
		for (Entry entry : pile.contents()) {
			if (entry.item() == seed) {
				stack.set(ModComponents.VARIANT_PILE, new VariantPile(pile.contents(), Optional.of(seed), pile.manual()));
				return;
			}
		}
	}

	/**
	 * Build a single stack from ordered {@code entries}: a plain vanilla stack if one variant, else a
	 * canonical-hosted pile. {@code group} supplies the canonical host (falls back to the first entry).
	 */
	public static ItemStack stackOf(VariantGroup group, List<Entry> entries) {
		int total = 0;
		for (Entry entry : entries) {
			total += entry.count();
		}
		if (total <= 0 || entries.isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (entries.size() == 1) {
			return new ItemStack(entries.get(0).item(), total);
		}
		Item host = canonicalHostFor(group, entries);
		ItemStack stack = new ItemStack(host, total);
		stack.set(ModComponents.VARIANT_PILE, new VariantPile(List.copyOf(entries), Optional.empty(), false));
		return stack;
	}

	/**
	 * The canonical host item for a multi-variant pile. Uses the supplied {@code group}, or resolves the
	 * group from the entries themselves if none was given, so a pile is <em>always</em> hosted on its
	 * group's canonical (e.g. {@code oak_log}) — never on whatever wood happens to be first. The bare
	 * top-wood fallback only remains as a logged last resort and should be unreachable now that group
	 * membership is snapshotted at tag-load (see {@link VariantGroups#rebuildMembership}).
	 */
	private static Item canonicalHostFor(VariantGroup group, List<Entry> entries) {
		VariantGroup resolved = group;
		if (resolved == null) {
			for (Entry entry : entries) {
				resolved = VariantGroups.of(entry.item());
				if (resolved != null) {
					break;
				}
			}
		}
		if (resolved != null) {
			return resolved.canonical();
		}
		MythStack.LOGGER.warn("[pile-host] no variant group resolved for a {}-entry pile; hosting on {} as a "
				+ "last resort (group snapshot may not be loaded yet)",
				entries.size(), BuiltInRegistries.ITEM.getKey(entries.get(0).item()));
		return entries.get(0).item();
	}

	/**
	 * Pool {@code stacks} of one {@code group} into a canonical-ordered {@code variant → count} map,
	 * dissolving any piles into their contents. Stacks of other groups/items are ignored.
	 */
	public static LinkedHashMap<Item, Integer> pool(VariantGroup group, List<ItemStack> stacks) {
		Map<Item, Integer> raw = new HashMap<>();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			VariantPile pile = isPile(stack) ? reconcile(stack) : null;
			if (pile != null) {
				for (Entry entry : pile.contents()) {
					raw.merge(entry.item(), entry.count(), Integer::sum);
				}
			} else if (group != null && group.contains(stack.getItem())) {
				raw.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		List<Item> keys = new ArrayList<>(raw.keySet());
		keys.sort(Comparator
				.comparingInt((Item item) -> group != null && item == group.canonical() ? 0 : 1)
				.thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
		LinkedHashMap<Item, Integer> ordered = new LinkedHashMap<>();
		for (Item item : keys) {
			ordered.put(item, raw.get(item));
		}
		return ordered;
	}

	/** Normalize a canonical-ordered pool into the minimal set of stacks (the drag-merge/combine core). */
	public static List<ItemStack> makeStacks(VariantGroup group, Map<Item, Integer> orderedPool) {
		List<CanonicalPacking.PackedStack<Item>> packed =
				CanonicalPacking.normalize(orderedPool, group == null ? CAP : group.cap());
		List<ItemStack> out = new ArrayList<>();
		for (CanonicalPacking.PackedStack<Item> stack : packed) {
			List<Entry> entries = new ArrayList<>();
			for (CanonicalPacking.Slice<Item> slice : stack.entries()) {
				entries.add(new Entry(slice.variant(), slice.count()));
			}
			out.add(stackOf(group, entries));
		}
		return out;
	}

	/** Display name for a pile: the plural form word, e.g. "Logs" or "Planks". The plural is what makes
	 * it read as a mixed carrier and keeps it distinct from a single "Spruce Log". */
	public static Component displayName(ItemStack stack) {
		VariantGroup group = VariantGroups.of(stack.getItem());
		String form = "Wood";
		if (group != null) {
			String last = group.id().getPath();
			last = last.substring(last.lastIndexOf('/') + 1); // "logs", "planks" — keep the plural
			if (!last.isEmpty()) {
				form = Character.toUpperCase(last.charAt(0)) + last.substring(1);
			}
		}
		return Component.literal(form);
	}

	/**
	 * If {@code stack} is a single-variant pile (only reachable as a non-host remainder), rebuild it as
	 * the plain vanilla stack of that variant; otherwise return it unchanged (after reconciling). Called
	 * at the click boundary so the icon/identity collapses the moment the last other-variant leaves,
	 * rather than lingering as a canonical-hosted disguise (point 1).
	 */
	public static ItemStack collapseToReal(ItemStack stack) {
		if (!isPile(stack)) {
			return stack;
		}
		VariantPile pile = reconcile(stack);
		if (pile != null && pile.contents().size() == 1) {
			return new ItemStack(pile.contents().get(0).item(), stack.getCount());
		}
		return stack;
	}
}
