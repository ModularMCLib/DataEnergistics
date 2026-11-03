package com.fish_dan_.data_energistics.common.plane.storage;

import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfigurationData;

import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Typed codecs persist the configuration and any unfinished formation operation. */
public record PlaneSavedState(PlaneConfigurationData configuration, Optional<GenericStack> pendingResource,
                              ItemEnchantments enchantments, boolean faulted) {

    public static final Codec<PlaneSavedState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlaneConfigurationData.CODEC.optionalFieldOf("plane_configuration", PlaneConfigurationData.EMPTY).forGetter(PlaneSavedState::configuration),
            GenericStack.CODEC.validate(resource -> resource.amount() > 0 ? DataResult.success(resource) :
                    DataResult.error(() -> "Pending formation resource amount must be positive"))
                    .optionalFieldOf("pending_formation_resource").forGetter(PlaneSavedState::pendingResource),
            ItemEnchantments.CODEC.optionalFieldOf("enchantments", ItemEnchantments.EMPTY).forGetter(PlaneSavedState::enchantments),
            Codec.BOOL.optionalFieldOf("plane_faulted", false).forGetter(PlaneSavedState::faulted)).apply(instance, PlaneSavedState::new));
}
