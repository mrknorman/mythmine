package com.mythstack.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mythstack.MythStack;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

/**
 * {@code mythstack:module} resource condition — data files (recipes, trades, worldgen) carry
 * {@code "fabric:load_conditions": [{"condition": "mythstack:module", "module": "terrain"}]} and
 * simply don't load when the module is off.
 */
public record ModuleCondition(String module) implements ResourceCondition {

	public static final MapCodec<ModuleCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(Codec.STRING.fieldOf("module").forGetter(ModuleCondition::module))
					.apply(instance, ModuleCondition::new));

	public static final ResourceConditionType<ModuleCondition> TYPE =
			ResourceConditionType.create(MythStack.id("module"), CODEC);

	public static void register() {
		ResourceConditions.register(TYPE);
	}

	@Override
	public ResourceConditionType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean test(@Nullable RegistryOps.RegistryInfoLookup registryLookup) {
		return ModConfig.enabled(module);
	}
}
