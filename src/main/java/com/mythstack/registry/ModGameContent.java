package com.mythstack.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Creative-tab placement, fuel values, and flammability for everything the mod adds — split out of
 * the mod initializer. Tab rule: every family sits DIRECTLY AFTER its anchor (its canonical, or
 * the vanilla block it varies), in whichever tab vanilla put that anchor — anchor-following
 * insertion, no hardcoded tabs.
 */
public final class ModGameContent {
	private ModGameContent() {
	}

	public static void register() {
		CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
			if (com.mythstack.config.ModConfig.TYPED_STICKS) {
				insertFamilyAfter(output, Items.STICK, ModItems.TYPED_STICKS);
			}
			if (!com.mythstack.config.ModConfig.BLOCKS) {
				insertOres(output);
				return;
			}
			for (var family : ModBlocks.TYPED_FAMILIES.entrySet()) {
				insertFamilyAfter(output, family.getKey().asItem(),
						family.getValue().stream().map(Block::asItem).toList());
			}
			if (com.mythstack.config.ModConfig.SAWMILL) {
				insertFamilyAfter(output, Items.STONECUTTER, List.of(ModBlocks.SAWMILL.asItem()));
			}
			// Stone kit: each material's new forms follow its raw block; functional forms and ore
			// variants follow their vanilla canonicals (buttons after the stone button, ...).
			for (var kit : StoneKit.NEW_FORMS.entrySet()) {
				insertFamilyAfter(output, kit.getKey().asItem(),
						kit.getValue().stream().map(Block::asItem).toList());
			}
			for (var functional : StoneKit.FUNCTIONAL_ANCHORS.entrySet()) {
				insertFamilyAfter(output, functional.getKey(),
						functional.getValue().stream().map(Block::asItem).toList());
			}
			insertOres(output);
		});

		FuelValueEvents.BUILD.register((builder, context) -> {
			ModItems.TYPED_STICKS.stream()
					.filter(stick -> stick != ModItems.CRIMSON_STICK && stick != ModItems.WARPED_STICK)
					.forEach(stick -> builder.add(stick, 100));
			// Typed wooden blocks burn like their vanilla canonicals (300) — except the nether
			// woods' and the beehive (not fuel in vanilla).
			for (var family : ModBlocks.TYPED_FAMILIES.entrySet()) {
				if (family.getKey() == Blocks.BEEHIVE) {
					continue;
				}
				family.getValue().stream().filter(block -> !ModBlocks.netherWood(block))
						.forEach(block -> builder.add(block, 300));
			}
		});

		// Flammability mirrors vanilla (bookshelves 30/20, lectern 30/20, composter/beehive 5/20);
		// nether wood doesn't burn.
		record Burn(List<Block> blocks, int ignite, int spread) {
		}
		for (Burn burn : List.of(new Burn(ModBlocks.TYPED_BOOKSHELVES, 30, 20),
				new Burn(ModBlocks.TYPED_CHISELED_BOOKSHELVES, 30, 20),
				new Burn(ModBlocks.TYPED_LECTERNS, 30, 20),
				new Burn(ModBlocks.TYPED_COMPOSTERS, 5, 20),
				new Burn(ModBlocks.TYPED_BEEHIVES, 5, 20))) {
			burn.blocks().stream().filter(block -> !ModBlocks.netherWood(block))
					.forEach(block -> FlammableBlockRegistry.getDefaultInstance()
							.add(block, burn.ignite(), burn.spread()));
		}
	}

	private static void insertOres(FabricCreativeModeTabOutput output) {
		if (!com.mythstack.config.ModConfig.TERRAIN) {
			return;
		}
		for (var ores : StoneOres.ORE_ANCHORS.entrySet()) {
			insertFamilyAfter(output, ores.getKey(),
					ores.getValue().stream().map(Block::asItem).toList());
		}
	}

	/** Insert {@code family} right after {@code anchor}, only in tabs that actually contain it. */
	private static void insertFamilyAfter(FabricCreativeModeTabOutput output, Item anchor,
			List<? extends Item> family) {
		if (output.getDisplayStacks().stream().anyMatch(stack -> stack.is(anchor))) {
			output.insertAfter(anchor, family.stream().map(ItemStack::new).toArray(ItemStack[]::new));
		}
	}
}
