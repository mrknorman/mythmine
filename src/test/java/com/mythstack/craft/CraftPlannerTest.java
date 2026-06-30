package com.mythstack.craft;

import com.mythstack.craft.CraftPlanner.EntropyPlan;
import com.mythstack.craft.CraftPlanner.RatioPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the crafting plan engine (plan §7 — phase 1). Pure logic, variants are Strings.
 */
class CraftPlannerTest {

	private static LinkedHashMap<String, Integer> pool(Object... pairs) {
		LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	// --- Ratio (wood-typed output) ---

	@Test
	void oneIngredient_isAllPure_andPreservesRatioExactly() {
		// logs -> planks: 1 log per craft, so every craft is pure; output mirrors the input exactly.
		RatioPlan<String> plan = CraftPlanner.planRatio(pool("oak", 30, "spruce", 30), 1);
		assertEquals(Map.of("oak", 30, "spruce", 30), plan.craftsByWood());
		assertEquals(Map.of(), plan.leftover());
	}

	@Test
	void multiIngredient_pureThenMixedRemainder_goesToMajority() {
		// 6 per craft (stairs-like). oak 32 -> 5 pure (2 left); spruce 28 -> 4 pure (4 left).
		// Remainder {oak2, spruce4} = 6 -> one mixed craft, majority spruce.
		RatioPlan<String> plan = CraftPlanner.planRatio(pool("oak", 32, "spruce", 28), 6);
		assertEquals(Map.of("oak", 5, "spruce", 5), plan.craftsByWood());
		assertEquals(Map.of(), plan.leftover());
	}

	@Test
	void mixedPass_anchorsOnPrimary_andLeavesTheRest() {
		// Nothing reaches 6 alone. Mixed craft eats the primary (oak 5) first, then 1 spruce -> majority oak.
		RatioPlan<String> plan = CraftPlanner.planRatio(pool("oak", 5, "spruce", 2, "birch", 3), 6);
		assertEquals(Map.of("oak", 1), plan.craftsByWood());
		assertEquals(Map.of("spruce", 1, "birch", 3), plan.leftover());
	}

	@Test
	void majorityTieBreaksToEarliestInPoolOrder() {
		// {birch2, oak2} both consumed equally -> tie -> earliest in pool order (birch was placed first).
		RatioPlan<String> plan = CraftPlanner.planRatio(pool("birch", 2, "oak", 2), 4);
		assertEquals(Map.of("birch", 1), plan.craftsByWood());
		assertEquals(Map.of(), plan.leftover());
	}

	// --- Entropy (non-wood output) ---

	@Test
	void entropy_consumesSmallestStacksFirst() {
		// 4 per craft (crafting-table-like). Smallest-first clears birch then oak, leaving the big spruce.
		EntropyPlan<String> plan = CraftPlanner.planEntropy(pool("oak", 3, "spruce", 8, "birch", 2), 4);
		assertEquals(3, plan.crafts());
		assertEquals(Map.of("spruce", 1), plan.leftover());
	}

	@Test
	void entropy_exactConsumptionLeavesNothing() {
		EntropyPlan<String> plan = CraftPlanner.planEntropy(pool("oak", 4, "spruce", 4), 4);
		assertEquals(2, plan.crafts());
		assertEquals(Map.of(), plan.leftover());
	}

	@Test
	void belowOneCraft_producesNothing() {
		RatioPlan<String> ratio = CraftPlanner.planRatio(pool("oak", 2, "spruce", 1), 6);
		assertEquals(Map.of(), ratio.craftsByWood());
		assertEquals(Map.of("oak", 2, "spruce", 1), ratio.leftover());
		EntropyPlan<String> entropy = CraftPlanner.planEntropy(pool("oak", 2, "spruce", 1), 6);
		assertEquals(0, entropy.crafts());
		assertEquals(Map.of("oak", 2, "spruce", 1), entropy.leftover());
	}
}
