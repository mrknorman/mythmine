package com.mythstack.mixin;

import com.mythstack.interaction.PileRecipeBook;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recipe-book availability counts a pile as its CONTENTS, not its host — a {oak,spruce} pile makes
 * spruce recipes craftable and stops overcounting oak. This is the choke point every inventory
 * accounting pass runs through (player inventory, both sides), so the book, canCraft, and
 * biggest-craftable-stack all see the true composition.
 */
@Mixin(StackedItemContents.class)
public abstract class StackedItemContentsMixin {

	@Inject(method = "accountSimpleStack", at = @At("HEAD"), cancellable = true)
	private void mythstack$accountPileContents(ItemStack stack, CallbackInfo ci) {
		if (PileRecipeBook.accountPile((StackedItemContents) (Object) this, stack)) {
			ci.cancel();
		}
	}
}
