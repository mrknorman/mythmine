package com.mythstack.dev;

import com.mythstack.MythStack;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.List;

/**
 * Dev-only end-to-end test of pile-aware furnaces (plan phase 8): a REAL furnace block entity in the
 * self-test world, ticked via the actual {@link AbstractFurnaceBlockEntity#serverTick} — the same path
 * a live chunk drives. Verifies per-element smelting/fuel, smallest-first order, burn-down to
 * unburnable remainders, and the fuel-slot extraction QOL.
 */
public final class FurnaceSelfTest {
	private static final BlockPos POS = new BlockPos(0, 200, 0);
	private static final int SLOT_INPUT = 0;
	private static final int SLOT_FUEL = 1;
	private static final int SLOT_RESULT = 2;

	private FurnaceSelfTest() {
	}

	/** Runs all furnace checks; logs PASS/FAIL per check and returns the failure count. */
	public static int run(ServerLevel level) {
		int[] failures = {0};

		// A. Per-element smelting, smallest-first: {oak1, spruce2} logs -> oak smelts first.
		AbstractFurnaceBlockEntity furnace = freshFurnace(level);
		furnace.setItem(SLOT_INPUT, logPile(new ItemStack(Items.OAK_LOG, 1), new ItemStack(Items.SPRUCE_LOG, 2)));
		furnace.setItem(SLOT_FUEL, new ItemStack(Items.COAL, 1)); // 1600 ticks = 8 smelts of headroom
		tick(level, furnace, 205);
		ItemStack input = furnace.getItem(SLOT_INPUT);
		check("furnace: first smelt eats the smallest wood (oak), pile collapses to plain spruce x2",
				!VariantPiles.isPile(input) && input.getItem() == Items.SPRUCE_LOG && input.getCount() == 2, failures);
		check("furnace: one charcoal out", count(furnace, SLOT_RESULT, Items.CHARCOAL) == 1, failures);
		tick(level, furnace, 405);
		check("furnace: the rest smelts through (3 charcoal, empty input)",
				count(furnace, SLOT_RESULT, Items.CHARCOAL) == 3 && furnace.getItem(SLOT_INPUT).isEmpty(), failures);

		// B. Smelt-down remainder: {oak2, crimson3} -> 2 charcoal, then idles on plain crimson x3.
		furnace = freshFurnace(level);
		furnace.setItem(SLOT_INPUT, logPile(new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.CRIMSON_STEM, 3)));
		furnace.setItem(SLOT_FUEL, new ItemStack(Items.COAL, 1));
		tick(level, furnace, 500);
		input = furnace.getItem(SLOT_INPUT);
		check("furnace: pile smelts down to its unsmeltable remainder (crimson x3 idle, 2 charcoal)",
				count(furnace, SLOT_RESULT, Items.CHARCOAL) == 2 && !VariantPiles.isPile(input)
						&& input.getItem() == Items.CRIMSON_STEM && input.getCount() == 3, failures);

		// C. Fuel burn-down: fuel pile {spruce1, crimson2} burns its spruce (300 ticks = 1 smelt + a bit),
		//    then goes cold on the unburnable remainder.
		furnace = freshFurnace(level);
		furnace.setItem(SLOT_INPUT, new ItemStack(Items.OAK_LOG, 3));
		furnace.setItem(SLOT_FUEL, logPile(new ItemStack(Items.SPRUCE_LOG, 1), new ItemStack(Items.CRIMSON_STEM, 2)));
		tick(level, furnace, 350);
		ItemStack fuel = furnace.getItem(SLOT_FUEL);
		check("furnace: fuel pile burns down to plain crimson x2",
				!VariantPiles.isPile(fuel) && fuel.getItem() == Items.CRIMSON_STEM && fuel.getCount() == 2, failures);
		check("furnace: spruce's 300 ticks smelted exactly one log, then the furnace went cold",
				count(furnace, SLOT_RESULT, Items.CHARCOAL) == 1
						&& !level.getBlockState(POS).getValue(AbstractFurnaceBlock.LIT), failures);

		// D. A pile with nothing burnable never ignites.
		furnace = freshFurnace(level);
		furnace.setItem(SLOT_INPUT, new ItemStack(Items.OAK_LOG, 1));
		furnace.setItem(SLOT_FUEL, logPile(new ItemStack(Items.CRIMSON_STEM, 1), new ItemStack(Items.WARPED_STEM, 1)));
		tick(level, furnace, 20);
		check("furnace: an all-nether fuel pile never ignites (fuel untouched)",
				!level.getBlockState(POS).getValue(AbstractFurnaceBlock.LIT)
						&& VariantPiles.isPile(furnace.getItem(SLOT_FUEL))
						&& furnace.getItem(SLOT_FUEL).getCount() == 2, failures);

