package com.mythstack.craft;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * The transmute layer (plan §7 crafting redesign — phase 2). Reads a crafting grid whose wood members may
 * be piles and produces a <em>ratio-preserving</em> craft plan: which product items to make, and how much
 * of each input wood to consume, driven by {@link CraftPlanner}.
 *
 * <p>It never hardcodes wood→product. It identifies the recipe by matching the grid as-is (the piles host
 * on their canonical, so vanilla matches the canonical recipe), then for each output wood it <b>substitutes
 * that wood into the grid and re-assembles</b> — the parallel per-wood recipe yields that wood's product.
 * That covers every wood→wood recipe (and modded woods) for free. If the assembled result is itself in a
 * variant group the output is wood-typed (ratio); otherwise it's a non-wood output planned by entropy.
 */
public final class CraftTransmute {
	private CraftTransmute() {
	}

	/**
	 * What to make ({@code product item → count}), what to eat ({@code input wood → count}), and the input
	 * wood left in the grid afterwards ({@code input wood → count}).
	 */
	public record Outcome(LinkedHashMap<Item, Integer> products, LinkedHashMap<Item, Integer> consumed,
			LinkedHashMap<Item, Integer> leftover) {
		public boolean isEmpty() {
			return products.isEmpty();
		}
	}

	/**
	 * Plan the transmuted craft for {@code grid} (row-major, {@code width × height}), or {@code null} if it
	 * isn't a craftable wood recipe. {@code level} supplies the recipe set.
	 */
	public static Outcome plan(List<ItemStack> grid, int width, int height, ServerLevel level) {
		// Only transmute pure-wood recipes: every non-empty ingredient must be a wood member. A recipe that
		// also needs sticks etc. is left to vanilla — we wouldn't consume the non-wood ingredients.
		for (ItemStack stack : grid) {
			if (!stack.isEmpty() && !isWood(stack)) {
				return null;
			}
		}
		VariantGroup group = inputGroup(grid);
		int perCraft = woodSlots(grid);
		LinkedHashMap<Item, Integer> pool = buildPool(grid);
		if (group == null || perCraft == 0 || pool.isEmpty()) {
			return null;
		}
		// Identify the recipe by normalizing every wood slot to the canonical wood — so a shape filled with
		// MIXED woods (which matches no per-wood recipe as laid out) still resolves to its canonical recipe.
		ItemStack canonical = productStack(grid, width, height, group.canonical(), level);
		if (canonical == null || canonical.isEmpty()) {
			return null;
		}
		int outputPerCraft = canonical.getCount();

		LinkedHashMap<Item, Integer> products = new LinkedHashMap<>();
		LinkedHashMap<Item, Integer> leftover;
		if (VariantGroups.of(canonical.getItem()) != null) {
			// Wood-typed output: ratio plan, each output wood mapped to its product by substitution.
			CraftPlanner.RatioPlan<Item> plan = CraftPlanner.planRatio(pool, perCraft);
			for (var entry : plan.craftsByWood().entrySet()) {
				ItemStack product = productStack(grid, width, height, entry.getKey(), level);
				if (product != null && !product.isEmpty()) {
					products.merge(product.getItem(), entry.getValue() * outputPerCraft, Integer::sum);
				}
			}
			leftover = plan.leftover();
		} else {
			// Non-wood output: entropy plan, the single product repeated.
			CraftPlanner.EntropyPlan<Item> plan = CraftPlanner.planEntropy(pool, perCraft);
			if (plan.crafts() > 0) {
				products.put(canonical.getItem(), plan.crafts() * outputPerCraft);
			}
			leftover = plan.leftover();
		}
		return new Outcome(products, subtract(pool, leftover), leftover);
	}

	/** Assemble the product when {@code wood} fills every wood slot (substitute-and-assemble), or null. */
	private static ItemStack productStack(List<ItemStack> grid, int width, int height, Item wood, ServerLevel level) {
		List<ItemStack> substituted = new ArrayList<>(grid.size());
		for (ItemStack stack : grid) {
			substituted.add(isWood(stack) ? new ItemStack(wood) : stack.copy());
		}
		CraftingInput input = CraftingInput.of(width, height, substituted);
		return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(holder -> holder.value().assemble(input)).orElse(null);
	}

	private static VariantGroup inputGroup(List<ItemStack> grid) {
		for (ItemStack stack : grid) {
			if (isWood(stack)) {
				return VariantGroups.of(stack.getItem());
			}
		}
		return null;
	}

	/** One slot's contribution to a single craft: take one {@code wood} from grid slot {@code slot}. */
	public record SlotTake(int slot, Item wood) {
	}

