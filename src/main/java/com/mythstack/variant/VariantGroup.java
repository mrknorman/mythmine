package com.mythstack.variant;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * A set of interchangeable variants of a single wood form (e.g. all logs, or all planks).
 *
 * <p>Membership is defined by a {@link TagKey} — preferably an existing vanilla tag (e.g.
 * {@code #minecraft:planks}) so we never hand-maintain variant lists; modded woods that tag
 * in are picked up for free. The {@code canonical} member is the deterministic default used
 * for crafting output and as the host item of a mixed pile (see docs/IMPLEMENTATION_PLAN.md,
 * "hosted-on-canonical").
 */
public record VariantGroup(Identifier id, TagKey<Item> members, Item canonical) {

	/** True if {@code item} is a member of this group and is not a deferred material (nether/bamboo). */
	public boolean contains(Item item) {
		return BuiltInRegistries.ITEM.wrapAsHolder(item).is(members)
				&& !VariantGroups.isDeferredMaterial(item);
	}
}
