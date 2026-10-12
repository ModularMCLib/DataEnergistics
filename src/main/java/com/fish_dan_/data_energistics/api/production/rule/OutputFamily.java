package com.fish_dan_.data_energistics.api.production.rule;

/** Only valid rules in a family replace that family's default. EXTRA always adds resources. */
public enum OutputFamily {
    LOOT,
    EXPERIENCE,
    BLOOD,
    EXTRA
}
