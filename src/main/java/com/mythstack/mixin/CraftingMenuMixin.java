package com.mythstack.mixin;

import com.mythstack.craft.CraftMenuTransmute;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift-clicking the crafting-table result mass-crafts a pile to ratio-preserving output (plan §7 phase 3).
 * Server-authoritative; non-wood / non-transmutable crafts fall through to vanilla. The 2×2 grid is handled
 * by {@link InventoryMenuMixin}.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mythstack$massCraft(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex == 0 && player instanceof ServerPlayer serverPlayer && CraftMenuTransmute.tryMassCraft(
				(AbstractContainerMenu) (Object) this,
				((AbstractCraftingMenuAccessor) this).mythstack$craftSlots(), serverPlayer)) {
			cir.setReturnValue(ItemStack.EMPTY); // handled — stop the vanilla shift-click loop
		}
	}
}
