package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.botania.matching.BotaniaRecipeIngredientRoles;
import com.fish_dan_.data_energistics.integration.magic.botania.packaged.BotaniaMachineAdapter;
import com.fish_dan_.data_energistics.integration.magic.botania.packaged.BotaniaMachineKind;

@DataEnergisticsEntrypoint(requiredMods = "botania")
public final class BotaniaPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.recipeMatching().register(new BotaniaRecipeIngredientRoles());
        for (BotaniaMachineKind kind : BotaniaMachineKind.values()) registry.packagedCrafting().register(new BotaniaMachineAdapter(kind));
    }
}
