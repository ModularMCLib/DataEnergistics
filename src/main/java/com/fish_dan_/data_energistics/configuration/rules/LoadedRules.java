package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ExtractorOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ItemRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.MimeticOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.OutputRule;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** The complete immutable Data Extractor rule snapshot published to gameplay consumers. */
public record LoadedRules(
                          ObjectList<ItemRule> inputRules,
                          ObjectList<OutputRule> outputRules,
                          ObjectList<MimeticOutputRule> mimeticOutputs,
                          ObjectList<ExtractorOutputRule> extractorOutputs) {

    public LoadedRules(ObjectList<ItemRule> inputRules, ObjectList<OutputRule> outputRules) {
        this(inputRules, outputRules, ObjectList.of(), ObjectList.of());
    }

    public LoadedRules {
        inputRules = ObjectLists.unmodifiable(new ObjectArrayList<>(inputRules));
        outputRules = ObjectLists.unmodifiable(new ObjectArrayList<>(outputRules));
        mimeticOutputs = ObjectLists.unmodifiable(new ObjectArrayList<>(mimeticOutputs));
        extractorOutputs = ObjectLists.unmodifiable(new ObjectArrayList<>(extractorOutputs));
    }

    public static LoadedRules empty() {
        return new LoadedRules(ObjectList.of(), ObjectList.of(), ObjectList.of(), ObjectList.of());
    }
}
