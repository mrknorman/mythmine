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
import net.minecraft.world.item.crafting.RecipeHolder;

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

		// The ledger for the whole ratio plan (grid still pre-consume, as vanilla): crafted stats per
		// product, and every per-wood recipe the plan used — triggered and unlocked.
		for (Map.Entry<Item, Integer> product : outcome.products().entrySet()) {
			new ItemStack(product.getKey()).onCraftedBy(player, product.getValue());
		}
		for (RecipeHolder<?> recipe : outcome.recipes()) {
			player.triggerRecipeCrafted(recipe, items);
		}
		player.awardRecipes(outcome.recipes().stream().filter(recipe -> !recipe.value().isSpecial()).toList());

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

	/**
	 * Single-take: craft one. The player already holds the result (the preview), so we consume that one
	 * craft's wood — one item from <em>each</em> contributing slot, exactly like vanilla, so the recipe
	 * shape survives repeated takes. Returns true if handled (caller cancels vanilla onTake).
	 */
	public static boolean trySingleCraft(AbstractContainerMenu menu, CraftingContainer grid, ServerPlayer player) {
		CraftTransmute.Single single = CraftTransmute.firstCraft(
				grid.getItems(), grid.getWidth(), grid.getHeight(), (ServerLevel) player.level());
		if (single == null) {
			return false;
		}
		// The ledger — vanilla's ResultSlot.checkTakeAchievements, per the ELEMENT recipe actually used:
		// crafted stat + item hook, the recipe-crafted advancement trigger (grid pre-consume, as vanilla),
		// and the recipe-book unlock.
		award(player, single.product(), single.recipe(), grid);
		for (CraftTransmute.SlotTake take : single.takes()) {
			ItemStack slot = grid.getItem(take.slot());
			if (VariantPiles.isPile(slot)) {
				VariantPiles.removeWood(slot, take.wood(), 1);
				grid.setItem(take.slot(), slot.getCount() <= 0 ? ItemStack.EMPTY : VariantPiles.collapseToReal(slot));
			} else {
				slot.shrink(1);
				grid.setItem(take.slot(), slot.isEmpty() ? ItemStack.EMPTY : slot);
			}
		}
		menu.broadcastChanges();
		return true;
	}

	/** Vanilla's take-achievements, aimed at the element recipe the transmuter actually used. */
	private static void award(ServerPlayer player, ItemStack product, RecipeHolder<?> recipe, CraftingContainer grid) {
		product.onCraftedBy(player, product.getCount());
		player.triggerRecipeCrafted(recipe, grid.getItems());
		if (!recipe.value().isSpecial()) {
			player.awardRecipes(List.of(recipe));
		}
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
