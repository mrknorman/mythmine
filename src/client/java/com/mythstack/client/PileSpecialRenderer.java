package com.mythstack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws a pile icon as up to 3 of the contained woods, fanned. We render the variants ourselves each
 * frame (reading the live {@link VariantPile} component) instead of via the declarative model system,
 * which couldn't reliably do the composite. Reusable for every future pile form.
 *
 * <p>Pattern copied from vanilla block-entity item rendering (CampfireRenderer): build an
 * {@link ItemStackRenderState} per variant with {@code FIXED} display context, then submit each at an
 * offset. Plain variant stacks have no pile component, so they render as normal logs (no recursion).
 */
public class PileSpecialRenderer implements SpecialModelRenderer<List<ItemStackRenderState>> {

	/** Per-layer {translateX, translateY, translateZ, scale}. Drawn back-to-front. First-pass; tunable. */
	private static final float[][] SLOTS = {
			{ 0.18f, 0.18f, 0.02f, 0.5f },   // slot 0: first wood -> front / top
			{ -0.18f, 0.0f, 0.0f, 0.5f },    // slot 1: mid-left
			{ 0.18f, -0.18f, -0.02f, 0.5f }, // slot 2: back / bottom
	};

	@Override
	public List<ItemStackRenderState> extractArgument(ItemStack stack) {
		List<ItemStackRenderState> layers = new ArrayList<>();
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return layers;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ItemModelResolver resolver = minecraft.getItemModelResolver();
		int count = Math.min(SLOTS.length, pile.contents().size());
		for (int i = 0; i < count; i++) {
			ItemStackRenderState layer = new ItemStackRenderState();
			resolver.updateForTopItem(layer, new ItemStack(pile.contents().get(i).item()),
					ItemDisplayContext.FIXED, minecraft.level, null, 0);
			layers.add(layer);
		}
		return layers;
	}

	@Override
	public void submit(List<ItemStackRenderState> layers, PoseStack pose, SubmitNodeCollector collector,
			int light, int overlay, boolean hasFoil, int outlineColor) {
		// Back-to-front so slot 0 (the first wood) ends up drawn last / on top.
		for (int i = layers.size() - 1; i >= 0; i--) {
			float[] slot = SLOTS[i];
			pose.pushPose();
			pose.translate(slot[0], slot[1], slot[2]);
			pose.scale(slot[3], slot[3], slot[3]);
			layers.get(i).submit(pose, collector, light, overlay, outlineColor);
			pose.popPose();
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(-0.5f, -0.5f, -0.5f));
		output.accept(new Vector3f(0.5f, 0.5f, 0.5f));
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<List<ItemStackRenderState>> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public SpecialModelRenderer<List<ItemStackRenderState>> bake(SpecialModelRenderer.BakingContext context) {
			return new PileSpecialRenderer();
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
