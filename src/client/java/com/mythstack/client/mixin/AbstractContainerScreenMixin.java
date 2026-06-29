package com.mythstack.client.mixin;

import com.mythstack.interaction.PileSeparation;
import com.mythstack.net.SelectVariantPayload;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * Client-side pile UX on the container screen:
 * <ul>
 *   <li>The in-drag preview of a pile matches the eventual even split. Vanilla draws the whole carried
 *   pile (with a fractional count) in every dragged slot, so the icons only "settle" into their real
 *   shares on release; we redirect that preview to each slot's actual share instead.</li>
 *   <li>Scrolling over a pile slot cycles its <em>active</em> wood, sent to the server to apply.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Shadow
	@Final
	protected Set<Slot> quickCraftSlots;

	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Shadow
	protected Slot hoveredSlot;

	@Redirect(method = "extractSlot",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack mythstack$pileDragPreview(ItemStack carried, int newCount,
			GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		if (VariantPiles.isPile(carried)) {
			return PileSeparation.dragPreviewShare(carried, this.quickCraftSlots, slot);
		}
		return carried.copyWithCount(newCount);
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void mythstack$scrollSelectVariant(double mouseX, double mouseY, double scrollX, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (this.hoveredSlot == null || scrollY == 0) {
			return;
		}
		VariantPile pile = this.hoveredSlot.getItem().get(ModComponents.VARIANT_PILE);
		if (pile == null || pile.contents().size() < 2) {
			return; // nothing (or only one wood) to cycle through
		}
		List<VariantPile.Entry> contents = pile.contents();
		int size = contents.size();
		int current = -1;
		if (pile.selected().isPresent()) {
			Item active = pile.selected().get();
			for (int i = 0; i < size; i++) {
				if (contents.get(i).item() == active) {
					current = i;
					break;
				}
			}
		}
		int dir = scrollY > 0 ? 1 : -1; // wheel up advances to the next wood
		int next = current < 0 ? (dir > 0 ? 0 : size - 1) : Math.floorMod(current + dir, size);
		Item wood = contents.get(next).item();
		// Update the client's stack immediately so the icon/highlight track the wheel with no round-trip,
		// then tell the server (which re-broadcasts the same selection). Mirrors BundleMouseActions.
		VariantPiles.seed(this.hoveredSlot.getItem(), wood);
		ClientPlayNetworking.send(new SelectVariantPayload(this.menu.containerId, this.hoveredSlot.index, wood));
		cir.setReturnValue(true); // consume the scroll so it doesn't fall through to vanilla
	}
}
