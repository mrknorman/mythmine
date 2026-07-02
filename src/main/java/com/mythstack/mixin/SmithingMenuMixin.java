package com.mythstack.mixin;

import com.mythstack.registry.ModBlocks;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Typed smithing tables keep their menu open — {@code isValidBlock} names THE smithing table. */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {

	@Inject(method = "isValidBlock", at = @At("RETURN"), cancellable = true)
	private void mythstack$typedTableIsValid(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && ModBlocks.TYPED_SMITHING_TABLES.contains(state.getBlock())) {
			cir.setReturnValue(true);
		}
	}
}
