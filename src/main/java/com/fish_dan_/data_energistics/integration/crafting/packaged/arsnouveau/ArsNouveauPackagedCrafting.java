package com.fish_dan_.data_energistics.integration.crafting.packaged.arsnouveau;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.crafting.matching.arsnouveau.ArsRecipeIngredientRoles;
import com.fish_dan_.data_energistics.integration.crafting.reusable.arsnouveau.ArsImbuementReusableInputs;

@DataEnergisticsEntrypoint(requiredMods = "ars_nouveau")
public final class ArsNouveauPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.recipeMatching().register(new ArsRecipeIngredientRoles());
        registry.reusableInputs().register(new ArsImbuementReusableInputs());
        registry.packagedCrafting().register(new ArsPedestalAdapter(ArsMachineKind.APPARATUS));
        registry.packagedCrafting().register(new ArsPedestalAdapter(ArsMachineKind.IMBUEMENT));
    }
}
