package com.mythstack.variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Canonical packing — the pure normalize algorithm (see docs/IMPLEMENTATION_PLAN.md §6.3).
 *
 * <p>Pools a multiset of {@code variant → count} and repacks it into the minimal, deterministic
 * set of stacks: full {@code cap}-sized <em>pure</em> stacks are peeled per variant, then the
 * leftover remainder is packed canonical-first into stacks of at most {@code cap}. A single-variant
 * result {@linkplain PackedStack#isPure() is pure} (it would collapse to a plain vanilla stack);
 * a multi-variant result is a carrier/pile.
 *
 * <p>Deliberately generic over the variant type {@code V} and free of any Minecraft type, so the
 * algorithm is unit-tested with plain values. The Minecraft adapter (ItemStack ↔ PackedStack)
 * lives elsewhere.
 */
public final class CanonicalPacking {
	private CanonicalPacking() {
	}

	/** One entry of a packed stack: a variant and how many of it. */
	public record Slice<V>(V variant, int count) {
	}

	/**
	 * A packed stack. {@code entries} are in canonical order and sum to {@link #total()}.
	 * {@link #isPure()} ⇒ a single variant (would be a plain vanilla stack); otherwise a carrier.
	 */
	public record PackedStack<V>(List<Slice<V>> entries) {
		public int total() {
			int sum = 0;
			for (Slice<V> slice : entries) {
				sum += slice.count();
			}
			return sum;
		}

		public boolean isPure() {
			return entries.size() == 1;
		}
	}

	/**
	 * Repack {@code pool} (iterated in canonical order) into the minimal set of stacks of at most
	 * {@code cap}. Entries with a non-positive count are ignored; an empty/zero pool yields an empty
	 * list (the "empty collapse" — the pile is destroyed).
	 *
	 * @param pool variant → count, iterated in canonical order (use an ordered map)
	 * @param cap  the per-stack hard cap (64 in vanilla)
	 */
	public static <V> List<PackedStack<V>> normalize(Map<V, Integer> pool, int cap) {
		if (cap <= 0) {
			throw new IllegalArgumentException("cap must be positive: " + cap);
		}
		List<PackedStack<V>> out = new ArrayList<>();

		// Step A: peel every full `cap` as a pure stack; keep the per-variant remainder (< cap).
		List<Slice<V>> remainder = new ArrayList<>();
		for (Map.Entry<V, Integer> entry : pool.entrySet()) {
			int count = entry.getValue() == null ? 0 : entry.getValue();
			if (count <= 0) {
				continue;
			}
			for (int full = count / cap; full > 0; full--) {
				out.add(pure(entry.getKey(), cap));
			}
			int rem = count % cap;
			if (rem > 0) {
				remainder.add(new Slice<>(entry.getKey(), rem));
			}
		}

		// Step B: pack the remainder canonical-first into stacks of at most `cap`. A finished stack
		// with a single variant is pure; with several, it's a carrier.
		List<Slice<V>> current = new ArrayList<>();
		int currentTotal = 0;
		for (Slice<V> slice : remainder) {
			int count = slice.count();
			while (count > 0) {
				int take = Math.min(cap - currentTotal, count);
				current.add(new Slice<>(slice.variant(), take));
				currentTotal += take;
				count -= take;
				if (currentTotal == cap) {
					out.add(new PackedStack<>(List.copyOf(current)));
					current = new ArrayList<>();
					currentTotal = 0;
				}
			}
		}
		if (currentTotal > 0) {
			out.add(new PackedStack<>(List.copyOf(current)));
		}
		return out;
	}

	private static <V> PackedStack<V> pure(V variant, int count) {
		return new PackedStack<>(List.of(new Slice<>(variant, count)));
	}
}
