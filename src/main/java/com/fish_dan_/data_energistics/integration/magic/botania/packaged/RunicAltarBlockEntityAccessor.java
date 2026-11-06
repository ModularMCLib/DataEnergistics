package com.fish_dan_.data_energistics.integration.magic.botania.packaged;

/** Read-only readiness check, avoiding partially delivering a cycle during the altar's native cooldown. */
public interface RunicAltarBlockEntityAccessor {

    int dataEnergistics$craftingCooldown();
}
