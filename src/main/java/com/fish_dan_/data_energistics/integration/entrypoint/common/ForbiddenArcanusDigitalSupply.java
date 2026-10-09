package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.worldenergy.AeKeyTypeRegistration;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceRegistration;
import com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply.ForbiddenArcanusDigitalSupplyAdapter;
import com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply.ForbiddenArcanusEssenceKeyType;

import net.minecraft.resources.ResourceLocation;

@DataEnergisticsEntrypoint(requiredMods = "forbidden_arcanus")
public final class ForbiddenArcanusDigitalSupply implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.aeKeyTypes().register(new AeKeyTypeRegistration(
                ForbiddenArcanusEssenceKeyType.TYPE.getId(), ForbiddenArcanusEssenceKeyType.TYPE,
                ResourceLocation.fromNamespaceAndPath("data_energistics", "forbidden_arcanus/essence")));
        var adapter = new ForbiddenArcanusDigitalSupplyAdapter();
        registry.digitalSupplyInterfaces().register(new DigitalSupplyInterfaceRegistration(adapter.id(), adapter.resources(), adapter));
    }
}
