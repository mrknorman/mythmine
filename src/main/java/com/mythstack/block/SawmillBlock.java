package com.mythstack.block;

import com.mythstack.menu.SawmillMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The sawmill: the stonecutter, but for wood — same block behavior, our menu + recipe set. */
public class SawmillBlock extends StonecutterBlock {

	private static final Component TITLE = Component.translatable("container.mythstack.sawmill");

	public SawmillBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider((containerId, inventory, player) ->
				new SawmillMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
	}
}
