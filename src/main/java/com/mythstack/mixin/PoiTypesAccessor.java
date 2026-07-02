package com.mythstack.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Lets the typed barrels register as fisherman job sites (POI discovery is keyed by block state). */
@Mixin(PoiTypes.class)
public interface PoiTypesAccessor {

	@Accessor("TYPE_BY_STATE")
	static Map<BlockState, Holder<PoiType>> mythstack$typeByState() {
		throw new AssertionError();
	}

	@Accessor("TYPE_BY_STATE")
	@Mutable
	static void mythstack$setTypeByState(Map<BlockState, Holder<PoiType>> map) {
		throw new AssertionError();
	}
}
