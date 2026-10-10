package com.fish_dan_.data_energistics.configuration.rules.codec;

import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/** Resolves configured output ids at the untrusted configuration boundary without logging optional misses. */
final class DataProductionKeyResolver {

    private DataProductionKeyResolver() {}

    static @Nullable AEKey resolve(OutputKeyKind kind, ResourceLocation id) {
        return switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM.getOptional(id).map(item -> AEItemKey.of(new ItemStack(item))).orElse(null);
            case FLUID -> BuiltInRegistries.FLUID.getOptional(id).map(AEFluidKey::of).orElse(null);
            case RESOURCE -> resolveResource(id);
        };
    }

    private static @Nullable AEKey resolveResource(ResourceLocation id) {
        try {
            return DataEnergisticsEntrypointLoader.snapshot().dataProductionResources().find(id);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }
}
