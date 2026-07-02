package com.mythstack.mixin;

import com.mythstack.interaction.TypedStationValidity;
import com.mythstack.registry.ModBlocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Typed looms keep their menu open — the validity check is hardcoded to THE loom block. */
@Mixin(LoomMenu.class)
public abstract class LoomMenuMixin {

	@Redirect(method = "stillValid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/LoomMenu;stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z"))
	private boolean mythstack$typedLoomStillValid(ContainerLevelAccess access, Player player, Block block) {
		return TypedStationValidity.stillValid(access, player, block, ModBlocks.TYPED_LOOMS);
	}
}
