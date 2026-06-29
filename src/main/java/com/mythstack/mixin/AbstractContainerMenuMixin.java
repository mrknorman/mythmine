package com.mythstack.mixin;

import com.mythstack.variant.VariantPiles;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After any container click, collapse a single-variant pile left on the cursor or the clicked slot
 * into its real vanilla stack (point 1). A pile can become single-variant via an in-place split (which
 * can't change the host item id mid-stream); this is the controlled point where we *can* replace the
 * stack, so the icon/identity flips the instant the last other-variant leaves.
 */
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

	@Inject(method = "clicked", at = @At("TAIL"))
	private void mythstack$collapseSingleVariantPiles(int slotId, int button, ContainerInput input,
			Player player, CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;

		ItemStack carried = self.getCarried();
		ItemStack collapsedCarried = VariantPiles.collapseToReal(carried);
		if (collapsedCarried != carried) {
			self.setCarried(collapsedCarried);
		}

		if (slotId >= 0 && slotId < self.slots.size()) {
			Slot slot = self.getSlot(slotId);
			ItemStack inSlot = slot.getItem();
			ItemStack collapsed = VariantPiles.collapseToReal(inSlot);
			if (collapsed != inSlot) {
				slot.set(collapsed);
			}
		}
	}
}
