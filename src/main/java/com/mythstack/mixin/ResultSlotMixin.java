package com.mythstack.mixin;

import com.mythstack.craft.CraftMenuTransmute;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Single-take of a transmuted craft result (plan §7 phase 3): the player already holds the previewed
 * product, so we consume the wood for that one craft and advance — instead of vanilla canonical-first.
 * Server-authoritative; falls through to vanilla for non-transmutable crafts.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

	@Shadow
	@Final
	private CraftingContainer craftSlots;

	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
	private void mythstack$singleCraft(Player player, ItemStack carried, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer && CraftMenuTransmute.trySingleCraft(
				serverPlayer.containerMenu, this.craftSlots, serverPlayer)) {
			ci.cancel();
		}
	}
}
