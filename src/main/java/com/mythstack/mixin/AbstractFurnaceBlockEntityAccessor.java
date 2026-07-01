package com.mythstack.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Private furnace internals used by the pile-aware hooks in {@link AbstractFurnaceBlockEntityMixin}. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {

	@Accessor("quickCheck")
	RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> mythstack$quickCheck();

	@Invoker("consumeFuel")
	static void mythstack$consumeFuel(NonNullList<ItemStack> items, ItemStack fuel) {
		throw new AssertionError();
	}

	@Invoker("burn")
	static void mythstack$burn(NonNullList<ItemStack> items, ItemStack input, ItemStack result) {
		throw new AssertionError();
	}
}
