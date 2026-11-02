package com.fish_dan_.data_energistics.common.plane.storage;

import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfigurationData;

import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** The field names retain the initial save shape while all values are decoded through their typed codecs. */
public record PlaneSavedState(PlaneConfigurationData configuration, ItemStack pendingItem, boolean faulted) {

    public static final Codec<PlaneSavedState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlaneConfigurationData.CODEC.optionalFieldOf("plane_configuration", PlaneConfigurationData.EMPTY).forGetter(PlaneSavedState::configuration),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("pending_formation_item", ItemStack.EMPTY).forGetter(PlaneSavedState::pendingItem),
            Codec.BOOL.optionalFieldOf("plane_faulted", false).forGetter(PlaneSavedState::faulted)).apply(instance, PlaneSavedState::new));
}
