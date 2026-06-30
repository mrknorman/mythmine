package com.mythstack;

import com.mythstack.dev.SelfTest;
import com.mythstack.net.PileNetworking;
import com.mythstack.registry.ModBlocks;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroups;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MythStack implements ModInitializer {
	public static final String MOD_ID = "mythstack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModComponents.initialize();
		ModBlocks.initialize();
		PileNetworking.register();

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
