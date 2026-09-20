package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.technology.brandonscore.energy.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.technology.brandonscore.energy.BrandonsCoreEnergyEndpointIntegration;

@DataEnergisticsEntrypoint(requiredMods = "brandonscore")
public final class BrandonsCoreEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(new BrandonsCoreEnergyEndpointIntegration(new BrandonsCoreEnergyBridge()));
    }
}
