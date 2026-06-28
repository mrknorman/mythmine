package com.mythstack.variant;

import com.mythstack.variant.CanonicalPacking.PackedStack;
import com.mythstack.variant.CanonicalPacking.Slice;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the canonical packing algorithm — the worked examples from plan §6.3 plus invariants.
 * Pure logic, no Minecraft runtime: variants are plain Strings.
 */
class CanonicalPackingTest {
	private static final int CAP = 64;

	private static LinkedHashMap<String, Integer> pool(Object... pairs) {
		LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	private static List<PackedStack<String>> norm(Map<String, Integer> pool) {
		return CanonicalPacking.normalize(pool, CAP);
	}

	@Test
	void singleVariantUnderCap_isOnePureStack() {
		List<PackedStack<String>> out = norm(pool("oak", 50));
		assertEquals(1, out.size());
		assertTrue(out.get(0).isPure());
		assertEquals(50, out.get(0).total());
		assertEquals("oak", out.get(0).entries().get(0).variant());
	}

	@Test
	void emptyOrZeroPool_yieldsNothing() {
		assertTrue(norm(pool()).isEmpty());
		assertTrue(norm(pool("oak", 0)).isEmpty());
	}

	@Test
	void twoVariantsUnderCap_oneCarrier() {
		// 30 oak + 30 birch -> one 60 carrier
		List<PackedStack<String>> out = norm(pool("oak", 30, "birch", 30));
		assertEquals(1, out.size());
		PackedStack<String> carrier = out.get(0);
		assertFalse(carrier.isPure());
		assertEquals(60, carrier.total());
		assertEquals(List.of(new Slice<>("oak", 30), new Slice<>("birch", 30)), carrier.entries());
	}

	@Test
	void exactlyFullEach_twoPureStacksNoCarrier() {
		// 64 oak + 64 birch -> two pure stacks, no carrier (slot-neutral tidiness)
		List<PackedStack<String>> out = norm(pool("oak", 64, "birch", 64));
		assertEquals(2, out.size());
		assertTrue(out.get(0).isPure());
		assertTrue(out.get(1).isPure());
		assertEquals("oak", out.get(0).entries().get(0).variant());
		assertEquals("birch", out.get(1).entries().get(0).variant());
	}

	@Test
	void overCapOneVariant_pureStackPlusRemainderCarrier() {
		// 70 oak + 50 birch -> pure oak64 + carrier(6 oak, 50 birch)=56
		List<PackedStack<String>> out = norm(pool("oak", 70, "birch", 50));
		assertEquals(2, out.size());
		assertTrue(out.get(0).isPure());
		assertEquals(64, out.get(0).total());
		assertEquals("oak", out.get(0).entries().get(0).variant());
		PackedStack<String> carrier = out.get(1);
		assertFalse(carrier.isPure());
		assertEquals(56, carrier.total());
		assertEquals(List.of(new Slice<>("oak", 6), new Slice<>("birch", 50)), carrier.entries());
	}

	@Test
	void summedRemainderOverCap_packsIntoMinimalStacks() {
		// 50 oak + 50 birch -> carrier(50 oak, 14 birch)=64 + pure birch36 (the §6.3 generalization)
		List<PackedStack<String>> out = norm(pool("oak", 50, "birch", 50));
		assertEquals(2, out.size());
		PackedStack<String> carrier = out.get(0);
		assertEquals(64, carrier.total());
		assertEquals(List.of(new Slice<>("oak", 50), new Slice<>("birch", 14)), carrier.entries());
		PackedStack<String> pure = out.get(1);
		assertTrue(pure.isPure());
		assertEquals(36, pure.total());
		assertEquals("birch", pure.entries().get(0).variant());
	}

	@Test
	void dissolveToSingleVariant_collapsesToPureStacks() {
		// 200 oak -> 64 + 64 + 64 + 8, all pure oak
		List<PackedStack<String>> out = norm(pool("oak", 200));
		assertEquals(4, out.size());
		assertTrue(out.stream().allMatch(PackedStack::isPure));
		assertTrue(out.stream().allMatch(s -> "oak".equals(s.entries().get(0).variant())));
		assertEquals(200, out.stream().mapToInt(PackedStack::total).sum());
	}

	@Test
	void totalConservedAndEveryStackWithinCap() {
		LinkedHashMap<String, Integer> p = pool("oak", 100, "birch", 33, "spruce", 64, "jungle", 1);
		int inputTotal = p.values().stream().mapToInt(Integer::intValue).sum();
		List<PackedStack<String>> out = norm(p);
		assertEquals(inputTotal, out.stream().mapToInt(PackedStack::total).sum());
		assertTrue(out.stream().allMatch(s -> s.total() > 0 && s.total() <= CAP));
	}
}
