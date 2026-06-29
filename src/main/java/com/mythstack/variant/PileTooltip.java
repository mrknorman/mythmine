package com.mythstack.variant;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Tooltip data for a pile: its contained woods as plain stacks (in contents order) plus the index of the
 * active/selected wood ({@code -1} = none), rendered as a bundle-style grid by {@code ClientPileTooltip}
 * with the active slot highlighted. Transient (built in {@code Item#getTooltipImage}) — never serialized.
 */
public record PileTooltip(List<ItemStack> items, int selectedIndex) implements TooltipComponent {
}
