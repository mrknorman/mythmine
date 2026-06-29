package com.mythstack.client.mixin;

import com.mythstack.MythStack;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TEMPORARY, READ-ONLY DIAGNOSTIC. At model-bake time (resource load), logs the resolved GUI transform
 * of each pile slot model. Does not change rendering at all. Tells us whether our display.gui override
 * is present at BAKE (so the bug is model resolution) or lost only at render (so it's caching).
 */
@Mixin(ModelRenderProperties.class)
public class DiagBakeMixin {

	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	@Inject(method = "fromResolvedModel", at = @At("HEAD"))
	private static void mythstack$diagBake(ModelBaker baker, ResolvedModel resolvedModel, TextureSlots textureSlots,
			CallbackInfoReturnable<ModelRenderProperties> cir) {
		String name = resolvedModel.debugName();
		if (name == null || !name.contains("pile")) {
			return;
		}
		ItemTransform gui = resolvedModel.getTopTransforms().getTransform(ItemDisplayContext.GUI);
		if (SEEN.add(name)) {
			MythStack.LOGGER.info("[bake] {} -> guiScale={} guiTranslation={}", name, gui.scale(), gui.translation());
		}
	}
}
