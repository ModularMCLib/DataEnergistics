package com.fish_dan_.data_energistics.core.mixins.com.brandon3055.draconicevolution.entity.guardian;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.technology.draconicevolution.orbital.GuardianEncounterCleanup;

import com.brandon3055.draconicevolution.entity.guardian.GuardianFightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@RequireMods("draconicevolution")
@Mixin(value = GuardianFightManager.class, remap = false)
public interface GuardianEncounterCleanupInvoker extends GuardianEncounterCleanup {

    @Override
    @Invoker("cleanUpAndDispose")
    void dataEnergistics$releaseErasedEncounter();
}
