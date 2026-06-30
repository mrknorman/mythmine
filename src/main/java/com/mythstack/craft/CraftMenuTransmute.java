package com.mythstack.craft;

import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wires {@link CraftTransmute} into a crafting menu (plan §7 — phase 3). Server-authoritative: the client
 * predicts vanilla and the server corrects via {@code broadcastChanges}.
 */
public final class CraftMenuTransmute {
	private CraftMenuTransmute() {
	}

	/**
	 * Shift-click mass craft: run the whole ratio plan at once. Returns true if it handled the craft (the
	 * caller stops the vanilla shift-click loop), false to fall through to vanilla (non-wood output, or no
	 * transmutable craft).
	 */
	public static boolean tryMassCraft(AbstractContainerMenu menu, CraftingContainer grid, ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		List<ItemStack> items = grid.getItems();
		CraftTransmute.Outcome outcome = CraftTransmute.plan(items, grid.getWidth(), grid.getHeight(), level);
		if (outcome == null || outcome.isEmpty()) {
			return false;
		}
		VariantGroup outGroup = VariantGroups.of(outcome.products().keySet().iterator().next());
		if (outGroup == null) {
			return false; // non-wood output — vanilla already handles mixed input fine
		}

		// Build the ratio output, packed into piles, and add it to the inventory (auto-consolidated).
		for (ItemStack output : VariantPiles.makeStacks(outGroup, VariantPiles.pool(outGroup, toStacks(outcome.products())))) {
			if (!player.getInventory().add(output)) {
				player.drop(output, false);
			}
		}

		// Consume: clear the wood slots, then put the sub-craft leftover back (can't make another craft).
		VariantGroup inGroup = inputGroup(items);
		List<Integer> woodSlots = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			if (isWood(items.get(i))) {
				woodSlots.add(i);
				grid.setItem(i, ItemStack.EMPTY);
			}
		}
		if (inGroup != null && !outcome.leftover().isEmpty()) {
			List<ItemStack> left = VariantPiles.makeStacks(inGroup, VariantPiles.pool(inGroup, toStacks(outcome.leftover())));
			for (int k = 0; k < left.size() && k < woodSlots.size(); k++) {
				grid.setItem(woodSlots.get(k), left.get(k));
			}
		}
		menu.broadcastChanges();
		return true;
	}

	private static List<ItemStack> toStacks(Map<Item, Integer> counts) {
		List<ItemStack> stacks = new ArrayList<>(counts.size());
		for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
			stacks.add(new ItemStack(entry.getKey(), entry.getValue()));
		}
		return stacks;
	}

	private static VariantGroup inputGroup(List<ItemStack> grid) {
		for (ItemStack stack : grid) {
			if (isWood(stack)) {
				return VariantGroups.of(stack.getItem());
			}
		}
		return null;
	}

	private static boolean isWood(ItemStack stack) {
		return !stack.isEmpty() && (VariantPiles.isPile(stack) || VariantGroups.of(stack.getItem()) != null);
	}
}
