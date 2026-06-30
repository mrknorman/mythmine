package com.mythstack.mixin;

import com.mythstack.craft.CraftMenuTransmute;
import com.mythstack.craft.CraftTransmute;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ratio crafting wiring (plan §7 phase 3):
 * <ul>
 *   <li>the static {@code slotChangedCraftingGrid} (shared by the table and the 2×2) sets the result slot
 *       to the transmuted preview, so a mixed/non-oak grid that matches no vanilla recipe still becomes
 *       craftable, and the icon shows the real next wood;</li>
 *   <li>shift-click on the result mass-crafts the whole ratio plan.</li>
 * </ul>
 * Server-authoritative; non-wood / non-transmutable crafts fall through to vanilla. {@code InventoryMenu}'s
 * own shift-click is hooked by {@link InventoryMenuMixin}; single-take by {@link ResultSlotMixin}.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

	@Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
	private static void mythstack$transmutePreview(AbstractContainerMenu menu, ServerLevel level, Player player,
			CraftingContainer container, ResultContainer resultSlots, RecipeHolder<CraftingRecipe> recipeHint,
			CallbackInfo ci) {
		ItemStack preview = CraftTransmute.previewProduct(
				container.getItems(), container.getWidth(), container.getHeight(), level);
		if (!preview.isEmpty()) {
			resultSlots.setItem(0, preview);
			menu.broadcastChanges();
		}
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mythstack$massCraft(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex == 0 && player instanceof ServerPlayer serverPlayer && CraftMenuTransmute.tryMassCraft(
				(AbstractContainerMenu) (Object) this,
				((AbstractCraftingMenuAccessor) this).mythstack$craftSlots(), serverPlayer)) {
			cir.setReturnValue(ItemStack.EMPTY); // handled — stop the vanilla shift-click loop
		}
	}
}
