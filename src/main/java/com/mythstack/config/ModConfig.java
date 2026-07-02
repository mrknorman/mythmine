package com.mythstack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mythstack.MythStack;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Module config (config/mythstack.json) — the seams of the future submod split. PILES (the
 * machinery + vanilla families) is the mod itself; the rest can be switched off:
 * <ul>
 *   <li>{@code blocks} — the normalization/station/typed-block content;</li>
 *   <li>{@code sawmill} — the sawmill block + the carpenter villager;</li>
 *   <li>{@code terrain} — the geology overhaul (regions, bands, ore variants, loot normalization);</li>
 *   <li>{@code typed_sticks} — the most vanilla-divisive flavor item, individually toggleable.</li>
 * </ul>
 * Off means UNOBTAINABLE (no recipes/trades/worldgen/tabs, excluded from family membership so the
 * transmuter never produces it) — registration always happens, so existing worlds keep their
 * blocks and tags never dangle. The Shale/Oak renames are a built-in resource pack, toggled in the
 * vanilla resource-pack UI instead of here.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final boolean BLOCKS;
	public static final boolean SAWMILL;
	public static final boolean TERRAIN;
	public static final boolean TYPED_STICKS;

	static {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("mythstack.json");
		JsonObject json = new JsonObject();
		if (Files.exists(path)) {
			try {
				json = GSON.fromJson(Files.readString(path), JsonObject.class);
			} catch (IOException | RuntimeException e) {
				MythStack.LOGGER.error("could not read {}, using defaults", path, e);
			}
		}
		BLOCKS = get(json, "blocks");
		SAWMILL = get(json, "sawmill");
		TERRAIN = get(json, "terrain");
		TYPED_STICKS = get(json, "typed_sticks");
		if (!Files.exists(path)) {
			JsonObject defaults = new JsonObject();
			defaults.addProperty("blocks", true);
			defaults.addProperty("sawmill", true);
			defaults.addProperty("terrain", true);
			defaults.addProperty("typed_sticks", true);
			try {
				Files.writeString(path, GSON.toJson(defaults));
			} catch (IOException e) {
				MythStack.LOGGER.error("could not write default config", e);
			}
		}
		MythStack.LOGGER.info("modules: blocks={} sawmill={} terrain={} typed_sticks={}",
				BLOCKS, SAWMILL, TERRAIN, TYPED_STICKS);
	}

	private ModConfig() {
	}

	private static boolean get(JsonObject json, String key) {
		return !json.has(key) || json.get(key).getAsBoolean();
	}

	public static boolean enabled(String module) {
		return switch (module) {
			case "blocks" -> BLOCKS;
			case "sawmill" -> SAWMILL;
			case "terrain" -> TERRAIN;
			case "typed_sticks" -> TYPED_STICKS;
			default -> {
				MythStack.LOGGER.warn("unknown module in condition: {}", module);
				yield true;
			}
		};
	}
}
