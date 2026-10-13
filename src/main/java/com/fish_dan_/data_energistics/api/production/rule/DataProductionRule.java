package com.fish_dan_.data_energistics.api.production.rule;

import appeng.api.stacks.AEKey;

/** A resolved, available output row; a zero amount intentionally suppresses its family's default. */
public record DataProductionRule(OutputFamily family, AEKey key, OutputAmountMode amountMode, long amount) {

    public DataProductionRule {
        if (family == null || key == null || amountMode == null || amount < 0) {
            throw new IllegalArgumentException("Production rule fields are required and amount cannot be negative");
        }
    }

    public long evaluate(DataProductionContext context) {
        return switch (amountMode) {
            case FIXED -> amount;
            case VANILLA_EXPERIENCE -> Math.multiplyExact(context.vanillaExperience(), amount);
            case CURRENT_HEALTH -> DataProductionContext.roundAmount(context.currentHealth() * amount);
            case EFFECTIVE_DAMAGE -> DataProductionContext.roundAmount(context.effectiveDamage() * amount);
        };
    }
}
