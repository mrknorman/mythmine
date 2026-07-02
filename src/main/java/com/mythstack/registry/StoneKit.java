package com.mythstack.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stone normalization S1a (STONE_PHASE.md): every tier-1 material filled to the 19-form core kit.
 * Naming follows vanilla conventions exactly; a form is only registered when vanilla doesn't have
 * it — the gap set (143 blocks) is DERIVED from the block registry, not hardcoded, so the kit
 * stays correct if vanilla fills a gap. The python generators use these same naming rules.
 */
public final class StoneKit {
	private StoneKit() {
	}

	/**
	 * A tier-1 material's line names. {@code raw} is the vanilla raw block id; the rest are the
	 * base names of each line (stairs/slab/wall names derive from them; "bricks" singularizes).
	 */
	public record Material(String name, String raw, String cobbled, String polished, String bricks,
			String chiseled, String pillar, boolean rawIsPillar, boolean lootNormalized,
			boolean reducedKit) {

		Material(String name, String raw, String cobbled, String polished, String bricks,
				String chiseled, String pillar, boolean rawIsPillar, boolean lootNormalized) {
			this(name, raw, cobbled, polished, bricks, chiseled, pillar, rawIsPillar, lootNormalized, false);
		}

		public String cracked() {
			return "cracked_" + bricks;
		}

		/** Wet-overworld materials moss (S1b); nether/end/dry ones don't. */
		public boolean mossy() {
			return switch (name) {
				case "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite",
						"dripstone" -> true;
				default -> false;
			};
		}
	}

	public static final List<Material> MATERIALS = List.of(
			new Material("stone", "stone", "cobblestone", "smooth_stone", "stone_bricks",
					"chiseled_stone_bricks", "stone_pillar", false, false), // vanilla already normalized
			new Material("deepslate", "deepslate", "cobbled_deepslate", "polished_deepslate",
					"deepslate_bricks", "chiseled_deepslate", "deepslate_pillar", false, false),
			new Material("granite", "granite", "cobbled_granite", "polished_granite",
					"granite_bricks", "chiseled_granite", "granite_pillar", false, true),
			new Material("diorite", "diorite", "cobbled_diorite", "polished_diorite",
					"diorite_bricks", "chiseled_diorite", "diorite_pillar", false, true),
			new Material("andesite", "andesite", "cobbled_andesite", "polished_andesite",
					"andesite_bricks", "chiseled_andesite", "andesite_pillar", false, true),
			new Material("tuff", "tuff", "cobbled_tuff", "polished_tuff", "tuff_bricks",
					"chiseled_tuff", "tuff_pillar", false, true),
			new Material("calcite", "calcite", "cobbled_calcite", "polished_calcite",
					"calcite_bricks", "chiseled_calcite", "calcite_pillar", false, true),
			new Material("blackstone", "blackstone", "cobbled_blackstone", "polished_blackstone",
					"polished_blackstone_bricks", "chiseled_polished_blackstone", "blackstone_pillar",
					false, false), // nether drops preserved: cobbled via stonecutter cut instead
			new Material("basalt", "basalt", "cobbled_basalt", "polished_basalt", "basalt_bricks",
					"chiseled_basalt", "basalt", true, false), // raw IS the pillar; nether drops preserved
			new Material("end_stone", "end_stone", "cobbled_end_stone", "polished_end_stone",
					"end_stone_bricks", "chiseled_end_stone", "end_stone_pillar", false, true),
			new Material("dripstone", "dripstone_block", "cobbled_dripstone", "polished_dripstone",
					"dripstone_bricks", "chiseled_dripstone", "dripstone_pillar", false, true),
			new Material("sandstone", "sandstone", "cobbled_sandstone", "smooth_sandstone",
					"sandstone_bricks", "chiseled_sandstone", "sandstone_pillar", false, true),
			new Material("red_sandstone", "red_sandstone", "cobbled_red_sandstone",
					"smooth_red_sandstone", "red_sandstone_bricks", "chiseled_red_sandstone",
					"red_sandstone_pillar", false, true),
			// 26.2's newest stones — near-complete vanilla kits already; volcanic-dry, so no mossy.
			new Material("cinnabar", "cinnabar", "cobbled_cinnabar", "polished_cinnabar",
					"cinnabar_bricks", "chiseled_cinnabar", "cinnabar_pillar", false, true),
			new Material("sulfur", "sulfur", "cobbled_sulfur", "polished_sulfur", "sulfur_bricks",
					"chiseled_sulfur", "sulfur_pillar", false, true),
			// Tier 2 (S1c): crafted/dimensional masonry whose raw drops itself — reduced kit
			// (no cobbled line, no mossy, vanilla drops kept).
			new Material("netherrack", "netherrack", "", "polished_netherrack", "nether_bricks",
					"chiseled_nether_bricks", "netherrack_pillar", false, false, true),
			new Material("quartz", "quartz_block", "", "smooth_quartz", "quartz_bricks",
					"chiseled_quartz_block", "quartz_pillar", false, false, true),
			new Material("prismarine", "prismarine", "", "dark_prismarine", "prismarine_bricks",
					"chiseled_prismarine", "prismarine_pillar", false, false, true),
			new Material("purpur", "purpur_block", "", "polished_purpur", "purpur_bricks",
					"chiseled_purpur", "purpur_pillar", false, false, true),
			new Material("packed_mud", "packed_mud", "", "polished_packed_mud", "mud_bricks",
					"chiseled_packed_mud", "packed_mud_pillar", false, false, true));

