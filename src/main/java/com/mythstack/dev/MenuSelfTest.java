package com.mythstack.dev;

import com.mythstack.MythStack;
import com.mythstack.registry.ModComponents;
import com.mythstack.registry.ModItems;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Dev-only end-to-end test of the crafting <em>menu</em> path (plan §7 phase 3): a {@link FakePlayer}
 * driving a real {@link CraftingMenu} / inventory 2×2 through {@link AbstractContainerMenu#clicked} —
 * the same doClick → result-slot → onTake / quickMoveStack path a live client hits. This is the layer
 * the planner unit tests can't reach: preview population, per-slot single-take consumption, mass-craft
 * output/leftover, and pile draining inside a live grid.
 */
public final class MenuSelfTest {
	private MenuSelfTest() {
	}

	/** Runs all menu checks; logs PASS/FAIL per check and returns the failure count. */
	public static int run(ServerLevel level) {
		int[] failures = {0};
		FakePlayer player = FakePlayer.get(level);
		player.getInventory().clearContent();

		mixedPilelessStairs(level, player, failures);
		nonOakPileAdvance(level, player, failures);
		mixedPassTie(level, player, failures);
		massCraftRatio(level, player, failures);
		massCraftLeftover(level, player, failures);
		inventoryTwoByTwo(level, player, failures);
		nonGroupOutputs(level, player, failures);
		pileOnPileUnmix(level, player, failures);
		typedSticks(level, player, failures);
		netherAndBamboo(level, player, failures);
		craftLedger(level, player, failures);
		recipeBookFill(level, player, failures);
		stickPropagation(level, player, failures);
		craftOutputConsolidation(level, player, failures);
		creativeParity(level, player, failures);
		familyTrades(failures);

		player.getInventory().clearContent();
		player.containerMenu = player.inventoryMenu;
		return failures[0];
	}

	/** The exact regression: mixed woods, NO oak, NO piles, shaped recipe — must preview and take. */
	private static void mixedPilelessStairs(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		// Stairs shape: S.. / SS. / BBB — spruce placed first, so ties resolve to spruce.
		menu.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 10));
		menu.getSlot(4).set(new ItemStack(Items.SPRUCE_PLANKS, 10));
		menu.getSlot(5).set(new ItemStack(Items.SPRUCE_PLANKS, 10));
		menu.getSlot(7).set(new ItemStack(Items.BIRCH_PLANKS, 10));
		menu.getSlot(8).set(new ItemStack(Items.BIRCH_PLANKS, 10));
		menu.getSlot(9).set(new ItemStack(Items.BIRCH_PLANKS, 10));
		ItemStack preview = menu.getSlot(0).getItem();
		check("menu: pile-less mixed stairs previews spruce stairs x4",
				preview.getItem() == Items.SPRUCE_STAIRS && preview.getCount() == 4, failures);

		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		check("menu: first take carries spruce stairs x4",
				menu.getCarried().getItem() == Items.SPRUCE_STAIRS && menu.getCarried().getCount() == 4, failures);
		check("menu: take drains one from EACH slot (shape preserved, 27/27 left)",
				gridTotal(menu, 9, Items.SPRUCE_PLANKS) == 27 && gridTotal(menu, 9, Items.BIRCH_PLANKS) == 27
						&& menu.getSlot(1).getItem().getCount() == 9 && menu.getSlot(9).getItem().getCount() == 9, failures);
		check("menu: result refills after the take",
				menu.getSlot(0).getItem().getItem() == Items.SPRUCE_STAIRS, failures);

		menu.clicked(0, 0, ContainerInput.PICKUP, player); // accumulate onto the held stack
		check("menu: second take accumulates to x8 on the cursor, grid at 24/24",
				menu.getCarried().getCount() == 8 && gridTotal(menu, 9, Items.SPRUCE_PLANKS) == 24
						&& gridTotal(menu, 9, Items.BIRCH_PLANKS) == 24, failures);
		menu.setCarried(ItemStack.EMPTY);
	}

	/**
	 * A no-oak pile in a 1×1 recipe, scrolled to spruce: the SELECTED wood steers preview and takes
	 * (contents order is [birch, spruce] — alphabetical — so without the selection birch would lead),
	 * and when it runs dry the craft advances to the remaining wood.
	 */
	private static void nonOakPileAdvance(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		ItemStack pile = VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.SPRUCE_LOG, 3), new ItemStack(Items.BIRCH_LOG, 5)))).get(0);
		VariantPiles.seed(pile, Items.SPRUCE_LOG); // the player scrolled the pile to spruce
		menu.getSlot(1).set(pile);
		check("menu: scrolled pile previews the SELECTED wood (spruce planks, not first-content birch)",
				menu.getSlot(0).getItem().getItem() == Items.SPRUCE_PLANKS, failures);

		ItemStack first = take(menu, player);
		check("menu: pile take yields spruce planks x4",
				first.getItem() == Items.SPRUCE_PLANKS && first.getCount() == 4, failures);
		ItemStack slot = menu.getSlot(1).getItem();
		check("menu: pile drained by exactly one spruce ({birch5,spruce2})",
				VariantPiles.isPile(slot) && VariantPiles.countOf(slot, Items.SPRUCE_LOG) == 2
						&& VariantPiles.countOf(slot, Items.BIRCH_LOG) == 5 && wellFormed(slot), failures);

		take(menu, player);
		take(menu, player); // spruce exhausted
		slot = menu.getSlot(1).getItem();
		check("menu: selected wood dry -> slot collapses to plain birch x5",
				!VariantPiles.isPile(slot) && slot.getItem() == Items.BIRCH_LOG && slot.getCount() == 5, failures);
		check("menu: preview advances to birch planks",
				menu.getSlot(0).getItem().getItem() == Items.BIRCH_PLANKS, failures);

		ItemStack last = ItemStack.EMPTY;
		for (int i = 0; i < 5; i++) {
			last = take(menu, player);
		}
		check("menu: birch takes yield birch planks x4 until the grid is empty",
				last.getItem() == Items.BIRCH_PLANKS && last.getCount() == 4
						&& menu.getSlot(1).getItem().isEmpty(), failures);
		check("menu: empty grid -> empty result", menu.getSlot(0).getItem().isEmpty(), failures);
	}

	/** A true mixed craft (1 spruce + 1 birch pressure plate): tie goes to the first-placed wood. */
	private static void mixedPassTie(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		menu.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(2).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		check("menu: mixed pressure plate previews spruce (tie -> first placed)",
				menu.getSlot(0).getItem().getItem() == Items.SPRUCE_PRESSURE_PLATE, failures);
		ItemStack taken = take(menu, player);
		check("menu: mixed take consumes both planks for one spruce plate",
				taken.getItem() == Items.SPRUCE_PRESSURE_PLATE && taken.getCount() == 1
						&& menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty()
						&& menu.getSlot(0).getItem().isEmpty(), failures);
	}

	/** Shift-click on a mixed log pile mass-crafts the whole ratio plan into the inventory. */
	private static void massCraftRatio(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		player.getInventory().clearContent();
		ItemStack pile = VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 30), new ItemStack(Items.SPRUCE_LOG, 30)))).get(0);
		menu.getSlot(1).set(pile);
		check("menu: mixed log pile previews oak planks (active wood leads)",
				menu.getSlot(0).getItem().getItem() == Items.OAK_PLANKS, failures);

		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		check("menu: mass craft yields the full ratio (120 oak + 120 spruce planks)",
				invTotal(player, Items.OAK_PLANKS) == 120 && invTotal(player, Items.SPRUCE_PLANKS) == 120, failures);
		check("menu: mass craft consumes the whole pile, empty cursor",
				menu.getSlot(1).getItem().isEmpty() && menu.getCarried().isEmpty(), failures);
		check("menu: every inventory pile is well-formed (sum == count)", invWellFormed(player), failures);
		player.getInventory().clearContent();
	}

	/** Mass craft with a sub-craft remainder: leftover wood returns to the grid, still craftable. */
	private static void massCraftLeftover(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		player.getInventory().clearContent();
		menu.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 3));
		menu.getSlot(2).set(new ItemStack(Items.BIRCH_PLANKS, 2));
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		check("menu: leftover mass craft yields 1 spruce + 1 birch plate",
				invTotal(player, Items.SPRUCE_PRESSURE_PLATE) == 1
						&& invTotal(player, Items.BIRCH_PRESSURE_PLATE) == 1, failures);
		check("menu: the odd spruce plank returns to the grid",
				gridTotal(menu, 9, Items.SPRUCE_PLANKS) == 1 && gridTotal(menu, 9, Items.BIRCH_PLANKS) == 0, failures);
		check("menu: the returned plank previews its own recipe (spruce button)",
				menu.getSlot(0).getItem().getItem() == Items.SPRUCE_BUTTON, failures);
		player.getInventory().clearContent();
	}

	/** The player-inventory 2×2 grid runs the same preview / take / mass paths. */
	private static void inventoryTwoByTwo(ServerLevel level, FakePlayer player, int[] failures) {
		player.containerMenu = player.inventoryMenu;
		AbstractContainerMenu menu = player.inventoryMenu;
		player.getInventory().clearContent();

		menu.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(2).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		check("menu: 2x2 mixed plate previews spruce",
				menu.getSlot(0).getItem().getItem() == Items.SPRUCE_PRESSURE_PLATE, failures);
		ItemStack taken = take(menu, player);
		check("menu: 2x2 take crafts and clears the grid",
				taken.getItem() == Items.SPRUCE_PRESSURE_PLATE
						&& menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), failures);

		ItemStack pile = VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 4), new ItemStack(Items.BIRCH_LOG, 4)))).get(0);
		menu.getSlot(1).set(pile);
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		check("menu: 2x2 mass craft yields 16 oak + 16 birch planks",
				invTotal(player, Items.OAK_PLANKS) == 16 && invTotal(player, Items.BIRCH_PLANKS) == 16
						&& menu.getSlot(1).getItem().isEmpty(), failures);
		player.getInventory().clearContent();
	}

	/** Outputs OUTSIDE any variant group: per-wood (boat) transmutes by wood, tag-based (chest) is inert. */
	private static void nonGroupOutputs(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		// Boat: "# #" / "###" — 3 spruce + 2 birch, no oak, no piles. Majority wood wins.
		menu.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(3).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(4).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(5).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		menu.getSlot(6).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		check("menu: mixed boat previews the majority wood (spruce boat)",
				menu.getSlot(0).getItem().getItem() == Items.SPRUCE_BOAT, failures);
		ItemStack boat = take(menu, player);
		check("menu: boat take consumes one plank from every slot",
				boat.getItem() == Items.SPRUCE_BOAT && boat.getCount() == 1
						&& gridTotal(menu, 9, Items.SPRUCE_PLANKS) == 0
						&& gridTotal(menu, 9, Items.BIRCH_PLANKS) == 0, failures);

		// Chest ring, 4+4 mixed: output is wood-agnostic, must still preview and craft.
		CraftingMenu chestMenu = table(level, player);
		for (int slot : new int[]{1, 2, 3, 4}) {
			chestMenu.getSlot(slot).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		}
		for (int slot : new int[]{6, 7, 8, 9}) {
			chestMenu.getSlot(slot).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		}
		check("menu: mixed chest ring previews a chest",
				chestMenu.getSlot(0).getItem().getItem() == Items.CHEST, failures);
		ItemStack chest = take(chestMenu, player);
		check("menu: chest take consumes the ring",
				chest.getItem() == Items.CHEST && gridTotal(chestMenu, 9, Items.SPRUCE_PLANKS) == 0
						&& gridTotal(chestMenu, 9, Items.BIRCH_PLANKS) == 0, failures);
	}

	/** Dropping a pile on a same-group pile unmixes: purest stack stays in the slot, rest on the cursor. */
	private static void pileOnPileUnmix(ServerLevel level, FakePlayer player, int[] failures) {
		player.containerMenu = player.inventoryMenu;
		AbstractContainerMenu menu = player.inventoryMenu;
		player.getInventory().clearContent();

		// Two half-and-half piles -> one pure oak stack in the slot, one pure birch stack on the cursor.
		menu.getSlot(9).set(VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BIRCH_LOG, 32)))).get(0));
		menu.setCarried(VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BIRCH_LOG, 32)))).get(0));
		menu.clicked(9, 0, ContainerInput.PICKUP, player);
		ItemStack inSlot = menu.getSlot(9).getItem();
		ItemStack cursor = menu.getCarried();
		check("unmix: two half piles -> plain oak x64 in the slot",
				!VariantPiles.isPile(inSlot) && inSlot.getItem() == Items.OAK_LOG && inSlot.getCount() == 64, failures);
		check("unmix: plain birch x64 on the cursor",
				!VariantPiles.isPile(cursor) && cursor.getItem() == Items.BIRCH_LOG && cursor.getCount() == 64, failures);
		menu.setCarried(ItemStack.EMPTY);

		// Partial: {oak30,birch20} + {oak40,birch24} -> pure oak 64 in the slot, {oak6,birch44} carried,
		// with the carried pile's birch selection surviving the repack.
		menu.getSlot(9).set(VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 30), new ItemStack(Items.BIRCH_LOG, 20)))).get(0));
		ItemStack held = VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.BIRCH_LOG, 24)))).get(0);
		VariantPiles.seed(held, Items.BIRCH_LOG);
		menu.setCarried(held);
		menu.clicked(9, 0, ContainerInput.PICKUP, player);
		inSlot = menu.getSlot(9).getItem();
		cursor = menu.getCarried();
		check("unmix: uneven piles -> pure oak x64 in the slot",
				!VariantPiles.isPile(inSlot) && inSlot.getItem() == Items.OAK_LOG && inSlot.getCount() == 64, failures);
		check("unmix: remainder pile {oak6,birch44} on the cursor, well-formed",
				VariantPiles.isPile(cursor) && VariantPiles.countOf(cursor, Items.OAK_LOG) == 6
						&& VariantPiles.countOf(cursor, Items.BIRCH_LOG) == 44 && wellFormed(cursor), failures);
		check("unmix: the cursor's active selection (birch) survives the repack",
				VariantPiles.activeWood(cursor) == Items.BIRCH_LOG, failures);
		menu.setCarried(ItemStack.EMPTY);
		menu.getSlot(9).set(ItemStack.EMPTY);
	}

	/** Typed sticks (spec §13 phase A): per-wood creation, family-stick acceptance, ratio mass craft. */
	private static void typedSticks(ServerLevel level, FakePlayer player, int[] failures) {
		// A mixed planks column makes the first-placed wood's typed sticks.
		CraftingMenu menu = table(level, player);
		menu.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(4).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		ItemStack preview = menu.getSlot(0).getItem();
		check("sticks: mixed planks column previews spruce sticks x4",
				preview.getItem() == ModItems.SPRUCE_STICK && preview.getCount() == 4, failures);
		ItemStack sticks = take(menu, player);
		check("sticks: take yields spruce sticks and consumes both planks",
				sticks.getItem() == ModItems.SPRUCE_STICK && sticks.getCount() == 4
						&& menu.getSlot(1).getItem().isEmpty() && menu.getSlot(4).getItem().isEmpty(), failures);

		// A ladder from MIXED typed sticks — the acceptance overrides + non-group output path together.
		CraftingMenu ladder = table(level, player);
		for (int slot : new int[]{1, 3, 4, 5, 6, 7, 9}) {
			ladder.getSlot(slot).set(new ItemStack(slot <= 4 ? ModItems.SPRUCE_STICK : ModItems.BIRCH_STICK, 1));
		}
		check("sticks: a ladder crafts from mixed typed sticks",
				ladder.getSlot(0).getItem().getItem() == Items.LADDER, failures);
		ItemStack lad = take(ladder, player);
		check("sticks: ladder take consumes a stick from every slot",
				lad.getItem() == Items.LADDER && gridTotal(ladder, 9, ModItems.SPRUCE_STICK) == 0
						&& gridTotal(ladder, 9, ModItems.BIRCH_STICK) == 0, failures);

		// Mass craft two mixed plank piles into the stick ratio (plain sticks ARE oak's).
		CraftingMenu mass = table(level, player);
		player.getInventory().clearContent();
		for (int slot : new int[]{1, 4}) {
			mass.getSlot(slot).set(VariantPiles.makeStacks(VariantGroups.PLANKS, VariantPiles.pool(VariantGroups.PLANKS,
					List.of(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.SPRUCE_PLANKS, 4)))).get(0));
		}
		mass.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		check("sticks: mass craft yields the ratio (16 plain + 16 spruce sticks)",
				invTotal(player, Items.STICK) == 16 && invTotal(player, ModItems.SPRUCE_STICK) == 16, failures);
		check("sticks: mass craft consumes both plank piles",
				mass.getSlot(1).getItem().isEmpty() && mass.getSlot(4).getItem().isEmpty(), failures);
		player.getInventory().clearContent();
	}

	/** D4 reversed: crimson/warped/bamboo are family woods; the crafting layer covers their gaps. */
	private static void netherAndBamboo(ServerLevel level, FakePlayer player, int[] failures) {
		// A crimson-MAJORITY boat grid: no crimson boat exists, so the runner-up wood's boat crafts.
		CraftingMenu menu = table(level, player);
		menu.getSlot(1).set(new ItemStack(Items.CRIMSON_PLANKS, 1));
		menu.getSlot(3).set(new ItemStack(Items.CRIMSON_PLANKS, 1));
		menu.getSlot(4).set(new ItemStack(Items.CRIMSON_PLANKS, 1));
		menu.getSlot(5).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(6).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		check("d4: crimson-majority boat grid previews the runner-up wood's boat (spruce)",
				menu.getSlot(0).getItem().getItem() == Items.SPRUCE_BOAT, failures);
		ItemStack boat = take(menu, player);
		check("d4: the boat take still consumes the crimson planks (one per slot)",
				boat.getItem() == Items.SPRUCE_BOAT && gridTotal(menu, 9, Items.CRIMSON_PLANKS) == 0
						&& gridTotal(menu, 9, Items.SPRUCE_PLANKS) == 0, failures);

		// A PURE crimson boat grid crafts nothing at all.
		CraftingMenu pure = table(level, player);
		for (int slot : new int[]{1, 3, 4, 5, 6}) {
			pure.getSlot(slot).set(new ItemStack(Items.CRIMSON_PLANKS, 1));
		}
		check("d4: a pure crimson boat grid is not craftable", pure.getSlot(0).getItem().isEmpty(), failures);

		// Bamboo IS boat-productive — via its raft.
		CraftingMenu raft = table(level, player);
		for (int slot : new int[]{1, 3, 4, 5, 6}) {
			raft.getSlot(slot).set(new ItemStack(Items.BAMBOO_PLANKS, 1));
		}
		check("d4: pure bamboo boat grid previews the raft",
				raft.getSlot(0).getItem().getItem() == Items.BAMBOO_RAFT, failures);

		// A log pile mixing oak and bamboo block masses with PER-WOOD plank counts (4 vs 2 per craft).
		CraftingMenu mass = table(level, player);
		player.getInventory().clearContent();
		mass.getSlot(1).set(VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.BAMBOO_BLOCK, 2)))).get(0));
		mass.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		check("d4: oak+bamboo log pile masses to per-wood counts (8 oak + 4 bamboo planks)",
				invTotal(player, Items.OAK_PLANKS) == 8 && invTotal(player, Items.BAMBOO_PLANKS) == 4
						&& mass.getSlot(1).getItem().isEmpty(), failures);
		player.getInventory().clearContent();

		// Crimson sticks craft per-wood like any family wood's.
		CraftingMenu sticks = table(level, player);
		sticks.getSlot(1).set(new ItemStack(Items.CRIMSON_PLANKS, 1));
		sticks.getSlot(4).set(new ItemStack(Items.CRIMSON_PLANKS, 1));
		check("d4: crimson planks column previews crimson sticks x4",
				sticks.getSlot(0).getItem().getItem() == ModItems.CRIMSON_STICK
						&& sticks.getSlot(0).getItem().getCount() == 4, failures);

		// Bamboo mosaic stays pure-bamboo: a mixed slab column transmutes to nothing (no oak recipe
		// exists for the shape), while pure bamboo slabs still match vanilla directly.
		CraftingMenu mixedSlabs = table(level, player);
		mixedSlabs.getSlot(1).set(new ItemStack(Items.BAMBOO_SLAB, 1));
		mixedSlabs.getSlot(4).set(new ItemStack(Items.OAK_SLAB, 1));
		check("d4: a mixed slab column crafts nothing (mosaic is bamboo-only)",
				mixedSlabs.getSlot(0).getItem().isEmpty(), failures);
		CraftingMenu mosaic = table(level, player);
		mosaic.getSlot(1).set(new ItemStack(Items.BAMBOO_SLAB, 1));
		mosaic.getSlot(4).set(new ItemStack(Items.BAMBOO_SLAB, 1));
		check("d4: pure bamboo slabs still craft the mosaic (vanilla path)",
				mosaic.getSlot(0).getItem().getItem() == Items.BAMBOO_MOSAIC, failures);
	}

	/**
	 * The crafting ledger: transmuted takes/mass-crafts unlock the ELEMENT recipes. The recipe-book
	 * unlock is the observable proof the award path ran — the crafted stat rides the same call
	 * ({@code ItemStack.onCraftedBy}) but is unobservable here because FakePlayer stubs
	 * {@code awardStat} to a no-op by design.
	 */
	private static void craftLedger(ServerLevel level, FakePlayer player, int[] failures) {
		// A single take of a transmuted (mixed, pile-less) craft.
		CraftingMenu menu = table(level, player);
		menu.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(4).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(5).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		menu.getSlot(7).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		menu.getSlot(8).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		menu.getSlot(9).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		ItemStack taken = take(menu, player);
		check("ledger: a transmuted take crafts and unlocks the element recipe (spruce_stairs)",
				taken.getItem() == Items.SPRUCE_STAIRS
						&& player.getRecipeBook().contains(recipeKey("spruce_stairs")), failures);

		// A mass craft unlocks every per-wood recipe the ratio plan used.
		CraftingMenu mass = table(level, player);
		player.getInventory().clearContent();
		mass.getSlot(1).set(VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
				List.of(new ItemStack(Items.OAK_LOG, 3), new ItemStack(Items.JUNGLE_LOG, 2)))).get(0));
		mass.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		check("ledger: mass craft unlocks all per-wood recipes used (oak + jungle planks)",
				player.getRecipeBook().contains(recipeKey("oak_planks"))
						&& player.getRecipeBook().contains(recipeKey("jungle_planks")), failures);
		player.getInventory().clearContent();
	}

	/** The recipe book sees pile CONTENTS and auto-fill pulls the intended wood out of the pile. */
	private static void recipeBookFill(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		player.getInventory().clearContent();
		player.getInventory().setItem(9, VariantPiles.makeStacks(VariantGroups.LOGS,
				VariantPiles.pool(VariantGroups.LOGS,
						List.of(new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.SPRUCE_LOG, 10)))).get(0));

		// Availability: a spruce recipe is craftable from spruce living INSIDE an oak-hosted pile.
		StackedItemContents contents = new StackedItemContents();
		player.getInventory().fillStackedContents(contents);
		RecipeHolder<?> spruceRecipe = ((RecipeManager) level.recipeAccess())
				.byKey(recipeKey("spruce_planks")).orElse(null);
		check("book: pile contents count for availability (spruce planks craftable)",
				spruceRecipe != null && contents.canCraft(spruceRecipe.value(), null), failures);

		// Fill: clicking the recipe pulls SPRUCE out of the pile, not the oak host.
		menu.handlePlacement(false, false, spruceRecipe, level, player.getInventory());
		ItemStack inPile = player.getInventory().getItem(9);
		check("book: auto-fill places a spruce log in the grid",
				gridTotal(menu, 9, Items.SPRUCE_LOG) == 1, failures);
		check("book: the pile lost exactly one spruce ({oak10,spruce9})",
				VariantPiles.isPile(inPile) && VariantPiles.countOf(inPile, Items.SPRUCE_LOG) == 9
						&& VariantPiles.countOf(inPile, Items.OAK_LOG) == 10 && wellFormed(inPile), failures);
		for (int i = 1; i <= 9; i++) {
			menu.getSlot(i).set(ItemStack.EMPTY);
		}
		player.getInventory().clearContent();
	}

	/** Spec §13 phase B: pure-stick fences, and gates/signs propagating wood across groups. */
	private static void stickPropagation(ServerLevel level, FakePlayer player, int[] failures) {
		// Fences are pure sticks now (6 -> 3), single-group: mixed sticks give the majority's fence.
		CraftingMenu fence = table(level, player);
		for (int slot : new int[]{1, 2, 3}) {
			fence.getSlot(slot).set(new ItemStack(ModItems.SPRUCE_STICK, 1));
		}
		for (int slot : new int[]{4, 5, 6}) {
			fence.getSlot(slot).set(new ItemStack(ModItems.BIRCH_STICK, 1));
		}
		check("sticks-b: 6 mixed sticks preview the majority fence (tie -> first placed, x3)",
				fence.getSlot(0).getItem().getItem() == Items.SPRUCE_FENCE
						&& fence.getSlot(0).getItem().getCount() == 3, failures);
		ItemStack fences = take(fence, player);
		check("sticks-b: the fence take consumes one stick from every slot",
				fences.getItem() == Items.SPRUCE_FENCE
						&& gridTotal(fence, 9, ModItems.SPRUCE_STICK) == 0
						&& gridTotal(fence, 9, ModItems.BIRCH_STICK) == 0, failures);

		// A gate grid is MULTI-group (sticks + planks): pure spruce in, spruce gate out.
		CraftingMenu gate = table(level, player);
		for (int slot : new int[]{1, 3, 4, 6}) {
			gate.getSlot(slot).set(new ItemStack(ModItems.SPRUCE_STICK, 1));
		}
		gate.getSlot(2).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		gate.getSlot(5).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
		check("sticks-b: spruce sticks + spruce planks preview the SPRUCE gate (multi-group)",
				gate.getSlot(0).getItem().getItem() == Items.SPRUCE_FENCE_GATE, failures);

		// Mixed woods across the groups: sticks and planks vote as ONE wood identity each.
		CraftingMenu mixed = table(level, player);
		for (int slot : new int[]{1, 3, 4, 6}) {
			mixed.getSlot(slot).set(new ItemStack(ModItems.SPRUCE_STICK, 1));
		}
		mixed.getSlot(2).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		mixed.getSlot(5).set(new ItemStack(Items.BIRCH_PLANKS, 1));
		ItemStack gateTaken = take(mixed, player);
		check("sticks-b: a mixed gate crafts the majority wood (4 spruce sticks beat 2 birch planks)",
				gateTaken.getItem() == Items.SPRUCE_FENCE_GATE
						&& gridTotal(mixed, 9, ModItems.SPRUCE_STICK) == 0
						&& gridTotal(mixed, 9, Items.BIRCH_PLANKS) == 0, failures);

		// Piles feed multi-group grids: a stick pile and a plank pile, both scrolled to jungle.
		CraftingMenu piled = table(level, player);
		ItemStack stickPile = VariantPiles.makeStacks(VariantGroups.STICKS, VariantPiles.pool(VariantGroups.STICKS,
				List.of(new ItemStack(ModItems.JUNGLE_STICK, 4), new ItemStack(ModItems.BIRCH_STICK, 4)))).get(0);
		VariantPiles.seed(stickPile, ModItems.JUNGLE_STICK);
		ItemStack plankPile = VariantPiles.makeStacks(VariantGroups.PLANKS, VariantPiles.pool(VariantGroups.PLANKS,
				List.of(new ItemStack(Items.JUNGLE_PLANKS, 4), new ItemStack(Items.BIRCH_PLANKS, 4)))).get(0);
		VariantPiles.seed(plankPile, Items.JUNGLE_PLANKS);
		for (int slot : new int[]{1, 3, 4, 6}) {
			piled.getSlot(slot).set(stickPile.copy());
		}
		piled.getSlot(2).set(plankPile.copy());
		piled.getSlot(5).set(plankPile.copy());
		check("sticks-b: scrolled piles across groups preview the selected wood's gate (jungle)",
				piled.getSlot(0).getItem().getItem() == Items.JUNGLE_FENCE_GATE, failures);

		// Signs propagate the same way (planks + a stick): all cherry in, cherry sign out.
		CraftingMenu sign = table(level, player);
		for (int slot : new int[]{1, 2, 3, 4, 5, 6}) {
			sign.getSlot(slot).set(new ItemStack(Items.CHERRY_PLANKS, 1));
		}
		sign.getSlot(8).set(new ItemStack(ModItems.CHERRY_STICK, 1));
		check("sticks-b: a sign grid propagates its wood (cherry sign x3)",
				sign.getSlot(0).getItem().getItem() == Items.CHERRY_SIGN, failures);
	}

	/** Crafted output consolidates like a pickup: serial shift-crafts merge into piles, not new slots. */
	private static void craftOutputConsolidation(ServerLevel level, FakePlayer player, int[] failures) {
		CraftingMenu menu = table(level, player);
		player.getInventory().clearContent();
		player.getInventory().setItem(9, new ItemStack(Items.BIRCH_FENCE_GATE, 5)); // already held, plain
		// Materials for TWO spruce gates — a multi-group grid, so shift-click runs the vanilla serial
		// loop over our single-takes; each crafted gate must consolidate instead of opening a new slot.
		for (int slot : new int[]{1, 3, 4, 6}) {
			menu.getSlot(slot).set(new ItemStack(ModItems.SPRUCE_STICK, 2));
		}
		menu.getSlot(2).set(new ItemStack(Items.SPRUCE_PLANKS, 2));
		menu.getSlot(5).set(new ItemStack(Items.SPRUCE_PLANKS, 2));
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		ItemStack merged = player.getInventory().getItem(9);
		check("craft-out: serially crafted gates consolidate into the held stack ({birch5,spruce2} pile)",
				VariantPiles.isPile(merged) && VariantPiles.countOf(merged, Items.BIRCH_FENCE_GATE) == 5
						&& VariantPiles.countOf(merged, Items.SPRUCE_FENCE_GATE) == 2 && wellFormed(merged), failures);
		check("craft-out: both crafts' materials were consumed",
				gridTotal(menu, 9, ModItems.SPRUCE_STICK) == 0
						&& gridTotal(menu, 9, Items.SPRUCE_PLANKS) == 0, failures);
		player.getInventory().clearContent();
	}

	/**
	 * Server-side parity for CREATIVE-mode players: menus a creative player opens (chests, crafting
	 * tables, their own inventoryMenu server-side) use the same click round-trip as survival, so every
	 * pile op must behave identically. (The creative SCREEN's client-authoritative paths can't run
	 * headlessly — those are covered by the inventoryMenu-only gate + the creative slot-edit sync.)
	 */
	private static void creativeParity(ServerLevel level, FakePlayer player, int[] failures) {
		player.setGameMode(GameType.CREATIVE);
		try {
			// Unmix by real click, as creative: identical to the survival result.
			player.containerMenu = player.inventoryMenu;
			AbstractContainerMenu menu = player.inventoryMenu;
			player.getInventory().clearContent();
			menu.getSlot(9).set(VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
					List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BIRCH_LOG, 32)))).get(0));
			menu.setCarried(VariantPiles.makeStacks(VariantGroups.LOGS, VariantPiles.pool(VariantGroups.LOGS,
					List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BIRCH_LOG, 32)))).get(0));
			menu.clicked(9, 0, ContainerInput.PICKUP, player);
			check("creative: pile-on-pile unmix behaves exactly as survival",
					!VariantPiles.isPile(menu.getSlot(9).getItem())
							&& menu.getSlot(9).getItem().getItem() == Items.OAK_LOG
							&& menu.getSlot(9).getItem().getCount() == 64
							&& menu.getCarried().getItem() == Items.BIRCH_LOG
							&& menu.getCarried().getCount() == 64, failures);
			menu.setCarried(ItemStack.EMPTY);
			menu.getSlot(9).set(ItemStack.EMPTY);

			// Transmuted crafting as creative: preview, take, and consumption identical to survival.
			CraftingMenu table = table(level, player);
			table.getSlot(1).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
			table.getSlot(4).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
			table.getSlot(5).set(new ItemStack(Items.SPRUCE_PLANKS, 1));
			table.getSlot(7).set(new ItemStack(Items.BIRCH_PLANKS, 1));
			table.getSlot(8).set(new ItemStack(Items.BIRCH_PLANKS, 1));
			table.getSlot(9).set(new ItemStack(Items.BIRCH_PLANKS, 1));
			ItemStack taken = take(table, player);
			check("creative: a transmuted take crafts and consumes exactly as survival",
					taken.getItem() == Items.SPRUCE_STAIRS && taken.getCount() == 4
							&& gridTotal(table, 9, Items.SPRUCE_PLANKS) == 0
							&& gridTotal(table, 9, Items.BIRCH_PLANKS) == 0, failures);
		} finally {
			player.setGameMode(GameType.SURVIVAL);
			player.containerMenu = player.inventoryMenu;
			player.getInventory().clearContent();
		}
	}

	/** Trades asking for a family canonical (the plain stick) accept any family member — exactly. */
	private static void familyTrades(int[] failures) {
		// The fletcher's stick trade, as vanilla constructs it.
		MerchantOffer fletcher = new MerchantOffer(new ItemCost(Items.STICK, 32),
				new ItemStack(Items.EMERALD), 16, 2, 0.05f);
		check("trades: 32 spruce sticks satisfy the fletcher's stick trade",
				fletcher.satisfiedBy(new ItemStack(ModItems.SPRUCE_STICK, 32), ItemStack.EMPTY), failures);
		ItemStack payment = new ItemStack(ModItems.SPRUCE_STICK, 40);
		check("trades: taking the trade consumes exactly 32 typed sticks",
				fletcher.take(payment, ItemStack.EMPTY) && payment.getCount() == 8, failures);
		// Piles pay too (vanilla host semantics, kept deliberately): the shrink is reconciled lazily at
		// the next read, consuming canonical-first — same as any external shrink (see SelfTest #3).
		ItemStack pile = VariantPiles.makeStacks(VariantGroups.STICKS, VariantPiles.pool(VariantGroups.STICKS,
				List.of(new ItemStack(Items.STICK, 20), new ItemStack(ModItems.SPRUCE_STICK, 20)))).get(0);
		boolean paid = fletcher.take(pile, ItemStack.EMPTY);
		VariantPiles.reconcile(pile);
		check("trades: a stick pile pays canonical-first (20 plain + 12 spruce eaten, {spruce8} left)",
				paid && pile.getCount() == 8
						&& VariantPiles.countOf(pile, ModItems.SPRUCE_STICK) == 8 && wellFormed(pile), failures);
		// A cost naming a NON-canonical member stays exact.
		MerchantOffer exact = new MerchantOffer(new ItemCost(Items.SPRUCE_PLANKS, 4),
				new ItemStack(Items.EMERALD), 16, 2, 0.05f);
		check("trades: a spruce-planks cost is NOT satisfied by birch planks",
				!exact.satisfiedBy(new ItemStack(Items.BIRCH_PLANKS, 4), ItemStack.EMPTY)
						&& exact.satisfiedBy(new ItemStack(Items.SPRUCE_PLANKS, 4), ItemStack.EMPTY), failures);
	}

	private static ResourceKey<Recipe<?>> recipeKey(String path) {
		return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("minecraft", path));
	}

	private static CraftingMenu table(ServerLevel level, FakePlayer player) {
		CraftingMenu menu = new CraftingMenu(99, player.getInventory(),
				ContainerLevelAccess.create(level, BlockPos.ZERO));
		player.containerMenu = menu;
		return menu;
	}

	/** Click-take the result slot and hand back what landed on the cursor (cursor is then cleared). */
	private static ItemStack take(AbstractContainerMenu menu, FakePlayer player) {
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried().copy();
		menu.setCarried(ItemStack.EMPTY);
		return taken;
	}

	/** Total of {@code wood} across the menu's grid slots 1..{@code gridSlots}, pile-aware. */
	private static int gridTotal(AbstractContainerMenu menu, int gridSlots, Item wood) {
		int total = 0;
		for (int i = 1; i <= gridSlots; i++) {
			total += countIn(menu.getSlot(i).getItem(), wood);
		}
		return total;
	}

	/** Total of {@code wood} across the player inventory, pile-aware. */
	private static int invTotal(FakePlayer player, Item wood) {
		int total = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			total += countIn(stack, wood);
		}
		return total;
	}

	private static int countIn(ItemStack stack, Item wood) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (VariantPiles.isPile(stack)) {
			return VariantPiles.countOf(stack, wood);
		}
		return stack.getItem() == wood ? stack.getCount() : 0;
	}

	private static boolean invWellFormed(FakePlayer player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!wellFormed(stack)) {
				return false;
			}
		}
		return true;
	}

	/** The pile invariant: sum of contents == stack count (plain stacks pass trivially). */
	private static boolean wellFormed(ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return true;
		}
		int sum = 0;
		for (VariantPile.Entry entry : pile.contents()) {
			sum += entry.count();
		}
		return sum == stack.getCount();
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
