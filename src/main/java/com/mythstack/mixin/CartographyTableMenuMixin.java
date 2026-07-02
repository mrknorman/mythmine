package com.mythstack.mixin;

import com.mythstack.interaction.TypedStationValidity;
import com.mythstack.registry.ModBlocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Typed cartography tables keep their menu open — same hardcoded-block validity as the loom. */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin {

	@Redirect(method = "stillValid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/CartographyTableMenu;stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z"))
	private boolean mythstack$typedTableStillValid(ContainerLevelAccess access, Player player, Block block) {
		return TypedStationValidity.stillValid(access, player, block, ModBlocks.TYPED_CARTOGRAPHY_TABLES);
	}
}
