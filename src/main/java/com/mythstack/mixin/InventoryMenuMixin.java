package com.mythstack.mixin;

import com.mythstack.craft.CraftMenuTransmute;
import com.mythstack.interaction.Pickup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same ratio mass-craft as {@link CraftingMenuMixin}, for the player's own 2×2 crafting grid. Result is
 * slot 0 here too.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

	protected InventoryMenuMixin(MenuType<?> menuType, int containerId) {
		super(menuType, containerId);
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mythstack$massCraft(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex == 0 && player instanceof ServerPlayer serverPlayer && CraftMenuTransmute.tryMassCraft(
				(AbstractContainerMenu) (Object) this,
				((AbstractCraftingMenuAccessor) this).mythstack$craftSlots(), serverPlayer)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}

	/** Crafted output consolidates like a pickup — same rule as {@link CraftingMenuMixin}. */
	@Redirect(method = "quickMoveStack", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/world/inventory/InventoryMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z"))
	private boolean mythstack$consolidateResult(InventoryMenu menu, ItemStack stack, int start, int end,
			boolean reverse, Player player, int slotIndex) {
		if (Pickup.consolidate(player.getInventory().getNonEquipmentItems(), stack)) {
			return true;
		}
		return this.moveItemStackTo(stack, start, end, reverse);
	}
}
