package com.mythstack.registry;

import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geology overhaul: per-material ore variants — the deepslate-ore pattern generalized. Every
 * promoted region stone (granite/diorite/andesite/calcite) and tuff hosts its own textured variant
 * of each overworld ore; the ore FEATURES gain block-match targets so veins in a granite region
 * place granite iron ore. Families grow via the vanilla ore tags, so piles/smelting inherit.
 */
public final class StoneOres {
	private StoneOres() {
	}

	public static final List<String> HOST_MATERIALS =
			List.of("granite", "diorite", "andesite", "calcite", "tuff");

	private record OreType(String name, Block vanilla, IntProvider xp) {
	}

	private static final List<OreType> ORE_TYPES = List.of(
			new OreType("coal_ore", Blocks.COAL_ORE, UniformInt.of(0, 2)),
			new OreType("iron_ore", Blocks.IRON_ORE, ConstantInt.of(0)),
			new OreType("copper_ore", Blocks.COPPER_ORE, ConstantInt.of(0)),
			new OreType("gold_ore", Blocks.GOLD_ORE, ConstantInt.of(0)),
			new OreType("redstone_ore", Blocks.REDSTONE_ORE, ConstantInt.of(0)),
			new OreType("emerald_ore", Blocks.EMERALD_ORE, UniformInt.of(3, 7)),
			new OreType("lapis_ore", Blocks.LAPIS_ORE, UniformInt.of(2, 5)),
			new OreType("diamond_ore", Blocks.DIAMOND_ORE, UniformInt.of(3, 7)));

	/** vanilla ore item -> our variants, for creative-tab anchoring (after each vanilla ore). */
	public static final Map<Item, List<Block>> ORE_ANCHORS = new LinkedHashMap<>();

	static void initialize() {
		int count = 0;
		for (OreType ore : ORE_TYPES) {
			java.util.ArrayList<Block> variants = new java.util.ArrayList<>();
			for (String material : HOST_MATERIALS) {
				String name = material + "_" + ore.name();
				BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(ore.vanilla());
				Block block = ore.name().equals("redstone_ore")
						? ModBlocks.register(name, RedStoneOreBlock::new, props, true)
						: ModBlocks.register(name, p -> new DropExperienceBlock(ore.xp(), p), props, true);
				variants.add(block);
				count++;
			}
			ORE_ANCHORS.put(ore.vanilla().asItem(), List.copyOf(variants));
		}
		if (count != 40) {
			throw new IllegalStateException("stone ores expected 40, got " + count);
		}
	}
}
