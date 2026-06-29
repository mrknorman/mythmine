package com.mythstack.variant;

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
	/** Vanilla per-stack hard cap. */
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
		VariantPile pile = new VariantPile(List.copyOf(contents), -1);
		stack.set(ModComponents.VARIANT_PILE, pile);
		return pile;
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
		return stackOf(group, split.peeled());
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
		Item host = group != null ? group.canonical() : entries.get(0).item();
		ItemStack stack = new ItemStack(host, total);
		stack.set(ModComponents.VARIANT_PILE, new VariantPile(List.copyOf(entries), -1));
		return stack;
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
		List<CanonicalPacking.PackedStack<Item>> packed = CanonicalPacking.normalize(orderedPool, CAP);
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

	/** Display name for a pile: the singular form word + " Pile", e.g. "Log Pile" or "Plank Pile". */
	public static Component displayName(ItemStack stack) {
		VariantGroup group = VariantGroups.of(stack.getItem());
		String form = "Wood";
		if (group != null) {
			String last = group.id().getPath();
			last = last.substring(last.lastIndexOf('/') + 1);
			if (last.endsWith("s")) {
				last = last.substring(0, last.length() - 1); // logs -> log (naive singularize; fine for MVP forms)
			}
			if (!last.isEmpty()) {
				form = Character.toUpperCase(last.charAt(0)) + last.substring(1);
			}
		}
		return Component.literal(form + " Pile");
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
