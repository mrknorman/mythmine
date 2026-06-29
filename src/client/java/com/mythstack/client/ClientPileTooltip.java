package com.mythstack.client;

import com.mythstack.variant.PileTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Renders a {@link PileTooltip} as a bundle-style grid of the contained woods (icon + count per slot),
 * up to 4 per row. Reuses vanilla's bundle slot-background sprite for the look.
 */
public class ClientPileTooltip implements ClientTooltipComponent {

	private static final Identifier SLOT_BACKGROUND = Identifier.withDefaultNamespace("container/bundle/slot_background");
	private static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/bundle/slot_highlight_back");
	private static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/bundle/slot_highlight_front");
	private static final int SLOT_SIZE = 24;
	private static final int COLUMNS = 4;

	private final List<ItemStack> items;
	private final int selectedIndex;

	public ClientPileTooltip(List<ItemStack> items, int selectedIndex) {
		this.items = items;
		this.selectedIndex = selectedIndex;
	}

	private int rows() {
		return Math.max(1, Mth.positiveCeilDiv(this.items.size(), COLUMNS));
	}

	private int columns() {
		return Math.min(Math.max(this.items.size(), 1), COLUMNS);
	}

	@Override
	public int getHeight(Font font) {
		return this.rows() * SLOT_SIZE;
	}

	@Override
	public int getWidth(Font font) {
		return this.columns() * SLOT_SIZE;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		for (int i = 0; i < this.items.size(); i++) {
			int drawX = x + (i % COLUMNS) * SLOT_SIZE;
			int drawY = y + (i / COLUMNS) * SLOT_SIZE;
			boolean active = i == this.selectedIndex;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, active ? SLOT_HIGHLIGHT_BACK : SLOT_BACKGROUND,
					drawX, drawY, SLOT_SIZE, SLOT_SIZE);
			ItemStack item = this.items.get(i);
			graphics.item(item, drawX + 4, drawY + 4, i);
			graphics.itemDecorations(font, item, drawX + 4, drawY + 4);
			if (active) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, drawX, drawY, SLOT_SIZE, SLOT_SIZE);
			}
		}
	}
}
