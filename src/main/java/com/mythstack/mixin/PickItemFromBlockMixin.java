package com.mythstack.mixin;

import com.mythstack.variant.VariantPiles;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Middle-click (pick-block) on a placed wood whose variant is locked inside a pile. Vanilla's
 * {@code tryPickItem} matches the exact item, so a wood that only exists inside a pile (hosted on
 * {@code oak_log}) is invisible to it and the pick does nothing. We fill that gap: when there's no loose
 * stack of the clicked wood, hand over a pile that contains it — set to <em>place that wood</em> (its
 * active variant), so pick-block on jungle gives you the pile already pointed at jungle (matching the
 * icon + the placement path). We don't cancel; vanilla's tail still sends the held-slot + broadcasts.
 *
 * <p>Creative is left to vanilla (it mints the raw block), as is the case where a loose stack exists.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PickItemFromBlockMixin {

	@Shadow
	public ServerPlayer player;

	@Inject(method = "tryPickItem", at = @At("HEAD"))
	private void mythstack$pickWoodFromPile(ItemStack itemStack, CallbackInfo ci) {
		ServerPlayer serverPlayer = this.player;
		if (serverPlayer.hasInfiniteMaterials()) {
			return; // creative hands over the raw block — leave it to vanilla
		}
		Inventory inventory = serverPlayer.getInventory();
		if (inventory.findSlotMatchingItem(itemStack) != -1) {
			return; // a loose stack of this wood exists — vanilla picks that
		}
		Item wood = itemStack.getItem();
		NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (VariantPiles.isPile(stack) && VariantPiles.countOf(stack, wood) > 0) {
				VariantPiles.seed(stack, wood); // hand it over set to place this wood
				if (Inventory.isHotbarSlot(i)) {
					inventory.setSelectedSlot(i);
				} else {
					inventory.pickSlot(i);
				}
				return;
			}
		}
	}
}
