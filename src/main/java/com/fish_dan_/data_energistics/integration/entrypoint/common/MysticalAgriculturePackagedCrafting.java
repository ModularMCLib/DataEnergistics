package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.mysticalagriculture.packaged.AltarKind;
import com.fish_dan_.data_energistics.integration.magic.mysticalagriculture.packaged.MysticalAltarAdapter;

/** Keeps optional MA classes behind the mod-gated plugin boundary. */
@DataEnergisticsEntrypoint(requiredMods = { "mysticalagriculture", "cucumber" })
public final class MysticalAgriculturePackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new MysticalAltarAdapter(AltarKind.INFUSION));
        registry.packagedCrafting().register(new MysticalAltarAdapter(AltarKind.AWAKENING));
    }
}
