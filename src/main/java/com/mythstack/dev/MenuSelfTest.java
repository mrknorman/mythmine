package com.mythstack.dev;

import com.mythstack.MythStack;
import com.mythstack.registry.ModComponents;
import com.mythstack.registry.ModItems;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
