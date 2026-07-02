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
		com.mythstack.config.ModuleCondition.register(); // before any datapack load
		ModComponents.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModRecipes.initialize();
		ModMenus.initialize();
		ModVillagers.initialize();
		PileNetworking.register();
		ModGameContent.register(); // creative tabs, fuels, flammability
		if (com.mythstack.config.ModConfig.TERRAIN) {
			ModWorldgen.register(); // blob removal, blue ice (data half: gen_worldgen.py)
		}

		// Vanilla-file OVERRIDES live in built-in data packs (a disabled pack falls back to vanilla
		// cleanly; resource conditions on overrides would leave registry entries unbound instead).
		// Default activation follows the module config; per-world /datapack control also works.
		var container = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
		net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
				id("geology"), container,
				net.minecraft.network.chat.Component.literal("MythStack Geology (regions, bands, ore variants)"),
				com.mythstack.config.ModConfig.TERRAIN
						? net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED
						: net.fabricmc.fabric.api.resource.ResourcePackActivationType.NORMAL);
		net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
				id("sticks"), container,
				net.minecraft.network.chat.Component.literal("MythStack Typed-Stick Drops"),
				com.mythstack.config.ModConfig.TYPED_STICKS
						? net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED
						: net.fabricmc.fabric.api.resource.ResourcePackActivationType.NORMAL);

		// The Shale/Oak rename cascade ships as a built-in resource pack — vanilla-native opt-out
		// via the resource-pack screen rather than a config flag.
		net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
				id("renames"), container,
				net.minecraft.network.chat.Component.literal("MythStack Renames (Shale, Oak Stick...)"),
				net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED);

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
