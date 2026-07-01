package com.mythstack.craft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The pure crafting plan (plan §7 crafting redesign — phase 1). Decides, for a recipe that eats
 * {@code perCraft} wood units per craft, how to consume a pool of mixed wood and what each craft produces.
 * No Minecraft runtime — variants are an abstract {@code V} so it unit-tests as plain Strings.
 *
 * <p>The {@code pool} is a {@link LinkedHashMap} in <em>priority order</em>: the first key is "pile&nbsp;1's
 * primary wood", used only to break ties. Two modes:
 *
 * <ul>
 *   <li>{@link #planRatio} — wood-typed output (logs→planks, planks→stairs…). <b>Pure passes</b> first:
 *       each wood makes {@code floor(have/perCraft)} crafts <em>of itself</em>, so the bulk output mirrors
 *       the input ratio. Then a <b>mixed pass</b> folds the sub-craft remainders together — each mixed
 *       craft consumes the <b>smallest remaining stacks first</b> (consolidating leftovers into fewer
 *       stacks) and is attributed to the <b>majority</b> wood it consumed (ties → earliest in pool order).</li>
 *   <li>{@link #planEntropy} — non-wood output (planks→crafting table / sticks). Same smallest-first
 *       consumption to clear clutter, {@code perCraft} per craft, until less than a craft's worth remains;
 *       just no output wood to attribute.</li>
 * </ul>
 */
public final class CraftPlanner {
	private CraftPlanner() {
	}

	/** Crafts attributed to each output wood, and the wood left over (both in a stable order). */
	public record RatioPlan<V>(LinkedHashMap<V, Integer> craftsByWood, LinkedHashMap<V, Integer> leftover) {
	}

	/** Number of (wood-agnostic) crafts produced, and the wood left over. */
	public record EntropyPlan<V>(int crafts, LinkedHashMap<V, Integer> leftover) {
	}

	public static <V> RatioPlan<V> planRatio(LinkedHashMap<V, Integer> pool, int perCraft) {
		return planRatio(pool, perCraft, pool.keySet());
	}

	/**
	 * As {@link #planRatio(LinkedHashMap, int)}, but only {@code productive} woods can be an OUTPUT —
	 * some recipes have no product for a wood (there is no crimson boat). Unproductive wood never
	 * pure-crafts and never receives attribution; mixed crafts still consume it, attributed to the
	 * most-consumed productive wood (ties → pool order), falling back to the pool's leading productive
	 * wood if a mixed bite happened to eat none. With no productive wood left, crafting stops — the
	 * rest is leftover (a pure-crimson boat grid crafts nothing).
	 */
	public static <V> RatioPlan<V> planRatio(LinkedHashMap<V, Integer> pool, int perCraft, Set<V> productive) {
		LinkedHashMap<V, Integer> work = new LinkedHashMap<>(pool);
		LinkedHashMap<V, Integer> crafts = new LinkedHashMap<>();

		// Pure passes: each productive wood crafts as many same-wood items as it can.
		for (V wood : pool.keySet()) {
			if (!productive.contains(wood)) {
				continue;
			}
			int have = work.getOrDefault(wood, 0);
			int n = have / perCraft;
			if (n > 0) {
				crafts.merge(wood, n, Integer::sum);
				work.put(wood, have - n * perCraft);
			}
		}

		// Mixed pass: fold the sub-craft remainders into majority-wood crafts, smallest-first to consolidate.
		while (total(work) >= perCraft && hasProductive(work, productive)) {
			Map<V, Integer> consumed = consumeSmallestFirst(work, perCraft);
			crafts.merge(majority(consumed, pool, productive), 1, Integer::sum);
		}
		return new RatioPlan<>(crafts, prune(work));
	}

	public static <V> EntropyPlan<V> planEntropy(LinkedHashMap<V, Integer> pool, int perCraft) {
		LinkedHashMap<V, Integer> work = new LinkedHashMap<>(pool);
		int crafts = 0;
		while (total(work) >= perCraft) {
			consumeSmallestFirst(work, perCraft);
			crafts++;
		}
		return new EntropyPlan<>(crafts, prune(work));
	}

	/** Consume {@code need} units from the smallest remaining stacks first; returns what was consumed. */
	private static <V> LinkedHashMap<V, Integer> consumeSmallestFirst(LinkedHashMap<V, Integer> work, int need) {
		LinkedHashMap<V, Integer> consumed = new LinkedHashMap<>();
		while (need > 0) {
			V wood = smallestNonZero(work);
			if (wood == null) {
				break;
			}
			int take = Math.min(need, work.get(wood));
			consumed.merge(wood, take, Integer::sum);
			work.put(wood, work.get(wood) - take);
			need -= take;
		}
		return consumed;
	}

	/** The non-empty wood with the fewest units; ties → earliest in iteration order. */
	private static <V> V smallestNonZero(LinkedHashMap<V, Integer> work) {
		V best = null;
		int bestCount = Integer.MAX_VALUE;
		for (Map.Entry<V, Integer> entry : work.entrySet()) {
			int count = entry.getValue();
			if (count > 0 && count < bestCount) {
				bestCount = count;
				best = entry.getKey();
			}
		}
		return best;
	}

	/**
	 * The most-consumed PRODUCTIVE wood; ties → earliest in {@code order}. If the bite consumed no
	 * productive wood at all, the pool's first productive wood takes the attribution (the caller's
	 * loop guard ensures one exists).
	 */
	private static <V> V majority(Map<V, Integer> consumed, LinkedHashMap<V, Integer> order, Set<V> productive) {
		V best = null;
		int bestCount = -1;
		for (V wood : order.keySet()) {
			if (!productive.contains(wood)) {
				continue;
			}
			int count = consumed.getOrDefault(wood, 0);
			if (count > bestCount) {
				bestCount = count;
				best = wood;
			}
		}
		return best;
	}

	/** True if any productive wood still has units left in {@code work}. */
	private static <V> boolean hasProductive(Map<V, Integer> work, Set<V> productive) {
		for (Map.Entry<V, Integer> entry : work.entrySet()) {
			if (entry.getValue() > 0 && productive.contains(entry.getKey())) {
				return true;
			}
		}
		return false;
	}

	private static <V> int total(Map<V, Integer> map) {
		int sum = 0;
		for (int value : map.values()) {
			sum += value;
		}
		return sum;
	}

	private static <V> LinkedHashMap<V, Integer> prune(LinkedHashMap<V, Integer> work) {
		LinkedHashMap<V, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<V, Integer> entry : work.entrySet()) {
			if (entry.getValue() > 0) {
				out.put(entry.getKey(), entry.getValue());
			}
		}
		return out;
	}
}
