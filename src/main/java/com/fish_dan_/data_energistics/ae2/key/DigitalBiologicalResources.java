package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyUnitConversion;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumSet;

/** Canonical resource declarations shared by every world-energy adapter. */
public final class DigitalBiologicalResources {

    public static final WorldEnergyResourceDefinition BLOOD = definition(BloodKey.INSTANCE);
    public static final WorldEnergyResourceDefinition EXPERIENCE = definition(ExperienceKey.INSTANCE);

    private DigitalBiologicalResources() {}

    public static ObjectList<WorldEnergyResourceDefinition> bloodAndExperience() {
        ObjectArrayList<WorldEnergyResourceDefinition> result = new ObjectArrayList<>(2);
        result.add(BLOOD);
        result.add(EXPERIENCE);
        return result;
    }

    private static WorldEnergyResourceDefinition definition(DigitalBiologicalResourceKey key) {
        return new WorldEnergyResourceDefinition(
                key.getId(),
                key,
                key.getDisplayName(),
                WorldEnergyUnitConversion.IDENTITY,
                true,
                EnumSet.of(WorldEnergyTransferDirection.NETWORK_TO_WORLD,
                        WorldEnergyTransferDirection.WORLD_TO_NETWORK));
    }
}
