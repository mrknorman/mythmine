package com.mythstack.dev;

import com.mythstack.MythStack;
import com.mythstack.craft.CraftTransmute;
import com.mythstack.interaction.Pickup;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPile.Entry;
import com.mythstack.variant.VariantPiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dev-only headless self-test for the {@link VariantPiles} layer (build phase 4). Runs on server
 * start in the dev environment and logs PASS/FAIL per check — verifiable via {@code runServer}
 * without a client. Not registered in production.
 */
public final class SelfTest {
	private SelfTest() {
	}

	public static void run(ServerLevel level) {
		int[] failures = {0};
		VariantGroup logs = VariantGroups.LOGS;

		// 1. Merge 30 oak + 30 birch -> one 60 carrier hosted on oak.
		List<ItemStack> merged = VariantPiles.makeStacks(logs,
				VariantPiles.pool(logs, List.of(new ItemStack(Items.OAK_LOG, 30), new ItemStack(Items.BIRCH_LOG, 30))));
		check("30+30 -> 1 stack", merged.size() == 1, failures);
		ItemStack pile = merged.isEmpty() ? ItemStack.EMPTY : merged.get(0);
		check("host is oak_log", pile.getItem() == Items.OAK_LOG, failures);
		check("count 60", pile.getCount() == 60, failures);
		check("contents {oak30,birch30}", contentsEqual(pile, Items.OAK_LOG, 30, Items.BIRCH_LOG, 30), failures);

		// 2. split(10) -> plain oak x10; remainder oak60 -> oak50 {oak20,birch30}.
		ItemStack copy = pile.copy();
		ItemStack off = copy.split(10);
		check("split-off plain oak x10", off.getItem() == Items.OAK_LOG && off.getCount() == 10 && !VariantPiles.isPile(off), failures);
		check("remainder count 50", copy.getCount() == 50, failures);
		check("remainder {oak20,birch30}", contentsEqual(copy, Items.OAK_LOG, 20, Items.BIRCH_LOG, 30), failures);

		// 3. External shrink then reconcile -> sum tracks count, canonical-first.
		ItemStack shrunk = pile.copy();
		shrunk.setCount(50);
		VariantPiles.reconcile(shrunk);
		check("reconcile to 50 -> {oak20,birch30}", contentsEqual(shrunk, Items.OAK_LOG, 20, Items.BIRCH_LOG, 30), failures);

		// 4. Single variant -> plain stack, no component (collapse).
		List<ItemStack> single = VariantPiles.makeStacks(logs,
				VariantPiles.pool(logs, List.of(new ItemStack(Items.OAK_LOG, 5))));
		check("single variant -> plain oak x5, no component",
				single.size() == 1 && single.get(0).getItem() == Items.OAK_LOG
						&& single.get(0).getCount() == 5 && !VariantPiles.isPile(single.get(0)), failures);

		// 5. 50+50 -> carrier(50 oak,14 birch)=64 + plain birch x36 (the >64 generalization).
		List<ItemStack> over = VariantPiles.makeStacks(logs,
				VariantPiles.pool(logs, List.of(new ItemStack(Items.OAK_LOG, 50), new ItemStack(Items.BIRCH_LOG, 50))));
		check("50+50 -> 2 stacks", over.size() == 2, failures);
		check("first carrier 64 {oak50,birch14}",
				over.size() == 2 && over.get(0).getCount() == 64 && contentsEqual(over.get(0), Items.OAK_LOG, 50, Items.BIRCH_LOG, 14), failures);
		check("second plain birch x36",
				over.size() == 2 && over.get(1).getItem() == Items.BIRCH_LOG && over.get(1).getCount() == 36 && !VariantPiles.isPile(over.get(1)), failures);

		// 6. A non-host single-variant remainder collapses to the real vanilla item (point 1).
		ItemStack edge = VariantPiles.makeStacks(logs, VariantPiles.pool(logs,
				List.of(new ItemStack(Items.OAK_LOG, 6), new ItemStack(Items.BIRCH_LOG, 50)))).get(0);
		edge.split(6); // peel the 6 oak -> remainder {birch:50}, still hosted on oak
		check("non-host remainder is a single-variant oak-hosted pile",
				edge.getItem() == Items.OAK_LOG && VariantPiles.isPile(edge)
						&& edge.get(ModComponents.VARIANT_PILE).contents().size() == 1, failures);
		ItemStack collapsed = VariantPiles.collapseToReal(edge);
		check("collapseToReal -> real birch x50",
				collapsed.getItem() == Items.BIRCH_LOG && collapsed.getCount() == 50 && !VariantPiles.isPile(collapsed), failures);

		// 7. Pile name via the getHoverName mixin is the plural form word.
		check("pile name -> 'Logs'", "Logs".equals(pile.getHoverName().getString()), failures);

		// 8. Auto-grouping on pickup (spec §5).
		List<ItemStack> inv1 = new ArrayList<>(List.of(new ItemStack(Items.OAK_LOG, 30)));
		ItemStack in1 = new ItemStack(Items.OAK_LOG, 10);
		boolean d1 = Pickup.consolidate(inv1, in1);
		check("pickup oak onto pure oak -> oak x40 (same-variant tops up)",
				d1 && in1.isEmpty() && inv1.get(0).getItem() == Items.OAK_LOG
						&& inv1.get(0).getCount() == 40 && !VariantPiles.isPile(inv1.get(0)), failures);

		List<ItemStack> inv2 = new ArrayList<>(List.of(new ItemStack(Items.OAK_LOG, 30)));
		ItemStack in2 = new ItemStack(Items.BIRCH_LOG, 10);
		boolean d2 = Pickup.consolidate(inv2, in2);
		check("pickup birch onto pure oak -> Logs pile {oak30,birch10} (auto-consolidate)",
				d2 && in2.isEmpty() && VariantPiles.isPile(inv2.get(0)) && inv2.get(0).getCount() == 40
						&& contentsEqual(inv2.get(0), Items.OAK_LOG, 30, Items.BIRCH_LOG, 10), failures);

		List<ItemStack> inv3 = new ArrayList<>(List.of(new ItemStack(Items.OAK_LOG, 30), pile.copy()));
		ItemStack in3 = new ItemStack(Items.OAK_LOG, 5);
		Pickup.consolidate(inv3, in3);
		check("pickup oak: tops up pure oak, not the pile",
				in3.isEmpty() && inv3.get(0).getCount() == 35 && !VariantPiles.isPile(inv3.get(0))
						&& inv3.get(1).getCount() == 60 && VariantPiles.isPile(inv3.get(1)), failures);

		// 9. splitPilePreferring peels the preferred wood first — the deposit top-up that keeps a pure
		//    stack pure. 10 birch from {oak30,birch30} -> plain birch x10 + remainder {oak30,birch20}.
		ItemStack pref = pile.copy();
		ItemStack pulled = VariantPiles.splitPilePreferring(pref, 10, Items.BIRCH_LOG);
		check("preferring-peel 10 birch -> plain birch x10",
				pulled.getItem() == Items.BIRCH_LOG && pulled.getCount() == 10 && !VariantPiles.isPile(pulled), failures);
		check("preferring-peel remainder {oak30,birch20} (sum==count)",
				pref.getCount() == 50 && contentsEqual(pref, Items.OAK_LOG, 30, Items.BIRCH_LOG, 20), failures);

		// 10. Past the preferred amount the rest peels canonical-first: 40 birch-first -> {oak10,birch30}.
		ItemStack pref2 = pile.copy();
		ItemStack pulled2 = VariantPiles.splitPilePreferring(pref2, 40, Items.BIRCH_LOG);
		check("preferring-peel 40 -> pile {oak10,birch30}",
				VariantPiles.isPile(pulled2) && pulled2.getCount() == 40
						&& contentsEqual(pulled2, Items.OAK_LOG, 10, Items.BIRCH_LOG, 30), failures);
		check("preferring-peel 40 remainder plain oak x20",
				pref2.getItem() == Items.OAK_LOG && pref2.getCount() == 20 && !VariantPiles.isPile(pref2), failures);

		// 11. The active wood survives a split — the pickup / move path. A full split (== picking the whole
		//     pile up onto the cursor) must keep the selection on the peeled stack, not reset it.
		ItemStack sel = pile.copy(); // {oak30, birch30}
		VariantPiles.seed(sel, Items.BIRCH_LOG);
		check("seed sets active birch", selectedIs(sel, Items.BIRCH_LOG), failures);
		ItemStack pickedUp = VariantPiles.splitPile(sel, sel.getCount());
		check("full split (pickup) keeps active birch on the picked-up pile",
				selectedIs(pickedUp, Items.BIRCH_LOG), failures);

		// 12. A partial split keeps the active wood on whichever side still holds it: peel 10 canonical-first
		//     (oak) and birch stays selected on the remainder, with no stale selection on the peeled oak.
		ItemStack sel2 = pile.copy();
		VariantPiles.seed(sel2, Items.BIRCH_LOG);
		ItemStack peeledOak = VariantPiles.splitPile(sel2, 10);
		check("partial split: active birch stays on the remainder", selectedIs(sel2, Items.BIRCH_LOG), failures);
		check("partial split: peeled oak-only portion carries no stale selection",
				!selectedIs(peeledOak, Items.BIRCH_LOG), failures);

		// 13. The manual (curated) flag survives split/reconcile; freshly-built piles are auto.
		ItemStack tri = VariantPiles.makeStacks(logs, VariantPiles.pool(logs, List.of(
				new ItemStack(Items.OAK_LOG, 20), new ItemStack(Items.BIRCH_LOG, 20),
				new ItemStack(Items.SPRUCE_LOG, 20)))).get(0);
		check("fresh pile is auto (not manual)", !VariantPiles.isManual(tri), failures);
		ItemStack triManual = tri.copy();
		VariantPiles.markManual(triManual, true);
		check("markManual sets the flag", VariantPiles.isManual(triManual), failures);
		ItemStack manualPeeled = VariantPiles.splitPile(triManual, 30); // peeled {oak20,birch10}, rem {birch10,spruce20}
		check("split of a manual pile: peeled stays manual", VariantPiles.isManual(manualPeeled), failures);
		check("split of a manual pile: remainder stays manual", VariantPiles.isManual(triManual), failures);
		check("split of an auto pile: peeled stays auto", !VariantPiles.isManual(VariantPiles.splitPile(tri.copy(), 30)), failures);

		// 14. Auto-expand-overflow: a wood at >= a full stack across AUTO piles is pulled into pure stacks,
		//     the sub-stack remainder stays in the piles, and piles that lose a wood collapse to pure stacks.
		List<ItemStack> ovInv = new ArrayList<>();
		ovInv.add(VariantPiles.makeStacks(logs, VariantPiles.pool(logs, List.of(
				new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.BIRCH_LOG, 24)))).get(0)); // {oak40,birch24}
		ovInv.add(VariantPiles.makeStacks(logs, VariantPiles.pool(logs, List.of(
				new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.SPRUCE_LOG, 30)))).get(0)); // {oak40,spruce30}
		ovInv.add(ItemStack.EMPTY);
		ovInv.add(ItemStack.EMPTY);
		Pickup.autoExpandOverflow(ovInv, logs);
		check("overflow: 64 oak pulled into a pure stack", pureCount(ovInv, Items.OAK_LOG) == 64, failures);
		check("overflow: 16 oak left in piles", pileCount(ovInv, Items.OAK_LOG) == 16, failures);
		check("overflow: drained pile collapsed to pure birch x24", pureCount(ovInv, Items.BIRCH_LOG) == 24, failures);

		// 15. Manual piles are invisible to overflow — never drained, never counted toward the threshold.
		List<ItemStack> mInv = new ArrayList<>();
		ItemStack curated = VariantPiles.makeStacks(logs, VariantPiles.pool(logs, List.of(
				new ItemStack(Items.OAK_LOG, 50), new ItemStack(Items.BIRCH_LOG, 14)))).get(0);
		VariantPiles.markManual(curated, true);
		mInv.add(curated); // {oak50,birch14} MANUAL
		mInv.add(VariantPiles.makeStacks(logs, VariantPiles.pool(logs, List.of(
				new ItemStack(Items.OAK_LOG, 30), new ItemStack(Items.SPRUCE_LOG, 30)))).get(0)); // {oak30,spruce30} auto
		mInv.add(ItemStack.EMPTY);
		Pickup.autoExpandOverflow(mInv, logs);
		check("overflow leaves the manual pile untouched (still 50 oak, still manual)",
				VariantPiles.isManual(mInv.get(0)) && VariantPiles.countOf(mInv.get(0), Items.OAK_LOG) == 50, failures);
		check("overflow pulled nothing (auto oak only 30 < 64)", pureCount(mInv, Items.OAK_LOG) == 0, failures);

		// 16. Per-group cap: signs stack to 16, so packing caps a sign pile at 16 (not 64 like logs).
		VariantGroup signs = VariantGroups.SIGNS;
		check("signs group cap is 16", signs.cap() == 16, failures);
		List<ItemStack> signStacks = VariantPiles.makeStacks(signs, VariantPiles.pool(signs, List.of(
				new ItemStack(Items.OAK_SIGN, 10), new ItemStack(Items.SPRUCE_SIGN, 10))));
		check("20 signs -> 2 stacks at cap 16", signStacks.size() == 2 && signStacks.get(0).getCount() == 16, failures);
		check("first sign stack is a capped mixed pile", VariantPiles.isPile(signStacks.get(0)), failures);

		// 17. Transmute (phase 2): a mixed-log pile crafts to ratio planks (logs->planks, 1 log/craft, x4).
		ItemStack logPile = VariantPiles.makeStacks(logs, VariantPiles.pool(logs, List.of(
				new ItemStack(Items.OAK_LOG, 30), new ItemStack(Items.SPRUCE_LOG, 30)))).get(0);
		CraftTransmute.Outcome out = CraftTransmute.plan(List.of(logPile), 1, 1, level);
		check("transmute: mixed-log pile yields a plan", out != null && !out.isEmpty(), failures);
		if (out != null) {
			check("transmute: 30 oak + 30 spruce logs -> 120 oak + 120 spruce planks",
					out.products().equals(Map.of(Items.OAK_PLANKS, 120, Items.SPRUCE_PLANKS, 120)), failures);
			check("transmute: consumes the whole log pile",
					out.consumed().equals(Map.of(Items.OAK_LOG, 30, Items.SPRUCE_LOG, 30)), failures);
		}

		// 18. A SHAPED recipe (stairs) with mixed woods across slots — and no oak, no piles — must still
		//     match via canonical normalization and produce ratio stairs (the multi-item regression).
		List<ItemStack> stairsGrid = List.of(
				new ItemStack(Items.SPRUCE_PLANKS, 10), ItemStack.EMPTY, ItemStack.EMPTY,
				new ItemStack(Items.SPRUCE_PLANKS, 10), new ItemStack(Items.SPRUCE_PLANKS, 10), ItemStack.EMPTY,
				new ItemStack(Items.BIRCH_PLANKS, 10), new ItemStack(Items.BIRCH_PLANKS, 10), new ItemStack(Items.BIRCH_PLANKS, 10));
		CraftTransmute.Outcome stairs = CraftTransmute.plan(stairsGrid, 3, 3, level);
		check("transmute: mixed-wood shaped stairs matches via canonical normalization",
				stairs != null && !stairs.isEmpty(), failures);
		if (stairs != null) {
			check("transmute: 30 spruce + 30 birch planks -> 20 spruce + 20 birch stairs",
					stairs.products().equals(Map.of(Items.SPRUCE_STAIRS, 20, Items.BIRCH_STAIRS, 20)), failures);
		}

		// 18b. Ore families (the first non-wood groups): stone + deepslate variants consolidate on
		//      pickup like any family, and the pile reads as a proper title ("Iron Ores").
		List<ItemStack> oreInv = new ArrayList<>(List.of(new ItemStack(Items.IRON_ORE, 10)));
		ItemStack oreIn = new ItemStack(Items.DEEPSLATE_IRON_ORE, 10);
		boolean oreDone = Pickup.consolidate(oreInv, oreIn);
		check("ore pickup: deepslate iron onto iron -> one Iron Ores pile {iron10,deepslate10}",
				oreDone && oreIn.isEmpty() && VariantPiles.isPile(oreInv.get(0))
						&& oreInv.get(0).getCount() == 20
						&& contentsEqual(oreInv.get(0), Items.IRON_ORE, 10, Items.DEEPSLATE_IRON_ORE, 10), failures);
		check("ore pile name -> 'Iron Ores'", "Iron Ores".equals(oreInv.get(0).getHoverName().getString()), failures);

		// 18c. Saplings family: tree-farm clutter consolidates like any family.
		List<ItemStack> sapInv = new ArrayList<>(List.of(new ItemStack(Items.OAK_SAPLING, 5)));
		ItemStack sapIn = new ItemStack(Items.SPRUCE_SAPLING, 5);
		boolean sapDone = Pickup.consolidate(sapInv, sapIn);
		check("sapling pickup: spruce onto oak -> one Saplings pile of 10",
				sapDone && sapIn.isEmpty() && VariantPiles.isPile(sapInv.get(0))
						&& sapInv.get(0).getCount() == 10
						&& "Saplings".equals(sapInv.get(0).getHoverName().getString()), failures);

		// 18d. Creative tabs: every typed family sits DIRECTLY AFTER its canonical, in whichever tab
		//      vanilla put it (the anchor-following insertion).
		net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
				level.enabledFeatures(), false, level.registryAccess());
		check("creative tabs: typed sticks directly follow the stick",
				tabAdjacent(Items.STICK, com.mythstack.registry.ModItems.TYPED_STICKS.get(0)), failures);
		boolean allAdjacent = true;
		for (var family : com.mythstack.registry.ModBlocks.TYPED_FAMILIES.entrySet()) {
			if (!tabAdjacent(family.getKey().asItem(), family.getValue().get(0).asItem())) {
				MythStack.LOGGER.error("[selftest] tab adjacency missing for {}", family.getKey());
				allAdjacent = false;
			}
		}
		check("creative tabs: all 15 typed block families directly follow their canonicals", allAdjacent, failures);

		// 19. End-to-end menu path: a fake player driving real crafting-menu clicks (phase 3).
		failures[0] += MenuSelfTest.run(level);

		// 20. Pile-aware furnaces: a real block entity ticked through serverTick (phase 8).
		failures[0] += FurnaceSelfTest.run(level);

		// 21. Automation: crafter pulses, hopper transfer, comparator signal (phase 9).
		failures[0] += AutomationSelfTest.run(level);

		if (failures[0] == 0) {
			MythStack.LOGGER.info("[selftest] ALL CHECKS PASSED");
		} else {
			MythStack.LOGGER.error("[selftest] {} CHECK(S) FAILED", failures[0]);
		}
	}

	/** Total of {@code wood} held in plain (non-pile) stacks across {@code slots}. */
	private static int pureCount(List<ItemStack> slots, net.minecraft.world.item.Item wood) {
		int count = 0;
		for (ItemStack slot : slots) {
			if (!slot.isEmpty() && !VariantPiles.isPile(slot) && slot.getItem() == wood) {
				count += slot.getCount();
			}
		}
		return count;
	}

	/** Total of {@code wood} held inside piles across {@code slots}. */
	private static int pileCount(List<ItemStack> slots, net.minecraft.world.item.Item wood) {
		int count = 0;
		for (ItemStack slot : slots) {
			if (VariantPiles.isPile(slot)) {
				count += VariantPiles.countOf(slot, wood);
			}
		}
		return count;
	}

	private static boolean selectedIs(ItemStack stack, net.minecraft.world.item.Item wood) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		return pile != null && pile.selected().isPresent() && pile.selected().get() == wood;
	}

	private static boolean contentsEqual(ItemStack stack, Object... itemThenCount) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return false;
		}
		List<Entry> expected = new java.util.ArrayList<>();
		for (int i = 0; i < itemThenCount.length; i += 2) {
			expected.add(new Entry((net.minecraft.world.item.Item) itemThenCount[i], (Integer) itemThenCount[i + 1]));
		}
		return pile.contents().equals(expected);
	}

	/** True if some creative tab lists {@code first} immediately after {@code anchor}. */
	private static boolean tabAdjacent(net.minecraft.world.item.Item anchor, net.minecraft.world.item.Item first) {
		for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.world.item.CreativeModeTabs.allTabs()) {
			ItemStack previous = ItemStack.EMPTY;
			for (ItemStack stack : tab.getDisplayItems()) {
				if (previous.getItem() == anchor && stack.getItem() == first) {
					return true;
				}
				previous = stack;
			}
		}
		return false;
	}

	private static void check(String name, boolean ok, int[] failures) {
		if (ok) {
			MythStack.LOGGER.info("[selftest] PASS  {}", name);
		} else {
			MythStack.LOGGER.error("[selftest] FAIL  {}", name);
			failures[0]++;
		}
	}
}
