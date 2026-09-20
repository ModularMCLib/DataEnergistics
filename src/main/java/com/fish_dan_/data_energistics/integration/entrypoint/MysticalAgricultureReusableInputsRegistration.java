package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.mysticalagriculture.reusable.MysticalAgricultureReusableInputs;

@DataEnergisticsEntrypoint(requiredMods = { "mysticalagriculture", "cucumber" })
public final class MysticalAgricultureReusableInputsRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.reusableInputs().register(new MysticalAgricultureReusableInputs());
    }
}
