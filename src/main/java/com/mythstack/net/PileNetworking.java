package com.mythstack.net;

import com.mythstack.variant.VariantPiles;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Common-side networking for pile interactions. Registers the C2S {@link SelectVariantPayload} codec and
 * its server receiver, which applies the scrolled-to active variant to the player's open menu — the
 * direct analogue of vanilla {@code AbstractContainerMenu.setSelectedBundleItemIndex}: mutate the slot
 * stack's component in place, no slot re-set and no broadcast (the client already updated its own copy,
 * and the selection rides with the stack from then on).
 */
public final class PileNetworking {
	private PileNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SelectVariantPayload.TYPE, SelectVariantPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectVariantPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> apply(player, payload));
		});
	}

	private static void apply(ServerPlayer player, SelectVariantPayload payload) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu == null) {
			return;
		}
		int index = payload.slotIndex();
		if (index < 0 || index >= menu.slots.size()) {
			return;
		}
		ItemStack stack = menu.slots.get(index).getItem();
		VariantPiles.seed(stack, payload.wood()); // mutate in place; no-op if the wood isn't in this pile
	}
}
