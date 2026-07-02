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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	 * What to make ({@code product item → count}), what to eat ({@code input wood → count}), the input
	 * wood left in the grid afterwards ({@code input wood → count}), and the recipes that were used —
	 * one per output wood — for the crafting ledger (stats, unlocks, advancement triggers).
	 */
	public record Outcome(LinkedHashMap<Item, Integer> products, LinkedHashMap<Item, Integer> consumed,
			LinkedHashMap<Item, Integer> leftover, List<RecipeHolder<?>> recipes) {
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
		// The pooled ratio plan is single-group only: multi-group grids (gates: planks + sticks) return
		// null here, so shift-click falls back to the vanilla loop over our per-slot single-takes —
		// serial mass crafting with correct propagation, just not pooled rebalancing.
		VariantGroup group = inputGroup(grid);
		int perCraft = woodSlots(grid);
		LinkedHashMap<Item, Integer> pool = buildPool(grid);
		if (group == null || perCraft == 0 || pool.isEmpty() || !singleGroup(grid, group)) {
			return null;
		}
		// Identify the recipe by normalizing every wood slot to the canonical wood — so a shape filled with
		// MIXED woods (which matches no per-wood recipe as laid out) still resolves to its canonical recipe.
		Map<String, Optional<Resolved>> memo = new HashMap<>();
		Resolved canonical = resolve(grid, width, height, "oak", level, memo);
		if (canonical == null || canonical.product().isEmpty()) {
			return null;
		}

		LinkedHashMap<Item, Integer> products = new LinkedHashMap<>();
		List<RecipeHolder<?>> recipes = new ArrayList<>();
		LinkedHashMap<Item, Integer> leftover;
		if (VariantGroups.of(canonical.product().getItem()) != null) {
			// Wood-typed output: resolve each wood's recipe + product up front. A wood with no product
			// for this recipe (there is no crimson boat) is unproductive — consumable by mixed crafts but
			// never an output. Counts are PER WOOD: a bamboo block yields 2 planks where a log yields 4.
			LinkedHashMap<Item, Resolved> byWood = new LinkedHashMap<>();
			for (Item wood : pool.keySet()) {
				Resolved resolved = resolve(grid, width, height, VariantGroups.variantKey(wood), level, memo);
				if (resolved != null && !resolved.product().isEmpty()) {
					byWood.put(wood, resolved);
				}
			}
			CraftPlanner.RatioPlan<Item> plan = CraftPlanner.planRatio(pool, perCraft, byWood.keySet());
			for (var entry : plan.craftsByWood().entrySet()) {
				Resolved resolved = byWood.get(entry.getKey());
				if (resolved != null && entry.getValue() > 0) {
					products.merge(resolved.product().getItem(),
							entry.getValue() * resolved.product().getCount(), Integer::sum);
					if (!recipes.contains(resolved.recipe())) {
						recipes.add(resolved.recipe());
					}
				}
			}
			leftover = plan.leftover();
		} else {
			// Non-wood output: entropy plan, the single product repeated.
			CraftPlanner.EntropyPlan<Item> plan = CraftPlanner.planEntropy(pool, perCraft);
			if (plan.crafts() > 0) {
				products.put(canonical.product().getItem(), plan.crafts() * canonical.product().getCount());
				recipes.add(canonical.recipe());
			}
			leftover = plan.leftover();
		}
		return new Outcome(products, subtract(pool, leftover), leftover, recipes);
	}

	/** A wood's recipe + assembled product under substitution — what one craft of that wood uses/makes. */
	private record Resolved(RecipeHolder<CraftingRecipe> recipe, ItemStack product) {
	}

	/**
	 * Resolve the recipe matched (and product assembled) when the wood identified by {@code variantKey}
	 * fills every wood slot — per SLOT per GROUP (spec §13 phase B): a planks slot gets the wood's
	 * planks, a stick slot its stick, so multi-group recipes (gates, signs) transmute per wood. The
	 * key {@code "oak"} is the canonical normalization (every group's canonical member is its oak
	 * form). Null when the wood lacks a slot's form or no recipe matches. Memoized per top-level call
	 * — recipe-manager scans are the expensive part of a grid recompute.
	 */
	private static Resolved resolve(List<ItemStack> grid, int width, int height, String variantKey,
			ServerLevel level, Map<String, Optional<Resolved>> memo) {
		return memo.computeIfAbsent(variantKey, key -> {
			List<ItemStack> stacks = substitute(grid, key);
			if (stacks == null) {
				return Optional.empty();
			}
			CraftingInput input = CraftingInput.of(width, height, stacks);
			return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
					.map(holder -> new Resolved(holder, holder.value().assemble(input)));
		}).orElse(null);
	}

	/** The grid with every wood slot swapped for {@code variantKey}'s member of that slot's group. */
	private static List<ItemStack> substitute(List<ItemStack> grid, String variantKey) {
		List<ItemStack> stacks = new ArrayList<>(grid.size());
		for (ItemStack stack : grid) {
			if (!isWood(stack)) {
				stacks.add(stack.copy());
				continue;
			}
			VariantGroup slotGroup = VariantGroups.of(stack.getItem());
			Item member = VariantGroups.member(slotGroup, variantKey);
			if (member == null) {
				if (!VariantGroups.sameFamily(slotGroup, variantKey)) {
					stacks.add(stack.copy()); // other family (planks in a piston): the recipe decides
					continue;
				}
				return null; // this wood lacks the slot's form (e.g. bamboo has no hyphae)
			}
			stacks.add(new ItemStack(member));
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

	/** One slot's contribution to a single craft: take one {@code wood} from grid slot {@code slot}. */
	public record SlotTake(int slot, Item wood) {
	}

	/**
	 * The product + per-slot consumption of the next single craft (result preview / one-at-a-time take),
	 * with the recipe that makes it — the crafting ledger (stats, unlocks, triggers) follows that recipe.
	 */
	public record Single(ItemStack product, List<SlotTake> takes, RecipeHolder<CraftingRecipe> recipe) {
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
		VariantGroup group = inputGroup(grid);
		if (group == null) {
			return null;
		}
		List<SlotTake> takes = new ArrayList<>();
		// Contributions tally by cross-group wood IDENTITY (spec §13 phase B): a spruce stick and a
		// spruce plank both count toward "spruce", so a gate grid propagates its wood as one voice.
		LinkedHashMap<String, Integer> tally = new LinkedHashMap<>(); // insertion order = first-placed
		boolean anyWood = false;
		for (int i = 0; i < grid.size(); i++) {
			ItemStack stack = grid.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (isWood(stack)) {
				Item give = VariantPiles.isPile(stack) ? VariantPiles.activeWood(stack) : stack.getItem();
				if (give == null) {
					return null;
				}
				takes.add(new SlotTake(i, give));
				tally.merge(VariantGroups.variantKey(give), 1, Integer::sum);
				anyWood = true;
			} else {
				// A NON-family ingredient (books, chains, coal) is consumed one-per-slot as-is — it just
				// doesn't vote on the wood. Substitution passes it through untouched, so per-wood recipes
				// with mixed ingredients (hanging signs, bookshelves) transmute like everything else.
				// Items with a crafting remainder (buckets) stay fully vanilla — we don't replicate that.
				if (stack.getItem().getCraftingRemainder() != null) {
					return null;
				}
				takes.add(new SlotTake(i, stack.getItem()));
			}
		}
		if (!anyWood) {
			return null;
		}
		Map<String, Optional<Resolved>> memo = new HashMap<>();
		Resolved canonical = resolve(grid, width, height, "oak", level, memo);
		if (canonical == null || canonical.product().isEmpty()) {
			return null; // no recipe under canonical (per-group oak) normalization
		}
		// Note the output need NOT belong to a variant group: per-wood outputs outside any group (boats)
		// transmute by substitution like everything else, and wood-agnostic outputs (chest, sticks) come
		// out identical for every wood — reproducing vanilla, but with active-wood pile consumption.
		// Output = the most common contribution that actually HAS a product for this recipe: there is
		// no crimson boat, so a crimson-majority grid makes the runner-up wood's boat. Descending count,
		// stable sort — ties keep first-placed order. No productive wood at all -> not craftable.
		List<Map.Entry<String, Integer>> byCount = new ArrayList<>(tally.entrySet());
		byCount.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		for (var entry : byCount) {
			Resolved resolved = resolve(grid, width, height, entry.getKey(), level, memo);
			if (resolved != null && !resolved.product().isEmpty()) {
				return new Single(resolved.product(), takes, resolved.recipe());
			}
		}
		return null;
	}

	/** True when every wood slot belongs to {@code group} — the pooled mass path can't mix groups. */
	private static boolean singleGroup(List<ItemStack> grid, VariantGroup group) {
		for (ItemStack stack : grid) {
			if (isWood(stack) && VariantGroups.of(stack.getItem()) != group) {
				return false;
			}
		}
		return true;
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
