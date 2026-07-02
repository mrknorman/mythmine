package com.mythstack.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the dev self-test pulse a crafter directly (a scheduled redstone tick can't run headlessly). */
@Mixin(CrafterBlock.class)
public interface CrafterBlockInvoker {

	@Invoker("dispenseFrom")
	void mythstack$dispenseFrom(BlockState state, ServerLevel level, BlockPos pos);
}
