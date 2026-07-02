package com.mythstack.menu;

import com.mythstack.registry.ModBlocks;
import com.mythstack.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.StonecutterMenu;

/**
 * The sawmill menu IS the stonecutter menu over the {@code sawing} recipe set (swapped in by
 * {@code StonecutterMenuMixin}) and valid over the sawmill block instead of the stonecutter.
 */
public class SawmillMenu extends StonecutterMenu {

	private final ContainerLevelAccess sawmillAccess;

	public SawmillMenu(int containerId, Inventory inventory) {
		super(containerId, inventory);
		this.sawmillAccess = ContainerLevelAccess.NULL;
	}

	public SawmillMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(containerId, inventory, access);
		this.sawmillAccess = access;
	}

	@Override
	public MenuType<?> getType() {
		return ModMenus.SAWMILL;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.sawmillAccess, player, ModBlocks.SAWMILL);
	}
}
