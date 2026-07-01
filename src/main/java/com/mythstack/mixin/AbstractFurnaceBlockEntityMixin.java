package com.mythstack.mixin;

import com.mythstack.interaction.PileFurnace;
import com.mythstack.variant.VariantPiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pile-aware furnaces (plan phase 8): both furnace sides consume piles <b>per element</b> via
 * {@link PileFurnace} — "piles burn down to their unburnable elements", and likewise smelt down.
 * Covers the smoker and blast furnace too (same base class; the blast furnace's halved burn time
 * calls {@code super.getBurnDuration}, so the pile-aware duration flows through). Plain stacks take
 * every vanilla path untouched.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin extends BaseContainerBlockEntity {

	protected AbstractFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Fuel validity + duration come from the pile's next burnable element, not the host. */
	@Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
	private void mythstack$pileBurnDuration(FuelValues fuelValues, ItemStack stack,
			CallbackInfoReturnable<Integer> cir) {
		if (VariantPiles.isPile(stack)) {
			cir.setReturnValue(PileFurnace.burnDuration(fuelValues, stack));
		}
	}

	/** Igniting eats that same element out of the pile — vanilla's shrink would peel canonical-first. */
	@Redirect(method = "serverTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;consumeFuel(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;)V"))
	private static void mythstack$consumePileFuel(NonNullList<ItemStack> items, ItemStack fuel,
			ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity) {
		if (VariantPiles.isPile(fuel)) {
			PileFurnace.consumeFuel(items, fuel, level.fuelValues());
		} else {
			AbstractFurnaceBlockEntityAccessor.mythstack$consumeFuel(items, fuel);
		}
	}

	/**
	 * The smelt recipe is resolved for the pile's next smeltable ELEMENT, so the result, cook time,
	 * and XP ledger ({@code setRecipeUsed}) all follow the element — the extension point for materials
	 * whose variants smelt differently (stone). A pile with no smeltable element resolves to no recipe,
	 * so the furnace idles on the remainder exactly like any unsmeltable input.
	 */
	@ModifyVariable(method = "serverTick", at = @At("STORE"), ordinal = 0)
	private static RecipeHolder<?> mythstack$perElementRecipe(RecipeHolder<?> hostRecipe,
			ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity) {
		ItemStack input = entity.getItem(0);
		if (!VariantPiles.isPile(input)) {
			return hostRecipe;
		}
		PileFurnace.Smeltable next = PileFurnace.nextSmeltable(input, level,
				((AbstractFurnaceBlockEntityAccessor) entity).mythstack$quickCheck());
		return next == null ? null : next.recipe();
	}

	/** A completed smelt consumes the chosen element from the pile (output merge mirrored). */
	@Redirect(method = "serverTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"))
	private static void mythstack$pileSmelt(NonNullList<ItemStack> items, ItemStack input, ItemStack result,
			ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity) {
		if (VariantPiles.isPile(input)) {
			PileFurnace.smeltOne(items, input, result, level,
					((AbstractFurnaceBlockEntityAccessor) entity).mythstack$quickCheck());
		} else {
			AbstractFurnaceBlockEntityAccessor.mythstack$burn(items, input, result);
		}
	}

	/**
	 * QOL (agreed): hoppers may extract an UNBURNABLE stack from the fuel slot — the same escape hatch
	 * vanilla gives empty buckets — so a burned-down nether remainder doesn't jam automation. Only ever
	 * widens what vanilla allows.
	 */
	@Inject(method = "canTakeItemThroughFace", at = @At("HEAD"), cancellable = true)
	private void mythstack$extractUnburnable(int slot, ItemStack stack, Direction direction,
			CallbackInfoReturnable<Boolean> cir) {
		if (slot == 1 && !stack.isEmpty() && this.level != null
				&& PileFurnace.burnDuration(this.level.fuelValues(), stack) == 0) {
			cir.setReturnValue(true);
		}
	}
}
