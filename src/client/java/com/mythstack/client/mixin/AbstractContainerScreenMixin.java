package com.mythstack.client.mixin;

import com.mythstack.interaction.PileSeparation;
import com.mythstack.net.SelectVariantPayload;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
		// then tell the server to apply the same selection to its copy. Mirrors BundleMouseActions.
		VariantPiles.seed(this.hoveredSlot.getItem(), wood);
		int index = mythstack$selectionSlotIndex();
		if (index >= 0) {
			ClientPlayNetworking.send(new SelectVariantPayload(index, wood));
		}
		cir.setReturnValue(true); // consume the scroll so it doesn't fall through to vanilla
	}

	/**
	 * The menu-slot index the server should seed. Normally the hovered slot's own index — the screen's menu
	 * is the server's {@code containerMenu}, so they agree. The creative inventory screen is the exception:
	 * it wraps the real inventory slots in its own menu while the server still sees the plain
	 * {@code inventoryMenu}, so we resolve the matching {@code inventoryMenu} index by stack identity (the
	 * wrapper shares the underlying ItemStack). Returns {@code -1} if it can't be resolved — better to seed
	 * nothing than the wrong slot.
	 */
	@Unique
	private int mythstack$selectionSlotIndex() {
		if (!(((Object) this) instanceof CreativeModeInventoryScreen)) {
			return this.hoveredSlot.index;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return -1;
		}
		ItemStack target = this.hoveredSlot.getItem();
		AbstractContainerMenu inventoryMenu = player.inventoryMenu;
		for (int i = 0; i < inventoryMenu.slots.size(); i++) {
			if (inventoryMenu.slots.get(i).getItem() == target) {
				return i;
			}
		}
		return -1;
	}
}
