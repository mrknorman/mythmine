package com.mythstack.interaction;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The shared "typed station keeps its menu open" rule: vanilla's exact validity (block match +
 * interaction range) first, then the same rule per typed variant. Not a mixin — a helper the
 * station-menu redirects share.
 */
public final class TypedStationValidity {
	private TypedStationValidity() {
	}

	public static boolean stillValid(ContainerLevelAccess access, Player player, Block vanilla, List<Block> typed) {
		if (ValidityInvoker.check(access, player, vanilla)) {
			return true;
		}
		for (Block block : typed) {
			if (ValidityInvoker.check(access, player, block)) {
				return true;
			}
		}
		return false;
	}

	/** Bridges to the protected static {@code AbstractContainerMenu.stillValid}. */
	abstract static class ValidityInvoker extends net.minecraft.world.inventory.AbstractContainerMenu {
		private ValidityInvoker() {
			super(null, 0);
		}

		static boolean check(ContainerLevelAccess access, Player player, Block block) {
			return net.minecraft.world.inventory.AbstractContainerMenu.stillValid(access, player, block);
		}
	}
}
