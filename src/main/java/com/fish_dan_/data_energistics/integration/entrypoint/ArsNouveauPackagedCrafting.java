package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.arsnouveau.matching.ArsRecipeIngredientRoles;
import com.fish_dan_.data_energistics.integration.magic.arsnouveau.packaged.ArsMachineKind;
import com.fish_dan_.data_energistics.integration.magic.arsnouveau.packaged.ArsPedestalAdapter;
import com.fish_dan_.data_energistics.integration.magic.arsnouveau.reusable.ArsImbuementReusableInputs;

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
