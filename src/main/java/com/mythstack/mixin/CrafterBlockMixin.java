package com.mythstack.mixin;

import com.mythstack.craft.CraftTransmute;
import com.mythstack.variant.VariantPiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Pile-aware crafter (plan phase 9): the crafter is the automation face of the crafting table, so a
 * pulse behaves exactly like one single-take — canonical-normalized matching, pile slots contribute
 * their ACTIVE wood, output = the majority productive contribution. Without this the crafter resolves
 * recipes against the pile HOST: a mixed pile silently canonicalizes (the very bug the table redesign
 * removed) and a mixed plain grid refuses to craft at all.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

	/**
	 * Swap the host-matched recipe for the transmuted element recipe — wood recipes' {@code assemble}
	 * ignores the input, so the dispensed product follows the swap (the furnace's local-swap trick).
	 * A transmutable grid that vanilla matched as no recipe (mixed plain) becomes craftable here.
	 */
	@ModifyVariable(method = "dispenseFrom", at = @At("STORE"), ordinal = 0)
	private Optional<RecipeHolder<CraftingRecipe>> mythstack$transmuteRecipe(
			Optional<RecipeHolder<CraftingRecipe>> vanilla, BlockState state, ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter) {
			CraftTransmute.Single single = CraftTransmute.firstCraft(
					crafter.getItems(), crafter.getWidth(), crafter.getHeight(), level);
			if (single != null) {
				return Optional.of(single.recipe());
			}
		}
		return vanilla;
	}

	/**
	 * Consume per element: a pile slot gives one of its ACTIVE wood — vanilla's blanket
	 * {@code shrink(1)} would peel the host canonical-first and eat the wrong wood. Plain slots and
	 * non-transmutable grids take the vanilla path untouched. The grid is unchanged since the recipe
	 * swap above, so this recompute yields the identical single craft.
	 */
	@Redirect(method = "dispenseFrom", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/core/NonNullList;forEach(Ljava/util/function/Consumer;)V"))
	private void mythstack$consumeElements(NonNullList<ItemStack> items, Consumer<ItemStack> vanillaShrink,
			BlockState state, ServerLevel level, BlockPos pos) {
		CraftTransmute.Single single = CraftTransmute.firstCraft(items, 3, 3, level); // crafters are 3×3
		if (single == null) {
			items.forEach(vanillaShrink);
			return;
		}
		for (CraftTransmute.SlotTake take : single.takes()) {
			ItemStack slot = items.get(take.slot());
			if (VariantPiles.isPile(slot)) {
				VariantPiles.removeWood(slot, take.wood(), 1);
				items.set(take.slot(), slot.getCount() <= 0 ? ItemStack.EMPTY : VariantPiles.collapseToReal(slot));
			} else {
				slot.shrink(1);
			}
		}
	}
}
