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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws a pile icon as up to 3 of the contained woods, arranged by how many there are. Each wood is
 * drawn via its own <em>fresh, single-layer</em> {@link ItemStackRenderState}, submitted at a per-slot
 * pose offset — so it never feeds the shared, variable-length layer pool of the carrier's render state.
 *
 * <p>Deliberately a {@code SpecialModelRenderer} rather than a declarative {@code composite}/{@code
 * select} model: the latter tripped a vanilla {@code ItemStackRenderState.clear()} stale-layer bug.
 * Drawing imperatively here avoids that entire class of bug and gives exact transform control.
 *
 * <p>Each log is centred programmatically: its measured bounding-box centre is placed at the slot
 * centre (block-space {@code 0.5,0.5,0.5}), then offset for the fan. Layout (scale + offsets) is chosen
 * by the number of woods so 2-wood piles read as a bigger pair and 3-wood piles as a zig-zag fan.
 */
public class PileSpecialRenderer implements SpecialModelRenderer<List<ItemStack>> {

	private static final int MAX_LAYERS = 3;
	private static final float CENTER = 0.5f; // slot centre in the base model's block space

	// --- 3-wood layout: zig-zag fan (first wood up, middle kicked left, last down). ---
	private static final float SCALE_3 = 0.6f;
	private static final float[][] OFFSETS_3 = {
			{ 0.17f, 0.34f, 0.12f },    // slot 0: upper-right, front / top
			{ -0.26f, 0.0f, 0.0f },     // slot 1: left, middle
			{ 0.17f, -0.34f, -0.12f },  // slot 2: lower-right, back / bottom
	};

	// --- 2-wood layout: a bigger diagonal pair (first wood front/upper, second behind/lower). ---
	private static final float SCALE_2 = 0.72f;
	private static final float[][] OFFSETS_2 = {
			{ 0.14f, 0.20f, 0.10f },    // slot 0: upper-right, front / top
			{ -0.14f, -0.20f, -0.10f }, // slot 1: lower-left, back
	};

	// --- 1-wood layout (rare non-host remainder): a single centred log, larger still. ---
	private static final float SCALE_1 = 0.85f;
	private static final float[][] OFFSETS_1 = {
			{ 0.0f, 0.0f, 0.0f },
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
		int count = stacks.size();
		float scale = scaleFor(count);
		float[][] offsets = offsetsFor(count);
		// Back-to-front so slot 0 (the first wood) draws last / on top. Each log is raw block geometry
		// (NONE context); we measure its bounding box and put its centre at the slot centre + fan offset.
		for (int i = count - 1; i >= 0; i--) {
			float[] off = offsets[i];
			ItemStackRenderState layer = new ItemStackRenderState();
			resolver.updateForTopItem(layer, stacks.get(i), ItemDisplayContext.NONE, minecraft.level, null, 0);
			AABB box = layer.getModelBoundingBox();
			Vec3 center = box.getCenter();
			pose.pushPose();
			pose.translate(CENTER + off[0], CENTER + off[1], CENTER + off[2]);
			pose.scale(scale, scale, scale);
			pose.translate(-center.x, -center.y, -center.z);
			layer.submit(pose, collector, light, overlay, outlineColor);
			pose.popPose();
		}
	}

	private static float scaleFor(int count) {
		if (count >= 3) {
			return SCALE_3;
		}
		return count == 2 ? SCALE_2 : SCALE_1;
	}

	private static float[][] offsetsFor(int count) {
		if (count >= 3) {
			return OFFSETS_3;
		}
		return count == 2 ? OFFSETS_2 : OFFSETS_1;
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
