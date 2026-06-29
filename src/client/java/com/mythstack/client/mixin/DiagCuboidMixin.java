package com.mythstack.client.mixin;

import com.mythstack.MythStack;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TEMPORARY DIAGNOSTIC. Logs the GUI display transform actually applied to each pile layer, gated to
 * pile stacks in the GUI context. Compares oak-present vs oak-absent piles to prove whether the
 * display-scale path is wood-dependent. Remove once the bug is found.
 */
@Mixin(CuboidItemModelWrapper.class)
public class DiagCuboidMixin {

	@Shadow
	@Final
	private ModelRenderProperties properties;

	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	@Inject(method = "update", at = @At("TAIL"))
	private void mythstack$diag(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
			ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed, CallbackInfo ci) {
		if (displayContext != ItemDisplayContext.GUI) {
			return;
		}
		VariantPile pile = item.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return;
		}
		boolean hasOak = pile.contents().stream().anyMatch(entry -> entry.item() == Items.OAK_LOG);
		ItemTransform transform = this.properties.transforms().getTransform(ItemDisplayContext.GUI);
		String key = hasOak + "|" + transform.scale() + "|" + transform.translation();
		if (SEEN.add(key)) {
			MythStack.LOGGER.info("[diag] hasOak={} guiScale={} guiTranslation={}",
					hasOak, transform.scale(), transform.translation());
		}
	}
}
