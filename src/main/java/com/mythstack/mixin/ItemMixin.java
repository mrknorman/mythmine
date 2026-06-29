package com.mythstack.mixin;

import com.mythstack.interaction.DragMerge;
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
 * Hooks the vanilla "click a carried stack onto a slot" path so a member dropped onto a same-group
 * member drag-merges into a pile (spec §6) instead of swapping. {@code this} is the slot item;
 * {@code carried} is the cursor stack. Gated to same-group interactions in {@link DragMerge}.
 */
@Mixin(Item.class)
public class ItemMixin {

	@Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
	private void mythstack$dragMerge(ItemStack carried, Slot slot, ClickAction action, Player player,
			CallbackInfoReturnable<Boolean> cir) {
		if (DragMerge.tryMerge(slot, carried, action)) {
			cir.setReturnValue(true);
		}
	}
}
