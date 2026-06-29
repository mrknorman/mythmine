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
 * Draws a pile icon as up to 3 of the contained woods, fanned (first wood on top). Each wood is drawn
 * via its own <em>fresh, single-layer</em> {@link ItemStackRenderState}, submitted at a per-slot pose
 * offset — so it never feeds the shared, variable-length layer pool of the carrier's render state.
 *
 * <p>Deliberately a {@code SpecialModelRenderer} rather than a declarative {@code composite}/{@code
 * select} model: the latter tripped a vanilla {@code ItemStackRenderState.clear()} stale-layer bug.
 * Drawing imperatively here avoids that entire class of bug and gives exact transform control.
 * Reusable for every future pile form.
 */
public class PileSpecialRenderer implements SpecialModelRenderer<List<ItemStack>> {

	private static final int MAX_LAYERS = 3;

	/**
	 * Per-layer fan: {translateX, translateY, translateZ, scale}, drawn back-to-front so slot 0 (the
	 * first wood) lands on top. Offsets are in the special-layer pose space (small — large values throw
	 * the logs off-slot); tunable.
	 */
	private static final float[][] SLOTS = {
			{ 0.022f, 0.028f, 0.02f, 0.58f },    // slot 0: upper-right, front / top
			{ -0.022f, 0.0f, 0.0f, 0.58f },      // slot 1: left, middle
			{ 0.022f, -0.028f, -0.02f, 0.58f },  // slot 2: lower-right, back / bottom
	};

	@Override
	public List<ItemStack> extractArgument(ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return null;
		}
		int count = Math.min(MAX_LAYERS, pile.contents().size());
		List<ItemStack> stacks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			stacks.add(new ItemStack(pile.contents().get(i).item()));
		}
		return stacks;
	}

	@Override
	public void submit(List<ItemStack> stacks, PoseStack pose, SubmitNodeCollector collector,
			int light, int overlay, boolean hasFoil, int outlineColor) {
		if (stacks == null || stacks.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ItemModelResolver resolver = minecraft.getItemModelResolver();
		// Back-to-front so slot 0 (the first wood) draws last / on top.
		for (int i = stacks.size() - 1; i >= 0; i--) {
			float[] slot = SLOTS[i];
			ItemStackRenderState layer = new ItemStackRenderState();
			resolver.updateForTopItem(layer, stacks.get(i), ItemDisplayContext.GUI, minecraft.level, null, 0);
			pose.pushPose();
			pose.translate(slot[0], slot[1], slot[2]);
			pose.scale(slot[3], slot[3], slot[3]);
			layer.submit(pose, collector, light, overlay, outlineColor);
			pose.popPose();
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(-0.5f, -0.5f, -0.5f));
		output.accept(new Vector3f(0.5f, 0.5f, 0.5f));
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<List<ItemStack>> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public SpecialModelRenderer<List<ItemStack>> bake(SpecialModelRenderer.BakingContext context) {
			return new PileSpecialRenderer();
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
