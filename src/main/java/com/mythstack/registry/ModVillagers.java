package com.mythstack.registry;

import com.google.common.collect.ImmutableSet;
import com.mythstack.MythStack;
import com.mythstack.mixin.PoiTypesAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The CARPENTER (plan: carpenter villager, part 1): job site = the sawmill, trades data-driven via
 * {@code trade_set/carpenter/level_N} (26.2 villager trades are datapack registries) — biome-coherent:
 * sells its village's wood (saplings, ladders, barrels, boats, bookshelves keyed on the villager
 * variant), buys any-family wood via the canonical costs (piles pay), and pays premium rates for
 * woods its biome lacks; exotic saplings (pale oak, mangrove, nether fungi) unlock at high levels.
 */
public final class ModVillagers {
	private ModVillagers() {
	}

	public static final ResourceKey<PoiType> CARPENTER_POI_KEY =
			ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MythStack.id("carpenter"));

	public static final Holder<PoiType> CARPENTER_POI = registerPoi();

	public static final ResourceKey<VillagerProfession> CARPENTER_KEY =
			ResourceKey.create(Registries.VILLAGER_PROFESSION, MythStack.id("carpenter"));

	public static final VillagerProfession CARPENTER = Registry.register(
			BuiltInRegistries.VILLAGER_PROFESSION, CARPENTER_KEY,
			new VillagerProfession(
					Component.translatable("entity.mythstack.villager.carpenter"),
					holder -> holder.is(CARPENTER_POI_KEY),
					holder -> holder.is(CARPENTER_POI_KEY),
					ImmutableSet.of(),
					ImmutableSet.of(),
					SoundEvents.UI_STONECUTTER_TAKE_RESULT,
					tradeSets()));

	private static Holder<PoiType> registerPoi() {
		Set<BlockState> states = new HashSet<>(ModBlocks.SAWMILL.getStateDefinition().getPossibleStates());
		Holder<PoiType> holder = Registry.registerForHolder(BuiltInRegistries.POINT_OF_INTEREST_TYPE,
				CARPENTER_POI_KEY, new PoiType(states, 1, 1));
		// POI discovery is keyed by block state (same wiring the typed job sites use).
		Map<BlockState, Holder<PoiType>> byState = new HashMap<>(PoiTypesAccessor.mythstack$typeByState());
		for (BlockState state : states) {
			byState.put(state, holder);
		}
		PoiTypesAccessor.mythstack$setTypeByState(byState);
		return holder;
	}

	private static Int2ObjectMap<ResourceKey<TradeSet>> tradeSets() {
		Int2ObjectMap<ResourceKey<TradeSet>> sets = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) {
			sets.put(level, ResourceKey.create(Registries.TRADE_SET, MythStack.id("carpenter/level_" + level)));
		}
		return sets;
	}

	/** Called from {@link MythStack#onInitialize()} to force class-load so the statics register. */
	public static void initialize() {
	}
}
