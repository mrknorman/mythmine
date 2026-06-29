package com.mythstack.client.mixin;

import com.mythstack.MythStack;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes pile interactions persist in the creative inventory. Creative is client-authoritative: the
 * inventory tab runs the click on the client's {@code inventoryMenu} and the server is told only via
 * {@code ServerboundSetCreativeModeSlotPacket} — there is no container-click round-trip. So our
 * interaction handlers (run client-side here — see {@code AbstractContainerMenuMixin}'s creative path)
 * mutate the local inventory, and this mixin diffs the inventory across the click and pushes every
 * changed slot to the server as a creative slot edit, so the result actually sticks.
 *
 * <p>Scoped to clicks that involve a pile or a wood, so ordinary creative inventory use is untouched.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

	/** Inventory-menu slot snapshot captured at the start of a relevant click; {@code null} = not relevant. */
	@Unique
	private List<ItemStack> mythstack$snapshot;

	@Inject(method = "slotClicked", at = @At("HEAD"))
	private void mythstack$snapshotBeforePileClick(Slot slot, int slotId, int button, ContainerInput input,
			CallbackInfo ci) {
		this.mythstack$snapshot = null;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack carried = player.inventoryMenu.getCarried();
		ItemStack slotItem = slot != null ? slot.getItem() : ItemStack.EMPTY;
		if (!mythstack$relevant(carried) && !mythstack$relevant(slotItem)) {
			return; // not a pile / wood interaction — leave vanilla creative handling alone
		}
		AbstractContainerMenu inv = player.inventoryMenu;
		List<ItemStack> snapshot = new ArrayList<>(inv.slots.size());
		for (Slot s : inv.slots) {
			snapshot.add(s.getItem().copy());
		}
		this.mythstack$snapshot = snapshot;
	}

	@Inject(method = "slotClicked", at = @At("TAIL"))
	private void mythstack$syncAfterPileClick(Slot slot, int slotId, int button, ContainerInput input,
			CallbackInfo ci) {
		List<ItemStack> snapshot = this.mythstack$snapshot;
		this.mythstack$snapshot = null;
		if (snapshot == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		LocalPlayer player = minecraft.player;
		if (gameMode == null || player == null) {
			return;
		}
		AbstractContainerMenu inv = player.inventoryMenu;
		// The server accepts creative slot edits for inventory-menu indices 1..45 (0 is the crafting result).
		int max = Math.min(inv.slots.size() - 1, 45);
		StringBuilder synced = new StringBuilder();
		for (int i = 1; i <= max; i++) {
			ItemStack now = inv.slots.get(i).getItem();
			ItemStack was = i < snapshot.size() ? snapshot.get(i) : ItemStack.EMPTY;
			if (!ItemStack.matches(was, now)) {
				gameMode.handleCreativeModeItemAdd(now, i);
				if (VariantPiles.isPile(now)) {
					synced.append(' ').append(i).append('=').append(mythstack$sel(now));
				}
			}
		}
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			ItemStack carried = inv.getCarried();
			ItemStack clicked = slot != null ? slot.getItem() : ItemStack.EMPTY;
			MythStack.LOGGER.info("[sel] click slot={} input={} -> carried={} clicked={} synced=[{}]",
					slotId, input, mythstack$sel(carried), mythstack$sel(clicked), synced.toString().trim());
		}
	}

	/** Dev trace: the pile's raw selected wood ({@code NONE} = selection lost), or empty/plain. */
	@Unique
	private static String mythstack$sel(ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return stack.isEmpty() ? "empty" : "plain";
		}
		return pile.selected().map(wood -> BuiltInRegistries.ITEM.getKey(wood).getPath()).orElse("NONE");
	}

	@Unique
	private static boolean mythstack$relevant(ItemStack stack) {
		return !stack.isEmpty() && (VariantPiles.isPile(stack) || VariantGroups.of(stack.getItem()) != null);
	}
}
