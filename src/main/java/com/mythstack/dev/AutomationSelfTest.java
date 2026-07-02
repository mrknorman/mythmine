package com.mythstack.dev;

import com.mythstack.MythStack;
import com.mythstack.mixin.CrafterBlockInvoker;
import com.mythstack.registry.ModBlocks;
import com.mythstack.registry.ModItems;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPiles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Dev-only end-to-end test of the automation pass (plan phase 9): a REAL crafter pulsed through the
 * actual {@code dispenseFrom}, a REAL hopper driven through {@code pushItemsTick}, and the comparator
 * signal helper — the same paths live redstone drives.
 */
public final class AutomationSelfTest {
	private static final BlockPos CRAFTER = new BlockPos(4, 200, 0);
	private static final BlockPos HOPPER = new BlockPos(8, 201, 0);
	private static final BlockPos HOPPER_CHEST = new BlockPos(8, 200, 0);
	private static final BlockPos CHEST_A = new BlockPos(12, 200, 0);
	private static final BlockPos CHEST_B = new BlockPos(14, 200, 0);

	private AutomationSelfTest() {
	}

	/** Runs all automation checks; logs PASS/FAIL per check and returns the failure count. */
	public static int run(ServerLevel level) {
		int[] failures = {0};

		// 1. THE regression: a mixed plain grid in a crafter dispenses the transmuted product.
		CrafterBlockEntity crafter = freshCrafter(level);
		ChestBlockEntity out = outputChest(level); // dispenses go into the front container, not entities
		crafter.setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 1));
		crafter.setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 1));
		crafter.setItem(4, new ItemStack(Items.SPRUCE_PLANKS, 1));
		crafter.setItem(6, new ItemStack(Items.BIRCH_PLANKS, 1));
		crafter.setItem(7, new ItemStack(Items.BIRCH_PLANKS, 1));
		crafter.setItem(8, new ItemStack(Items.BIRCH_PLANKS, 1));
		pulse(level);
		LinkedHashMap<Item, Integer> ejected = drainChest(out);
		check("crafter: mixed plain stairs grid dispenses spruce stairs x4",
				ejected.getOrDefault(Items.SPRUCE_STAIRS, 0) == 4 && ejected.size() == 1, failures);
		check("crafter: the pulse consumed one plank from every slot",
				crafter.isEmpty(), failures);

		// 2. A pile crafts by its ACTIVE wood, not the oak host (the old canonicalize bug).
		crafter = freshCrafter(level);
		ItemStack pile = VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.BIRCH_LOG, 5), new ItemStack(Items.SPRUCE_LOG, 3)))).get(0);
		VariantPiles.seed(pile, Items.SPRUCE_LOG); // scrolled to spruce
		crafter.setItem(4, pile);
		pulse(level);
		ejected = drainChest(out);
		ItemStack remaining = crafter.getItem(4);
		check("crafter: a scrolled pile dispenses the ACTIVE wood's planks (spruce, not oak)",
				ejected.getOrDefault(Items.SPRUCE_PLANKS, 0) == 4 && ejected.size() == 1, failures);
		check("crafter: the pile drained by exactly one spruce ({birch5,spruce2})",
				VariantPiles.isPile(remaining) && VariantPiles.countOf(remaining, Items.SPRUCE_LOG) == 2
						&& VariantPiles.countOf(remaining, Items.BIRCH_LOG) == 5, failures);

		// 3. A non-transmutable mixed grid still fails vanilla-style, nothing consumed.
		crafter = freshCrafter(level);
		crafter.setItem(1, new ItemStack(Items.BAMBOO_SLAB, 1));
		crafter.setItem(4, new ItemStack(Items.OAK_SLAB, 1));
		pulse(level);
		ejected = drainChest(out);
		check("crafter: a non-transmutable mixed grid dispenses nothing and keeps its items",
				ejected.isEmpty() && crafter.getItem(1).getCount() == 1 && crafter.getItem(4).getCount() == 1, failures);
		level.setBlock(CRAFTER, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(out.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);

		// 4. Hoppers unmix piles: one item per transfer, peeled canonical-first into plain stacks.
		level.setBlock(HOPPER_CHEST, Blocks.CHEST.defaultBlockState(), 3);
		level.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState(), 3); // faces down into the chest
		HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(HOPPER);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(HOPPER_CHEST);
		hopper.setItem(0, VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.BIRCH_LOG, 2)))).get(0));
		for (int i = 0; i < 64; i++) {
			HopperBlockEntity.pushItemsTick(level, HOPPER, level.getBlockState(HOPPER), hopper);
		}
		check("hopper: the pile drained completely through the hopper", hopper.isEmpty(), failures);
		check("hopper: the chest received plain stacks, canonical wood first (oak x2 then birch x2)",
				!VariantPiles.isPile(chest.getItem(0)) && chest.getItem(0).getItem() == Items.OAK_LOG
						&& chest.getItem(0).getCount() == 2
						&& !VariantPiles.isPile(chest.getItem(1)) && chest.getItem(1).getItem() == Items.BIRCH_LOG
						&& chest.getItem(1).getCount() == 2, failures);
		level.setBlock(HOPPER, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(HOPPER_CHEST, Blocks.AIR.defaultBlockState(), 3);

		// 4b. Leaves drop TYPED sticks (loot overrides): roll spruce leaves until sticks appear —
		//     they must all be spruce sticks, never plain (2% per roll; 1200 rolls ≈ certainty).
		level.setBlock(CHEST_A, Blocks.SPRUCE_LEAVES.defaultBlockState(), 3);
		int typed = 0;
		int plain = 0;
		for (int i = 0; i < 1200; i++) {
			for (ItemStack drop : Block.getDrops(level.getBlockState(CHEST_A), level, CHEST_A, null)) {
				if (drop.getItem() == ModItems.SPRUCE_STICK) {
					typed += drop.getCount();
				} else if (drop.getItem() == Items.STICK) {
					plain += drop.getCount();
				}
			}
		}
		check("loot: spruce leaves drop spruce sticks, never plain (" + typed + " typed)",
				typed > 0 && plain == 0, failures);

		// 4c. Typed blocks: a placed typed chest gets a REAL working chest block entity (the widened
		//     vanilla type), and typed ladders/chests drop themselves.
		level.setBlock(CHEST_A, ModBlocks.TYPED_CHESTS.get(0).defaultBlockState(), 3);
		check("blocks: a typed chest hosts a working vanilla ChestBlockEntity",
				level.getBlockEntity(CHEST_A) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity,
				failures);
		java.util.List<ItemStack> chestDrops = Block.getDrops(level.getBlockState(CHEST_A), level, CHEST_A, null);
		level.setBlock(CHEST_A, ModBlocks.TYPED_LADDERS.get(0).defaultBlockState(), 3);
		java.util.List<ItemStack> ladderDrops = Block.getDrops(level.getBlockState(CHEST_A), level, CHEST_A, null);
		check("blocks: typed chests and ladders drop themselves",
				chestDrops.size() == 1 && chestDrops.get(0).getItem() == ModBlocks.TYPED_CHESTS.get(0).asItem()
						&& ladderDrops.size() == 1
						&& ladderDrops.get(0).getItem() == ModBlocks.TYPED_LADDERS.get(0).asItem(), failures);
		level.setBlock(CHEST_A, Blocks.AIR.defaultBlockState(), 3);

		// 5. Comparators read a pile exactly like the plain stack it stands in for.
		level.setBlock(CHEST_A, Blocks.CHEST.defaultBlockState(), 3);
		level.setBlock(CHEST_B, Blocks.CHEST.defaultBlockState(), 3);
		ChestBlockEntity pileChest = (ChestBlockEntity) level.getBlockEntity(CHEST_A);
		ChestBlockEntity plainChest = (ChestBlockEntity) level.getBlockEntity(CHEST_B);
		pileChest.setItem(0, VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BIRCH_LOG, 32)))).get(0));
		plainChest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		int pileSignal = AbstractContainerMenu.getRedstoneSignalFromBlockEntity(pileChest);
		int plainSignal = AbstractContainerMenu.getRedstoneSignalFromBlockEntity(plainChest);
		check("comparator: a 64 pile reads exactly like a plain 64 stack (signal " + plainSignal + ")",
				pileSignal == plainSignal && pileSignal > 0, failures);
		level.setBlock(CHEST_A, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(CHEST_B, Blocks.AIR.defaultBlockState(), 3);

		return failures[0];
	}

	private static CrafterBlockEntity freshCrafter(ServerLevel level) {
		level.setBlock(CRAFTER, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(CRAFTER, Blocks.CRAFTER.defaultBlockState(), 3);
		return (CrafterBlockEntity) level.getBlockEntity(CRAFTER);
	}

	/** Fire the crafter exactly as a redstone pulse's scheduled tick would. */
	private static void pulse(ServerLevel level) {
		((CrafterBlockInvoker) Blocks.CRAFTER).mythstack$dispenseFrom(level.getBlockState(CRAFTER), level, CRAFTER);
	}

	/**
	 * The chest on the crafter's front face: {@code dispenseItem} inserts into an adjacent container
	 * when one exists, which is deterministic headlessly (spawned item entities in not-yet-loaded
	 * entity sections are invisible to queries).
	 */
	private static ChestBlockEntity outputChest(ServerLevel level) {
		BlockPos front = CRAFTER.relative(level.getBlockState(CRAFTER)
				.getValue(BlockStateProperties.ORIENTATION).front());
		level.setBlock(front, Blocks.CHEST.defaultBlockState(), 3);
		return (ChestBlockEntity) level.getBlockEntity(front);
	}

	/** Collect (and clear) everything the crafter dispensed into its output chest. */
	private static LinkedHashMap<Item, Integer> drainChest(ChestBlockEntity chest) {
		LinkedHashMap<Item, Integer> counts = new LinkedHashMap<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
				chest.setItem(i, ItemStack.EMPTY);
			}
		}
		return counts;
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
