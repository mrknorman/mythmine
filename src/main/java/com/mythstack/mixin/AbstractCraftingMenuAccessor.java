package com.mythstack.mixin;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the (protected) craft grid shared by {@code CraftingMenu} and {@code InventoryMenu}. */
@Mixin(AbstractCraftingMenu.class)
public interface AbstractCraftingMenuAccessor {

	@Accessor("craftSlots")
	CraftingContainer mythstack$craftSlots();
}
