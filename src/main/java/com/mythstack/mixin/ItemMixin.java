package com.mythstack.mixin;

import com.mythstack.interaction.DragMerge;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks vanilla's two "click a carried stack against a slot" extension points so piles behave like
 * bundles (the closest vanilla analogue), keyed on the pile's active wood:
 * <ul>
 *   <li>{@code overrideStackedOnOther} ({@code self} = carried): dropping a different same-group member
 *       onto a slot drag-merges into a pile (§6); right-clicking a carried pile onto an <em>empty</em>
 *       slot drips the active wood out (bundle parity — {@code BundleItem.overrideStackedOnOther}).</li>
 *   <li>{@code overrideOtherStackedOnMe} ({@code self} = slot item): right-clicking a pile in a slot with
 *       an empty cursor extracts its active wood onto the cursor (bundle parity —
 *       {@code BundleItem.overrideOtherStackedOnMe}).</li>
 * </ul>
 * Both run inside {@code AbstractContainerMenu.doClick} on client and server alike; the operations are
 * single-slot and deterministic, so — exactly like bundles — they need no packet and no server gating.
 */
@Mixin(Item.class)
public class ItemMixin {

	@Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
	private void mythstack$dragMerge(ItemStack carried, Slot slot, ClickAction action, Player player,
			CallbackInfoReturnable<Boolean> cir) {
		// Bundle-style drip: right-click a carried pile onto an empty slot → drop the active wood there.
		if (action == ClickAction.SECONDARY && slot.getItem().isEmpty() && VariantPiles.isPile(carried)) {
			Item active = VariantPiles.activeWood(carried);
			ItemStack probe = active == null ? ItemStack.EMPTY : new ItemStack(active);
			if (active != null && slot.mayPlace(probe)) {
				int take = Math.min(slot.getMaxStackSize(probe), VariantPiles.countOf(carried, active));
				ItemStack removed = VariantPiles.removeWood(carried, active, take);
				if (!removed.isEmpty()) {
					slot.safeInsert(removed); // take <= slot capacity, into an empty slot → fully placed
					cir.setReturnValue(true);
					return;
				}
			}
		}
		if (DragMerge.tryMerge(slot, carried, action)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
	private void mythstack$pileExtract(ItemStack self, ItemStack other, Slot slot, ClickAction action,
			Player player, SlotAccess carriedAccess, CallbackInfoReturnable<Boolean> cir) {
		// Bundle-style extract: right-click a pile in a slot with an empty cursor → active wood to cursor.
		if (action == ClickAction.SECONDARY && other.isEmpty() && VariantPiles.isPile(self)
				&& slot.allowModification(player)) {
			ItemStack removed = VariantPiles.removeSelectedStack(self);
			if (!removed.isEmpty()) {
				carriedAccess.set(removed);
				cir.setReturnValue(true);
			}
		}
	}
}
