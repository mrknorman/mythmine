package com.mythstack.mixin;

import com.mythstack.craft.CraftMenuTransmute;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same ratio mass-craft as {@link CraftingMenuMixin}, for the player's own 2×2 crafting grid. Result is
 * slot 0 here too.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mythstack$massCraft(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex == 0 && player instanceof ServerPlayer serverPlayer && CraftMenuTransmute.tryMassCraft(
				(AbstractContainerMenu) (Object) this,
				((AbstractCraftingMenuAccessor) this).mythstack$craftSlots(), serverPlayer)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
