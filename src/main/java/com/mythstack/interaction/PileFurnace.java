package com.mythstack.interaction;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.FuelValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Pile-aware furnaces (plan phase 8). A pile is hosted on its canonical item, so vanilla would burn or
 * smelt every element as the host (crimson stems → charcoal). These helpers make both furnace sides
 * consume <b>per element</b>: each burn eats one burnable wood at that wood's own fuel value, each smelt
 * eats one smeltable wood via that wood's own recipe — so the result, cook time, and XP all follow the
 * element, and a pile with no eligible element reports none (the furnace goes cold / idles on the
 * remainder: "piles burn down to their unburnable elements").
 *
 * <p>Element choice is smallest-count-first among eligible woods (ties → contents order) — the §1
 * entropy tenet: burning clears the clutter woods first, consolidating the pile. The pile's
 * selected/active wood is deliberately ignored: furnaces are automation, not a UI gesture. Recipes are
 * resolved per element (not per host) so materials whose variants smelt to different products (stone,
 * modded woods) extend here with at most a smarter chooser (e.g. output-slot-aware) — the seams to
 * extend are {@link #nextBurnable}, {@link #nextSmeltable}, and the output merge in {@link #smeltOne}.
 */
public final class PileFurnace {
	private static final int SLOT_INPUT = 0;
	private static final int SLOT_FUEL = 1;
	private static final int SLOT_RESULT = 2;

	private PileFurnace() {
	}

	/** A smeltable pile element and the recipe that smelts it. */
	public record Smeltable(Item wood, RecipeHolder<? extends AbstractCookingRecipe> recipe) {
	}

	/** Pile-aware burn duration: the next burnable element's own fuel value; 0 when nothing burns. */
	public static int burnDuration(FuelValues fuelValues, ItemStack stack) {
		if (!VariantPiles.isPile(stack)) {
			return fuelValues.burnDuration(stack);
		}
		Item wood = nextBurnable(fuelValues, stack);
		return wood == null ? 0 : fuelValues.burnDuration(new ItemStack(wood));
	}

	/** The fuel element the furnace consumes next: smallest-count burnable wood, or null. */
	public static Item nextBurnable(FuelValues fuelValues, ItemStack pile) {
		return smallestMatching(pile, wood -> fuelValues.burnDuration(new ItemStack(wood)) > 0);
	}

	/** The element the furnace smelts next (with its recipe): smallest-count with a recipe, or null. */
	public static Smeltable nextSmeltable(ItemStack stack, ServerLevel level,
			RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck) {
		for (VariantPile.Entry entry : byCountAscending(stack)) {
			Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> recipe =
					quickCheck.getRecipeFor(new SingleRecipeInput(new ItemStack(entry.item())), level);
			if (recipe.isPresent()) {
				return new Smeltable(entry.item(), recipe.get());
			}
		}
		return null;
	}

	/** Consume one burn's fuel element out of the pile in the fuel slot. */
	public static void consumeFuel(NonNullList<ItemStack> items, ItemStack fuel, FuelValues fuelValues) {
		Item wood = nextBurnable(fuelValues, fuel);
		if (wood == null) {
			return; // never eat the wrong wood — with duration 0 the furnace shouldn't be here anyway
		}
		VariantPiles.removeWood(fuel, wood, 1);
		items.set(SLOT_FUEL, fuel.getCount() <= 0 ? ItemStack.EMPTY : VariantPiles.collapseToReal(fuel));
	}

	/**
	 * One completed pile smelt: merge {@code result} into the output slot (mirrors vanilla
	 * {@code burn()}; a pile is never a wet sponge, so that special case doesn't apply) and eat the
	 * chosen element — vanilla's {@code shrink(1)} would peel the host canonical-first instead.
	 */
	public static void smeltOne(NonNullList<ItemStack> items, ItemStack input, ItemStack result,
			ServerLevel level, RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck) {
		ItemStack out = items.get(SLOT_RESULT);
		if (out.isEmpty()) {
			items.set(SLOT_RESULT, result.copy());
		} else {
			out.grow(result.getCount());
		}
		Smeltable next = nextSmeltable(input, level, quickCheck);
		if (next == null) {
			return;
		}
		VariantPiles.removeWood(input, next.wood(), 1);
		items.set(SLOT_INPUT, input.getCount() <= 0 ? ItemStack.EMPTY : VariantPiles.collapseToReal(input));
	}

	private static Item smallestMatching(ItemStack stack, Predicate<Item> eligible) {
		for (VariantPile.Entry entry : byCountAscending(stack)) {
			if (eligible.test(entry.item())) {
				return entry.item();
			}
		}
		return null;
	}

	/** The pile's contents smallest-count-first (stable: ties keep contents order); empty for non-piles. */
	private static List<VariantPile.Entry> byCountAscending(ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return List.of();
		}
		List<VariantPile.Entry> entries = new ArrayList<>(pile.contents());
		entries.sort(Comparator.comparingInt(VariantPile.Entry::count));
		return entries;
	}
}
