package com.mythstack.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Lets the typed chests join {@code BlockEntityType.CHEST}'s valid blocks — sharing the vanilla block
 * entity type is what gives them the whole vanilla chest stack for free (renderer, double-chest
 * merging, hoppers, comparators) with the classic chest look as the default texture.
 */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {

	@Accessor("validBlocks")
	Set<Block> mythstack$validBlocks();

	@Accessor("validBlocks")
	@Mutable
	void mythstack$setValidBlocks(Set<Block> blocks);
}
