package com.mythstack.net;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPiles;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Common-side networking for pile interactions. Registers the C2S {@link SelectVariantPayload} codec and
 * its server receiver, which applies the scrolled-to active variant authoritatively and re-syncs the slot.
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
		if (menu == null || menu.containerId != payload.containerId()) {
			return; // the open menu changed since the scroll — ignore the stale request
		}
		int index = payload.slotIndex();
		if (index < 0 || index >= menu.slots.size()) {
			return;
		}
		Slot slot = menu.slots.get(index);
		ItemStack stack = slot.getItem();
		if (stack.get(ModComponents.VARIANT_PILE) == null) {
			return; // not (or no longer) a pile
		}
		VariantPiles.seed(stack, payload.wood()); // no-op if the wood isn't in this pile — validates for us
		slot.set(stack);
		menu.broadcastChanges();
	}
}
