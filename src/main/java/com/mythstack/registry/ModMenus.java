package com.mythstack.registry;

import com.mythstack.MythStack;
import com.mythstack.menu.SawmillMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Menu registry — the sawmill's own type, so clients construct {@link SawmillMenu} on open. */
public final class ModMenus {
	private ModMenus() {
	}

	public static final MenuType<SawmillMenu> SAWMILL = Registry.register(BuiltInRegistries.MENU,
			MythStack.id("sawmill"), new MenuType<>(SawmillMenu::new, FeatureFlags.VANILLA_SET));

	/** Called from {@link MythStack#onInitialize()} to force class-load so the statics register. */
	public static void initialize() {
	}
}
