package com.mythstack.net;

import com.mythstack.MythStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * C2S: the player scrolled over the pile in slot {@code slotIndex} of their open menu to make
 * {@code wood} its active variant. Mirrors vanilla's {@code ServerboundSelectBundleItemPacket} (slot +
 * selection, no container id): the server applies it to {@code player.containerMenu} and the client has
 * already updated its own copy, so no re-broadcast is needed (see {@link PileNetworking}).
 */
public record SelectVariantPayload(int slotIndex, Item wood) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SelectVariantPayload> TYPE =
			new CustomPacketPayload.Type<>(MythStack.id("select_variant"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectVariantPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, SelectVariantPayload::slotIndex,
					ByteBufCodecs.registry(Registries.ITEM), SelectVariantPayload::wood,
					SelectVariantPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
