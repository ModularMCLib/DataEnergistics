package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.CarrierRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ExtractorOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.MimeticOutputRule;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** The complete immutable Data Extractor rule snapshot published to gameplay consumers. */
public record LoadedRules(
                          ObjectList<CarrierRule> inputRules,
                          ObjectList<MimeticOutputRule> mimeticOutputs,
                          ObjectList<ExtractorOutputRule> extractorOutputs) {

    public LoadedRules {
        inputRules = ObjectLists.unmodifiable(new ObjectArrayList<>(inputRules));
        mimeticOutputs = ObjectLists.unmodifiable(new ObjectArrayList<>(mimeticOutputs));
        extractorOutputs = ObjectLists.unmodifiable(new ObjectArrayList<>(extractorOutputs));
    }

    public static LoadedRules empty() {
        return new LoadedRules(ObjectList.of(), ObjectList.of(), ObjectList.of());
    }
}
