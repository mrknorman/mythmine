package com.mythstack.net;

import com.mythstack.MythStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * C2S: the player scrolled over the pile in slot {@code slotIndex} of the menu with id
 * {@code containerId} to make {@code wood} its active variant. The server validates that the slot
 * still holds a pile containing {@code wood} and updates it — the client doesn't predict, it waits for
 * the synced slot (server-authoritative, matching the rest of the pile interactions).
 */
public record SelectVariantPayload(int containerId, int slotIndex, Item wood) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SelectVariantPayload> TYPE =
			new CustomPacketPayload.Type<>(MythStack.id("select_variant"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectVariantPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, SelectVariantPayload::containerId,
					ByteBufCodecs.VAR_INT, SelectVariantPayload::slotIndex,
					ByteBufCodecs.registry(Registries.ITEM), SelectVariantPayload::wood,
					SelectVariantPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
