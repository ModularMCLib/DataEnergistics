package com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.viewer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import org.jspecify.annotations.Nullable;

/** Resolves viewer rituals from their native registry; loaded only when Forbidden Arcanus is present. */
public final class HephaestusRitualIdentity {

    private HephaestusRitualIdentity() {}

    public static @Nullable ResourceLocation resolve(Object recipe, RegistryAccess registries) {
        if (!(recipe instanceof Ritual ritual)) return null;
        return registries.registryOrThrow(FARegistries.RITUAL).getKey(ritual);
    }
}