		// D2. Ore families: a mixed iron-ore pile (stone + deepslate) smelts per element — each
		//     variant's own recipe — down to plain iron ingots.
		furnace = freshFurnace(level);
		furnace.setItem(SLOT_INPUT, VariantPiles.makeStacks(VariantGroups.IRON_ORES,
				VariantPiles.pool(VariantGroups.IRON_ORES, List.of(
						new ItemStack(Items.IRON_ORE, 2), new ItemStack(Items.DEEPSLATE_IRON_ORE, 2)))).get(0));
		furnace.setItem(SLOT_FUEL, new ItemStack(Items.COAL, 1));
		tick(level, furnace, 850);
		check("furnace: a mixed iron-ore pile smelts down to 4 ingots (per-element recipes)",
				count(furnace, SLOT_RESULT, Items.IRON_INGOT) == 4
						&& furnace.getItem(SLOT_INPUT).isEmpty(), failures);

		// E. Fuel-slot extraction QOL: unburnable remainders may leave through the bottom face.
		check("furnace: hopper may extract the unburnable remainder from the fuel slot",
				furnace.canTakeItemThroughFace(SLOT_FUEL, new ItemStack(Items.CRIMSON_STEM, 2), Direction.DOWN), failures);
		check("furnace: burnable fuel still may NOT be extracted (vanilla rule intact)",
				!furnace.canTakeItemThroughFace(SLOT_FUEL, new ItemStack(Items.OAK_LOG, 2), Direction.DOWN)
						&& !furnace.canTakeItemThroughFace(SLOT_FUEL,
								logPile(new ItemStack(Items.OAK_LOG, 1), new ItemStack(Items.CRIMSON_STEM, 1)), Direction.DOWN),
				failures);

		// F. A typed furnace is a REAL furnace: the granite furnace shares the vanilla block entity
		//    (widened validBlocks) and smelts through the same tick path.
		level.setBlock(POS, Blocks.AIR.defaultBlockState(), 3);
		net.minecraft.world.level.block.Block graniteFurnace =
				net.minecraft.core.registries.BuiltInRegistries.BLOCK
						.getValue(MythStack.id("granite_furnace"));
		level.setBlock(POS, graniteFurnace.defaultBlockState(), 3);
		AbstractFurnaceBlockEntity typed = (AbstractFurnaceBlockEntity) level.getBlockEntity(POS);
		check("furnace: the granite furnace hosts the vanilla furnace block entity", typed != null, failures);
		if (typed != null) {
			typed.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE, 1));
			typed.setItem(SLOT_FUEL, new ItemStack(Items.COAL, 1));
			tick(level, typed, 205);
			check("furnace: the granite furnace smelts cobblestone to stone",
					count(typed, SLOT_RESULT, Items.STONE) == 1, failures);
		}

		level.setBlock(POS, Blocks.AIR.defaultBlockState(), 3);
		return failures[0];
	}

	private static AbstractFurnaceBlockEntity freshFurnace(ServerLevel level) {
		level.setBlock(POS, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(POS, Blocks.FURNACE.defaultBlockState(), 3);
		return (AbstractFurnaceBlockEntity) level.getBlockEntity(POS);
	}

	/** Drive the real block-entity tick, re-reading the (LIT-toggling) block state each tick. */
	private static void tick(ServerLevel level, AbstractFurnaceBlockEntity furnace, int ticks) {
		for (int i = 0; i < ticks; i++) {
			AbstractFurnaceBlockEntity.serverTick(level, POS, level.getBlockState(POS), furnace);
		}
	}

	private static ItemStack logPile(ItemStack... stacks) {
		return VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS, List.of(stacks))).get(0);
	}

	private static int count(AbstractFurnaceBlockEntity furnace, int slot, net.minecraft.world.item.Item item) {
		ItemStack stack = furnace.getItem(slot);
		return stack.getItem() == item ? stack.getCount() : 0;
	}

	private static void check(String name, boolean ok, int[] failures) {
		if (ok) {
			MythStack.LOGGER.info("[selftest] PASS  {}", name);
		} else {
			MythStack.LOGGER.error("[selftest] FAIL  {}", name);
			failures[0]++;
		}
	}
}
