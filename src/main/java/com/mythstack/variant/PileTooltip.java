package com.mythstack.variant;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Tooltip data for a pile: its contained woods as plain stacks, rendered as a bundle-style grid by the
 * client {@code ClientPileTooltip}. Transient (built in {@code Item#getTooltipImage} when the tooltip is
 * drawn) — never serialized.
 */
public record PileTooltip(List<ItemStack> items) implements TooltipComponent {
}