	/** material -> its newly-registered kit blocks, kit order (for creative tabs + tests). */
	public static final Map<Block, List<Block>> NEW_FORMS = new LinkedHashMap<>();

	/** All shaped names in kit order for one material: [line, line_stairs, line_slab, line_wall]. */
	private static List<String> lines(Material m) {
		List<String> names = new ArrayList<>();
		String rawLine = switch (m.name()) {
			case "dripstone" -> "dripstone";
			case "quartz" -> "quartz";
			case "purpur" -> "purpur";
			default -> m.raw();
		};
		names.add(m.raw());
		names.add(rawLine + "_stairs");
		names.add(rawLine + "_slab");
		names.add(rawLine + "_wall");
		for (String base : m.reducedKit() ? List.of(m.polished(), m.bricks())
				: List.of(m.cobbled(), m.polished(), m.bricks())) {
			String stem = base.endsWith("s") && base.equals(m.bricks()) ? base.substring(0, base.length() - 1) : base;
			names.add(base);
			names.add(stem + "_stairs");
			names.add(stem + "_slab");
			names.add(stem + "_wall");
		}
		names.add(m.cracked());
		names.add(m.chiseled());
		if (!m.rawIsPillar()) {
			names.add(m.pillar());
		}
		if (m.mossy()) { // S1b: the mossy axis — mossy cobbled + mossy bricks lines
			for (String base : List.of("mossy_" + m.cobbled(), "mossy_" + m.bricks())) {
				String stem = base.endsWith("s") ? base.substring(0, base.length() - 1) : base;
				names.add(base);
				names.add(stem + "_stairs");
				names.add(stem + "_slab");
				names.add(stem + "_wall");
			}
		}
		return names;
	}

	/**
	 * Every kit item (vanilla or ours) -> its material key ("granite", "sulfur", ...), for
	 * {@code VariantGroups.keyFor}. Exact ids from the naming tables — no substring matching, so
	 * "sandstone" never collides with "stone".
	 */
	public static Map<net.minecraft.world.item.Item, String> materialItemKeys() {
		Map<net.minecraft.world.item.Item, String> keys = new HashMap<>();
		for (Material m : MATERIALS) {
			for (String name : lines(m)) {
				Identifier vanilla = Identifier.withDefaultNamespace(name);
				Identifier ours = com.mythstack.MythStack.id(name);
				Identifier id = BuiltInRegistries.ITEM.containsKey(vanilla) ? vanilla
						: BuiltInRegistries.ITEM.containsKey(ours) ? ours : null;
				if (id != null) {
					keys.put(BuiltInRegistries.ITEM.getValue(id), m.name());
				}
			}
		}
		return keys;
	}

	static void initialize() {
		for (Material m : MATERIALS) {
			Block raw = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(m.raw()));
			List<Block> added = new ArrayList<>();
			Block lineBase = raw; // the full cube each shaped form derives from, tracked in order
			for (String name : lines(m)) {
				Identifier vanilla = Identifier.withDefaultNamespace(name);
				if (BuiltInRegistries.BLOCK.containsKey(vanilla)) {
					Block existing = BuiltInRegistries.BLOCK.getValue(vanilla);
					if (!name.endsWith("_stairs") && !name.endsWith("_slab") && !name.endsWith("_wall")) {
						lineBase = existing;
					}
					continue;
				}
				BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(lineBase);
				Block block;
				if (name.endsWith("_stairs")) {
					Block base = lineBase;
					block = ModBlocks.register(name, p -> new StairBlock(base.defaultBlockState(), p), props, true);
				} else if (name.endsWith("_slab")) {
					block = ModBlocks.register(name, SlabBlock::new, props, true);
				} else if (name.endsWith("_wall")) {
					block = ModBlocks.register(name, WallBlock::new, props, true);
				} else if (name.equals(m.pillar())) {
					block = ModBlocks.register(name, RotatedPillarBlock::new,
							BlockBehaviour.Properties.ofFullCopy(raw), true);
				} else {
					block = ModBlocks.register(name, Block::new, props, true);
					lineBase = block;
				}
				added.add(block);
			}
			if (!added.isEmpty()) {
				NEW_FORMS.put(raw, List.copyOf(added));
			}
		}
		if (NEW_FORMS.values().stream().mapToInt(List::size).sum() != 251) {
			throw new IllegalStateException("stone kit expected 251 new blocks, got "
					+ NEW_FORMS.values().stream().mapToInt(List::size).sum());
		}
	}
}
