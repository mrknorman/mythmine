package com.mythstack.dev;

import com.mythstack.MythStack;
import com.mythstack.interaction.Pickup;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPile.Entry;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only headless self-test for the {@link VariantPiles} layer (build phase 4). Runs on server
 * start in the dev environment and logs PASS/FAIL per check — verifiable via {@code runServer}
 * without a client. Not registered in production.
 */
public final class SelfTest {
	private SelfTest() {
	}

	public static void run() {
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

		// 7. Pile name via the getHoverName mixin is the form word.
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

		if (failures[0] == 0) {
			MythStack.LOGGER.info("[selftest] ALL CHECKS PASSED");
		} else {
			MythStack.LOGGER.error("[selftest] {} CHECK(S) FAILED", failures[0]);
		}
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

	private static void check(String name, boolean ok, int[] failures) {
		if (ok) {
			MythStack.LOGGER.info("[selftest] PASS  {}", name);
		} else {
			MythStack.LOGGER.error("[selftest] FAIL  {}", name);
			failures[0]++;
		}
	}
}
