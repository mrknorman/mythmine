package com.mythstack.craft;

import com.mythstack.mixin.RecipeManagerAccessor;
import com.mythstack.registry.ModRecipes;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The sawmill's selectable-recipe set. Server side it is computed from the loaded {@code sawing}
 * recipes on demand; clients receive a display-only copy via {@code SawmillRecipesPayload} on join
 * and datapack reload (mirroring how vanilla syncs the stonecutter's set through its dedicated
 * channel — which we can't extend, hence our own).
 */
public final class SawmillRecipes {
	private static volatile SelectableRecipe.SingleInputSet<StonecutterRecipe> clientSet =
			SelectableRecipe.SingleInputSet.empty();

	private SawmillRecipes() {
	}

	/** The set for whichever side {@code access} belongs to. */
	public static SelectableRecipe.SingleInputSet<StonecutterRecipe> forAccess(RecipeAccess access) {
		return access instanceof RecipeManager manager ? build(manager) : clientSet;
	}

	/** Build the full set from the loaded sawing recipes, sorted by id for a stable UI order. */
	public static SelectableRecipe.SingleInputSet<StonecutterRecipe> build(RecipeManager manager) {
		List<RecipeHolder<StonecutterRecipe>> holders = new ArrayList<>(
				((RecipeManagerAccessor) manager).mythstack$recipes().byType(ModRecipes.SAWING));
		holders.sort(Comparator.comparing(holder -> holder.id().toString()));
		List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries = new ArrayList<>(holders.size());
		for (RecipeHolder<StonecutterRecipe> holder : holders) {
			entries.add(new SelectableRecipe.SingleInputEntry<>(holder.value().input(),
					new SelectableRecipe<>(holder.value().resultDisplay(), Optional.of(holder))));
		}
		return new SelectableRecipe.SingleInputSet<>(entries);
	}

	public static void setClientSet(SelectableRecipe.SingleInputSet<StonecutterRecipe> set) {
		clientSet = set;
	}
}
