package com.mythstack.mixin;

import com.mythstack.variant.VariantPiles;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Placing a block from a pile places its <em>active</em> wood (matching the icon), not the canonical host.
 * Vanilla {@code BlockItem.useOn} would place {@code this}'s block (e.g. oak) and consume one item from
 * the held pile canonical-first. We instead place the active wood's block through a substitute one-count
 * stack — so vanilla's {@code consume(1)} spends the substitute, not the pile — then remove one of the
 * active wood from the real pile ourselves. Runs on client and server alike (deterministic given the
 * synced selection), so the prediction matches.
 */
@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void mythstack$placeActiveWood(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack pile = context.getItemInHand();
		if (!VariantPiles.isPile(pile)) {
			return;
		}
		Item active = VariantPiles.activeWood(pile);
		Player player = context.getPlayer();
		if (player == null || !(active instanceof BlockItem activeBlockItem)) {
			return; // non-placeable active wood (shouldn't happen for woods) — let vanilla place the host
		}
		ItemStack one = new ItemStack(active, 1);
		BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
				context.getClickedPos(), context.isInside());
		UseOnContext substitute = new UseOnContext(context.getLevel(), player, context.getHand(), one, hit);
		InteractionResult result = activeBlockItem.place(new BlockPlaceContext(substitute));
		if (result.consumesAction() && !player.getAbilities().instabuild) {
			VariantPiles.removeWood(pile, active, 1);
			ItemStack collapsed = VariantPiles.collapseToReal(pile);
			if (collapsed != pile) {
				player.setItemInHand(context.getHand(), collapsed);
			}
		}
		cir.setReturnValue(result);
	}
}
