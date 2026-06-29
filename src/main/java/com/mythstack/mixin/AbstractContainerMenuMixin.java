package com.mythstack.mixin;

import com.mythstack.interaction.PileSeparation;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * After any container click, collapse a single-variant pile left on the cursor or the clicked slot
 * into its real vanilla stack (point 1). A pile can become single-variant via an in-place split (which
 * can't change the host item id mid-stream); this is the controlled point where we *can* replace the
 * stack, so the icon/identity flips the instant the last other-variant leaves.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

	@Shadow
	@Final
	private Set<Slot> quickcraftSlots;

	@Shadow
	protected abstract void resetQuickCraft();

	/**
	 * Pile-specific click handling, only when a pile is on the cursor:
	 * <ul>
	 *   <li>{@code QUICK_CRAFT} (drag) splits the pile as evenly as possible into a pile/stack per
	 *       dragged slot — vanilla would copy the component into each with a fractional count, producing
	 *       inconsistent piles. We act only on the execute phase; start/add accumulate the slots.</li>
	 *   <li>{@code PICKUP_ALL} (double-click) expands the pile into the surrounding storage instead of
	 *       vanilla "gather all".</li>
	 * </ul>
	 * Both run on the server (authoritative) and the client (prediction / the creative-inventory path);
	 * the operations are deterministic, so the sides converge.
	 */
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void mythstack$pileClick(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		if (!VariantPiles.isPile(self.getCarried())) {
			return;
		}
		if (input == ContainerInput.QUICK_CRAFT) {
			if (AbstractContainerMenu.getQuickcraftHeader(button) == 2) { // the drag's execute phase
				PileSeparation.dragDistribute(self, this.quickcraftSlots);
				this.resetQuickCraft();
				self.broadcastChanges();
				ci.cancel();
			}
		} else if (input == ContainerInput.PICKUP_ALL) {
			// Runs on both sides: the server authoritatively, the client as instant prediction (and as the
			// authoritative path for the creative inventory, which routes its clicks here client-side).
			// expand() is drop-free and deterministic, so both sides converge.
			self.setCarried(PileSeparation.expand(self, slotId));
			self.broadcastChanges();
			ci.cancel();
		}
	}

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
