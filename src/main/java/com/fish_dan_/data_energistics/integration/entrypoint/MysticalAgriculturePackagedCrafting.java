package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.integration.crafting.packaged.magic.mysticalagriculture.AltarKind;
import com.fish_dan_.data_energistics.integration.crafting.packaged.magic.mysticalagriculture.MysticalAltarAdapter;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

/** Keeps optional MA classes behind the mod-gated plugin boundary. */
@DataEnergisticsEntrypoint(requiredMods = { "mysticalagriculture", "cucumber" })
public final class MysticalAgriculturePackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new MysticalAltarAdapter(AltarKind.INFUSION));
        registry.packagedCrafting().register(new MysticalAltarAdapter(AltarKind.AWAKENING));
    }
}
