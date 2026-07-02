package com.mythstack.mixin;

import com.mythstack.craft.CraftTransmute;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The crafter UI's ghost result shows the transmuted product for a wood grid — the same preview the
 * crafting table's result slot gets, so what the screen promises is what a pulse dispenses.
 */
@Mixin(CrafterMenu.class)
public abstract class CrafterMenuMixin {

	@Shadow
	@Final
	private ResultContainer resultContainer;

	@Shadow
	@Final
	private CraftingContainer container;

	@Shadow
	@Final
	private Player player;

	@Inject(method = "refreshRecipeResult", at = @At("TAIL"))
	private void mythstack$transmutePreview(CallbackInfo ci) {
		if (this.player instanceof ServerPlayer serverPlayer) {
			ItemStack preview = CraftTransmute.previewProduct(this.container.getItems(),
					this.container.getWidth(), this.container.getHeight(), serverPlayer.level());
			if (!preview.isEmpty()) {
				this.resultContainer.setItem(0, preview);
			}
		}
	}
}
