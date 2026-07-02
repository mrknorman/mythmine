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

		// 4d. Stations: barrels and chiseled bookshelves host their WIDENED vanilla block entities,
		//     and a typed bookshelf drops its books like vanilla.
		level.setBlock(CHEST_A, ModBlocks.TYPED_BARRELS.get(0).defaultBlockState(), 3);
		boolean barrelOk = level.getBlockEntity(CHEST_A)
				instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity;
		level.setBlock(CHEST_A, ModBlocks.TYPED_CHISELED_BOOKSHELVES.get(0).defaultBlockState(), 3);
		boolean chiseledOk = level.getBlockEntity(CHEST_A)
				instanceof net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
		level.setBlock(CHEST_A, ModBlocks.TYPED_LECTERNS.get(0).defaultBlockState(), 3);
		boolean lecternOk = level.getBlockEntity(CHEST_A)
				instanceof net.minecraft.world.level.block.entity.LecternBlockEntity;
		level.setBlock(CHEST_A, ModBlocks.TYPED_JUKEBOXES.get(0).defaultBlockState(), 3);
		boolean jukeboxOk = level.getBlockEntity(CHEST_A)
				instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity;
		level.setBlock(CHEST_A, ModBlocks.TYPED_BEEHIVES.get(0).defaultBlockState(), 3);
		boolean beehiveOk = level.getBlockEntity(CHEST_A)
				instanceof net.minecraft.world.level.block.entity.BeehiveBlockEntity;
		check("stations: typed barrels and chiseled bookshelves host working block entities",
				barrelOk && chiseledOk, failures);
		check("stations: typed lecterns, jukeboxes, and beehives host working block entities",
				lecternOk && jukeboxOk && beehiveOk, failures);
		level.setBlock(CHEST_A, ModBlocks.TYPED_BOOKSHELVES.get(0).defaultBlockState(), 3);
		java.util.List<ItemStack> shelfDrops = Block.getDrops(level.getBlockState(CHEST_A), level, CHEST_A, null);
		int books = shelfDrops.stream().filter(d -> d.getItem() == Items.BOOK).mapToInt(ItemStack::getCount).sum();
		check("stations: a typed bookshelf drops its 3 books", books == 3, failures);
		level.setBlock(CHEST_A, Blocks.AIR.defaultBlockState(), 3);

		// 4e. The carpenter: profession + POI registered, the sawmill is its job site, and the
		//     data-driven trade sets/trades all loaded (biome-keyed entries included).
		boolean professionOk = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
				.containsKey(MythStack.id("carpenter"));
		boolean poiOk = com.mythstack.mixin.PoiTypesAccessor.mythstack$typeByState()
				.get(ModBlocks.SAWMILL.defaultBlockState()) != null
				&& com.mythstack.mixin.PoiTypesAccessor.mythstack$typeByState()
						.get(ModBlocks.SAWMILL.defaultBlockState())
						.is(com.mythstack.registry.ModVillagers.CARPENTER_POI_KEY);
		check("carpenter: profession registered and the sawmill is its job site", professionOk && poiOk, failures);
		var tradeSets = level.registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.TRADE_SET);
		var trades = level.registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TRADE);
		boolean setsOk = true;
		for (int lvl = 1; lvl <= 5; lvl++) {
			setsOk &= tradeSets.get(net.minecraft.resources.ResourceKey.create(
					net.minecraft.core.registries.Registries.TRADE_SET,
					MythStack.id("carpenter/level_" + lvl))).isPresent();
		}
		long tradeCount = trades.listElements()
				.filter(ref -> ref.key().identifier().getNamespace().equals("mythstack")).count();
		check("carpenter: all 5 trade sets + 53 trades loaded (" + tradeCount + ")",
				setsOk && tradeCount == 53, failures);

		// 4f. Stone kit S1a: 143 blocks registered (the count is enforced at init); loot
		//     normalization (granite drops cobbled, silk touch restores); walls joined the wall
		//     tag. 199 = 143 core kit (S1a) + 56 mossy (S1b).
		//     tag; the stonecutter gained full parity cuts.
		int kitCount = com.mythstack.registry.StoneKit.NEW_FORMS.values().stream()
				.mapToInt(java.util.List::size).sum();
		check("stone kit: all 199 gap blocks registered (core + mossy)", kitCount == 199, failures);
		Block cobbledGranite = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getValue(MythStack.id("cobbled_granite"));
		BlockPos stonePos = new BlockPos(32, 200, 0);
		level.setBlock(stonePos, net.minecraft.world.level.block.Blocks.GRANITE.defaultBlockState(), 3);
		var plainDrops = net.minecraft.world.level.block.Block.getDrops(
				level.getBlockState(stonePos), level, stonePos, null, null,
				new ItemStack(Items.IRON_PICKAXE));
		ItemStack silkPick = new ItemStack(Items.IRON_PICKAXE);
		silkPick.enchant(level.registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
		var silkDrops = net.minecraft.world.level.block.Block.getDrops(
				level.getBlockState(stonePos), level, stonePos, null, null, silkPick);
		check("stone kit: granite drops cobbled granite; silk touch restores granite",
				plainDrops.size() == 1 && plainDrops.get(0).getItem() == cobbledGranite.asItem()
						&& silkDrops.size() == 1 && silkDrops.get(0).getItem() == Items.GRANITE, failures);
		level.setBlock(stonePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		Block calciteWall = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getValue(MythStack.id("calcite_wall"));
		check("stone kit: new walls join #minecraft:walls (calcite wall connects)",
				calciteWall.defaultBlockState().is(net.minecraft.tags.BlockTags.WALLS), failures);

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
