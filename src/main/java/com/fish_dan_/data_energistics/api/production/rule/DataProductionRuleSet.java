package com.fish_dan_.data_energistics.api.production.rule;

import com.fish_dan_.data_energistics.ae2.key.BloodKey;
import com.fish_dan_.data_energistics.ae2.key.ExperienceKey;
import com.fish_dan_.data_energistics.api.production.DataProductionOutput;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.function.Supplier;

/** Immutable resolved rows with family-local coverage and deterministic declaration order. */
public final class DataProductionRuleSet {

    private static final DataProductionRuleSet EMPTY = new DataProductionRuleSet(ObjectList.of());
    private final ObjectList<DataProductionRule> rules;

    public DataProductionRuleSet(ObjectList<DataProductionRule> rules) {
        this.rules = ObjectLists.unmodifiable(new ObjectArrayList<>(rules));
    }

    public static DataProductionRuleSet empty() {
        return EMPTY;
    }

    public boolean replaces(OutputFamily family) {
        return rules.stream().anyMatch(rule -> rule.family() == family);
    }

    /**
     * Adds uncovered defaults, then all resolved rules in order. The loot supplier is called only without LOOT
     * coverage.
     * Extractors pass an empty loot supplier, and choose effective damage as their default blood measurement.
     */
    public DataProductionOutput produce(DataProductionContext context, Supplier<DataProductionOutput> defaultLoot,
                                        boolean bloodFromDamage) {
        DataProductionOutput.Accumulator result = DataProductionOutput.accumulator();
        if (!replaces(OutputFamily.LOOT)) {
            result.add(defaultLoot.get());
        }
        if (context.living()) {
            if (!replaces(OutputFamily.EXPERIENCE) && context.vanillaExperience() > 0) {
                result.add(ExperienceKey.INSTANCE, context.vanillaExperience());
            }
            long blood = DataProductionContext.roundAmount(bloodFromDamage ? context.effectiveDamage() : context.currentHealth());
            if (!replaces(OutputFamily.BLOOD) && blood > 0) {
                result.add(BloodKey.INSTANCE, blood);
            }
        }
        for (DataProductionRule rule : rules) {
            long amount = rule.evaluate(context);
            if (amount > 0) {
                result.add(rule.key(), amount);
            }
        }
        return result.build();
    }

    public ObjectList<DataProductionRule> rules() {
        return rules;
    }
}
