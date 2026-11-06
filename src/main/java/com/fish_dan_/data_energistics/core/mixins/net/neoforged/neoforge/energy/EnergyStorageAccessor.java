package com.fish_dan_.data_energistics.core.mixins.net.neoforged.neoforge.energy;

import net.neoforged.neoforge.energy.EnergyStorage;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the reference NeoForge energy storage state to the tower's unlimited energy adapter.
 *
 * <p>
 * The accessor avoids reflective mutation for the most common FE implementation while leaving receive and extract
 * permission checks on the public {@link EnergyStorage} API.
 */
@NullMarked
@Mixin(EnergyStorage.class)
public interface EnergyStorageAccessor {

    /**
     * Updates the current stored energy field after validation by the unlimited adapter.
     *
     * @param energy new stored FE amount
     */
    @Accessor("energy")
    void dataEnergistics$setEnergy(int energy);
}
