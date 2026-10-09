package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyUnitConversion;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumSet;

/** Canonical resource declarations shared by every digital-supply adapter. */
public final class DigitalBiologicalResources {

    public static final DigitalSupplyResourceDefinition BLOOD = definition(BloodKey.INSTANCE);
    public static final DigitalSupplyResourceDefinition EXPERIENCE = definition(ExperienceKey.INSTANCE);

    private DigitalBiologicalResources() {}

    public static ObjectList<DigitalSupplyResourceDefinition> bloodAndExperience() {
        ObjectArrayList<DigitalSupplyResourceDefinition> result = new ObjectArrayList<>(2);
        result.add(BLOOD);
        result.add(EXPERIENCE);
        return result;
    }

    private static DigitalSupplyResourceDefinition definition(DigitalBiologicalResourceKey key) {
        return new DigitalSupplyResourceDefinition(
                key.getId(),
                key,
                key.getDisplayName(),
                DigitalSupplyUnitConversion.IDENTITY,
                true,
                EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET,
                        DigitalSupplyTransferDirection.TARGET_TO_NETWORK));
    }
}
