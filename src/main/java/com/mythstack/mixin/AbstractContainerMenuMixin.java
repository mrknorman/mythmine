package com.mythstack.mixin;

import com.mythstack.interaction.DragMerge;
import com.mythstack.interaction.PileSeparation;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPiles;
import net.minecraft.server.level.ServerPlayer;
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
	 * Pile / wood double-click and drag handling:
	 * <ul>
	 *   <li>pile + {@code QUICK_CRAFT} (drag) → split the pile evenly into a pile/stack per dragged slot
	 *       (vanilla copies the component fractionally → corrupt piles); only the execute phase acts.</li>
	 *   <li>pile + {@code PICKUP_ALL} (double-click) → expand into the surrounding storage.</li>
	 *   <li>plain wood stack + {@code PICKUP_ALL} (double-click) → contract: gather that wood (incl. from
	 *       other piles), then fill the gap with whole other-wood stacks, into a seeded pile.</li>
	 * </ul>
	 * All are server-authoritative: the client cancels vanilla and waits for the synced result. Running
	 * these as client predictions desynced under rapid actions (stray stacks). The creative inventory
	 * (client-authoritative clicks) is therefore not covered here — that needs a dedicated client path.
	 */
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void mythstack$pileClick(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		ItemStack carried = self.getCarried();
		boolean server = player instanceof ServerPlayer;
		// Survival is server-authoritative (the client predicts via vanilla, so we act only on the server).
		// Creative has no server-side container-click path for the inventory — the screen runs the click on
		// the client and syncs via creative slot packets — so there we run the op client-side and
		// CreativeModeInventoryScreenMixin pushes the changed slots to the server.
		boolean creativeClient = !server && player.hasInfiniteMaterials();
		boolean run = server || creativeClient;
		if (VariantPiles.isPile(carried)) {
			if (input == ContainerInput.QUICK_CRAFT) {
				if (AbstractContainerMenu.getQuickcraftHeader(button) == 2) { // the drag's execute phase
					if (run) {
						PileSeparation.dragDistribute(self, this.quickcraftSlots);
						if (server) {
							self.broadcastChanges();
						}
					}
					this.resetQuickCraft();
					ci.cancel();
				}
			} else if (input == ContainerInput.PICKUP && button == 0
					&& slotId >= 0 && slotId < self.slots.size()
					&& VariantPiles.isPile(self.getSlot(slotId).getItem())
					&& VariantGroups.of(self.getSlot(slotId).getItem().getItem()) == VariantGroups.of(carried.getItem())) {
				// Pile dropped on a same-group pile: unmix (repack both, purest stack stays in the slot,
				// remainder on the cursor) instead of the vanilla swap. If the pair is already optimally
				// packed the acting side falls through to vanilla, so the click still swaps.
				if (run) {
					if (DragMerge.unmix(self, self.getSlot(slotId), carried)) {
						if (server) {
							self.broadcastChanges();
						}
						ci.cancel();
					}
				} else {
					ci.cancel(); // survival client: wait for the server's authoritative result
				}
			} else if (input == ContainerInput.PICKUP_ALL) {
				if (run) {
					self.setCarried(PileSeparation.expand(self, slotId));
					if (server) {
						self.broadcastChanges();
					}
				}
				ci.cancel();
			}
		} else if (input == ContainerInput.PICKUP_ALL && !carried.isEmpty()
				&& VariantGroups.of(carried.getItem()) != null) {
			if (run) {
				self.setCarried(PileSeparation.contract(self, player));
				if (server) {
					self.broadcastChanges();
				}
			}
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
