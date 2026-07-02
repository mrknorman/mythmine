package com.mythstack;

import com.mythstack.dev.SelfTest;
import com.mythstack.net.PileNetworking;
import com.mythstack.registry.ModBlocks;
import com.mythstack.registry.ModComponents;
import com.mythstack.registry.ModItems;
import com.mythstack.registry.ModMenus;
import com.mythstack.registry.ModRecipes;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
		ModRecipes.initialize();
		ModMenus.initialize();
		PileNetworking.register();

		// Every typed family sits DIRECTLY AFTER its canonical, in whichever tab vanilla put it —
		// anchor-following insertion, so the creative inventory stays organised without hardcoding tabs.
		CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
			insertFamilyAfter(output, Items.STICK, ModItems.TYPED_STICKS);
			for (var family : ModBlocks.TYPED_FAMILIES.entrySet()) {
				insertFamilyAfter(output, family.getKey().asItem(),
						family.getValue().stream().map(Block::asItem).toList());
			}
			insertFamilyAfter(output, Items.STONECUTTER, List.of(ModBlocks.SAWMILL.asItem()));
		});

		FuelValueEvents.BUILD.register((builder, context) -> {
			ModItems.TYPED_STICKS.stream()
					.filter(stick -> stick != ModItems.CRIMSON_STICK && stick != ModItems.WARPED_STICK)
					.forEach(stick -> builder.add(stick, 100));
			// Typed wooden blocks burn like their vanilla canonicals (300) — except the nether woods'
			// and the beehive (not fuel in vanilla).
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

	/** Insert {@code family} right after {@code anchor}, only in tabs that actually contain it. */
	private static void insertFamilyAfter(net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput output,
			net.minecraft.world.item.Item anchor, List<? extends net.minecraft.world.item.Item> family) {
		if (output.getDisplayStacks().stream().anyMatch(stack -> stack.is(anchor))) {
			output.insertAfter(anchor, family.stream().map(ItemStack::new).toArray(ItemStack[]::new));
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
