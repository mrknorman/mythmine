package com.mythstack;

import com.mythstack.dev.SelfTest;
import com.mythstack.net.PileNetworking;
import com.mythstack.registry.ModBlocks;
import com.mythstack.registry.ModComponents;
import com.mythstack.registry.ModItems;
import com.mythstack.variant.VariantGroups;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MythStack implements ModInitializer {
	public static final String MOD_ID = "mythstack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModComponents.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		PileNetworking.register();

		// Typed sticks sit next to the vanilla stick in Ingredients and burn exactly like it —
		// except the nether ones: crimson/warped wood is not furnace fuel, so neither are its sticks.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output ->
				output.insertAfter(Items.STICK, ModItems.TYPED_STICKS.stream().map(ItemStack::new).toArray(ItemStack[]::new)));
		FuelValueEvents.BUILD.register((builder, context) -> {
			ModItems.TYPED_STICKS.stream()
					.filter(stick -> stick != ModItems.CRIMSON_STICK && stick != ModItems.WARPED_STICK)
					.forEach(stick -> builder.add(stick, 100));
			// Typed wooden blocks burn like their vanilla canonicals (300) — except the nether woods'.
			for (var blocks : List.of(ModBlocks.TYPED_LADDERS, ModBlocks.TYPED_CHESTS,
					ModBlocks.TYPED_BOOKSHELVES, ModBlocks.TYPED_CHISELED_BOOKSHELVES,
					ModBlocks.TYPED_BARRELS, ModBlocks.TYPED_CRAFTING_TABLES)) {
				blocks.stream().filter(block -> !ModBlocks.netherWood(block))
						.forEach(block -> builder.add(block, 300));
			}
		});

		// Bookshelves catch fire like vanilla's (30/20); nether wood doesn't burn.
		for (var blocks : List.of(ModBlocks.TYPED_BOOKSHELVES, ModBlocks.TYPED_CHISELED_BOOKSHELVES)) {
			blocks.stream().filter(block -> !ModBlocks.netherWood(block))
					.forEach(block -> FlammableBlockRegistry.getDefaultInstance().add(block, 30, 20));
		}

		// Typed blocks sit next to their vanilla canonicals in Functional Blocks.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.insertAfter(Items.LADDER,
					ModBlocks.TYPED_LADDERS.stream().map(ItemStack::new).toArray(ItemStack[]::new));
			output.insertAfter(Items.CHEST,
					ModBlocks.TYPED_CHESTS.stream().map(ItemStack::new).toArray(ItemStack[]::new));
			output.insertAfter(Items.BOOKSHELF,
					ModBlocks.TYPED_BOOKSHELVES.stream().map(ItemStack::new).toArray(ItemStack[]::new));
			output.insertAfter(Items.CHISELED_BOOKSHELF,
					ModBlocks.TYPED_CHISELED_BOOKSHELVES.stream().map(ItemStack::new).toArray(ItemStack[]::new));
			output.insertAfter(Items.BARREL,
					ModBlocks.TYPED_BARRELS.stream().map(ItemStack::new).toArray(ItemStack[]::new));
			output.insertAfter(Items.CRAFTING_TABLE,
					ModBlocks.TYPED_CRAFTING_TABLES.stream().map(ItemStack::new).toArray(ItemStack[]::new));
		});

		// Snapshot variant-group membership whenever tags load/sync (client + server), so resolving an
		// item to its group never depends on a flaky per-call tag binding during container prediction.
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) ->
				VariantGroups.rebuildMembership(registries));

		// Drop the test block into the vanilla Building Blocks creative tab.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.accept(ModBlocks.WHITE_BLOCK));

		// Dev-only: once datapack tags have loaded, dump variant-group resolutions (phase 2) and run
		// the headless self-test for the VariantPiles layer (phase 4).
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				VariantGroups.logSampleResolutions();
				SelfTest.run(server.overworld());
			});
		}

		LOGGER.info("mythstack initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