	/** The product + per-slot consumption of the next single craft (result preview / one-at-a-time take). */
	public record Single(ItemStack product, List<SlotTake> takes) {
	}

	/**
	 * The next single craft this grid would make, or {@code null} if it isn't a transmutable wood craft.
	 * A single craft is <em>per-slot</em>, exactly like vanilla: each occupied wood slot contributes one
	 * item — a plain stack its own wood, a pile its <b>active</b> wood (scroll the pile in the grid to
	 * steer it). That keeps the grid shape intact across takes (a pooled draw would empty the first slots
	 * and break the recipe shape mid-way). Output = the majority contribution; ties go to the earliest
	 * contributing wood (the first-placed rule). Ratio/pooled crafting is the shift-click mass path.
	 */
	public static Single firstCraft(List<ItemStack> grid, int width, int height, ServerLevel level) {
		for (ItemStack stack : grid) {
			if (!stack.isEmpty() && !isWood(stack)) {
				return null;
			}
		}
		VariantGroup group = inputGroup(grid);
		if (group == null) {
			return null;
		}
		List<SlotTake> takes = new ArrayList<>();
		LinkedHashMap<Item, Integer> tally = new LinkedHashMap<>(); // insertion order = first-placed order
		for (int i = 0; i < grid.size(); i++) {
			ItemStack stack = grid.get(i);
			if (!isWood(stack)) {
				continue;
			}
			Item give = VariantPiles.isPile(stack) ? VariantPiles.activeWood(stack) : stack.getItem();
			if (give == null) {
				return null;
			}
			takes.add(new SlotTake(i, give));
			tally.merge(give, 1, Integer::sum);
		}
		if (takes.isEmpty()) {
			return null;
		}
		ItemStack canonical = productStack(grid, width, height, group.canonical(), level);
		if (canonical == null || canonical.isEmpty()) {
			return null; // no recipe under canonical normalization
		}
		// Note the output need NOT belong to a variant group: per-wood outputs outside any group (boats)
		// transmute by substitution like everything else, and wood-agnostic outputs (chest, sticks) come
		// out identical for every wood — reproducing vanilla, but with active-wood pile consumption.
		Item output = null;
		int best = 0;
		for (var entry : tally.entrySet()) {
			if (entry.getValue() > best) { // strict > keeps the earliest wood on ties
				output = entry.getKey();
				best = entry.getValue();
			}
		}
		ItemStack product = productStack(grid, width, height, output, level);
		return product == null || product.isEmpty() ? null : new Single(product, takes);
	}

	/** The result-slot preview: one craft's worth of the head wood's product, or empty. */
	public static ItemStack previewProduct(List<ItemStack> grid, int width, int height, ServerLevel level) {
		Single single = firstCraft(grid, width, height, level);
		return single == null ? ItemStack.EMPTY : single.product();
	}

	/** Pool the grid's wood into {@code wood → count}, leading with the first slot's active wood. */
	private static LinkedHashMap<Item, Integer> buildPool(List<ItemStack> grid) {
		LinkedHashMap<Item, Integer> pool = new LinkedHashMap<>();
		Item primary = primaryWood(grid);
		if (primary != null) {
			pool.put(primary, 0); // reserve first position so the active wood leads (preview + tie-breaks)
		}
		for (ItemStack stack : grid) {
			VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
			if (pile != null) {
				for (VariantPile.Entry entry : pile.contents()) {
					pool.merge(entry.item(), entry.count(), Integer::sum);
				}
			} else if (isWood(stack)) {
				pool.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		pool.values().removeIf(count -> count == 0);
		return pool;
	}

	private static Item primaryWood(List<ItemStack> grid) {
		for (ItemStack stack : grid) {
			if (isWood(stack)) {
				return VariantPiles.isPile(stack) ? VariantPiles.activeWood(stack) : stack.getItem();
			}
		}
		return null;
	}

	private static int woodSlots(List<ItemStack> grid) {
		int n = 0;
		for (ItemStack stack : grid) {
			if (isWood(stack)) {
				n++;
			}
		}
		return n;
	}

	private static boolean isWood(ItemStack stack) {
		return !stack.isEmpty() && (VariantPiles.isPile(stack) || VariantGroups.of(stack.getItem()) != null);
	}

	private static LinkedHashMap<Item, Integer> subtract(LinkedHashMap<Item, Integer> pool, LinkedHashMap<Item, Integer> leftover) {
		LinkedHashMap<Item, Integer> consumed = new LinkedHashMap<>();
		for (var entry : pool.entrySet()) {
			int eaten = entry.getValue() - leftover.getOrDefault(entry.getKey(), 0);
			if (eaten > 0) {
				consumed.put(entry.getKey(), eaten);
			}
		}
		return consumed;
	}
}
