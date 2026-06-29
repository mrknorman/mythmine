package com.mythstack.client.mixin;

import com.mythstack.interaction.PileSeparation;
import com.mythstack.variant.VariantPiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * Makes the in-drag preview of a pile match the eventual even split. Vanilla draws the whole carried
 * pile (with a fractional count) in every dragged slot, so the icons only "settle" into their real
 * shares when you release. We redirect that preview to each slot's actual round-robin share instead.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Shadow
	@Final
	protected Set<Slot> quickCraftSlots;

	@Redirect(method = "extractSlot",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack mythstack$pileDragPreview(ItemStack carried, int newCount,
			GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		if (VariantPiles.isPile(carried)) {
			return PileSeparation.dragPreviewShare(carried, this.quickCraftSlots, slot);
		}
		return carried.copyWithCount(newCount);
	}
}
