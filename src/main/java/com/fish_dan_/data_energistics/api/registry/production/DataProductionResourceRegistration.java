package com.fish_dan_.data_energistics.api.registry.production;

import appeng.api.stacks.AEKey;

import net.minecraft.resources.ResourceLocation;

/**
 * Declares one AE key that may be referenced by data-production rules.
 *
 * <p>
 * The registration is frozen with the common plugin snapshot. Rule compilation only consumes the frozen key, so
 * an optional integration can disappear without making the core rule parser load that integration's classes.
 * </p>
 */
public record DataProductionResourceRegistration(ResourceLocation id, AEKey key) {

    public DataProductionResourceRegistration {
        if (id == null || key == null) {
            throw new IllegalArgumentException("Data-production resource id and key are required");
        }
    }
}
