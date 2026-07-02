package com.mythstack;

import com.mythstack.dev.SelfTest;
import com.mythstack.net.PileNetworking;
import com.mythstack.registry.ModBlocks;
import com.mythstack.registry.ModComponents;
import com.mythstack.registry.ModGameContent;
import com.mythstack.registry.ModItems;
import com.mythstack.registry.ModMenus;
import com.mythstack.registry.ModRecipes;
import com.mythstack.registry.ModVillagers;
import com.mythstack.registry.ModWorldgen;
import com.mythstack.variant.VariantGroups;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

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
		ModVillagers.initialize();
		PileNetworking.register();
		ModGameContent.register(); // creative tabs, fuels, flammability
		ModWorldgen.register(); // blob removal, blue ice (data half: gen_worldgen.py)

		// Snapshot variant-group membership whenever tags load/sync (client + server), so resolving
		// an item to its group never depends on a flaky per-call tag binding during prediction.
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) ->
				VariantGroups.rebuildMembership(registries));

		// Dev-only: once the server is up, run the headless self-test suite.
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
