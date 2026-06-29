package com.mythstack;

import com.mythstack.dev.SelfTest;
import com.mythstack.registry.ModBlocks;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroups;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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

		// Drop the test block into the vanilla Building Blocks creative tab.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.accept(ModBlocks.WHITE_BLOCK));

		// Dev-only: once datapack tags have loaded, dump variant-group resolutions (phase 2) and run
		// the headless self-test for the VariantPiles layer (phase 4).
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				VariantGroups.logSampleResolutions();
				SelfTest.run();
			});
		}

		LOGGER.info("mythstack initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
