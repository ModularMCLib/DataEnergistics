package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.technology.modernindustrialization.energy.ModernIndustrializationEnergyBridge;
import com.fish_dan_.data_energistics.integration.technology.modernindustrialization.energy.ModernIndustrializationEnergyEndpointIntegration;

@DataEnergisticsEntrypoint(requiredMods = "modern_industrialization")
public final class ModernIndustrializationEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(new ModernIndustrializationEnergyEndpointIntegration(new ModernIndustrializationEnergyBridge()));
    }
}
