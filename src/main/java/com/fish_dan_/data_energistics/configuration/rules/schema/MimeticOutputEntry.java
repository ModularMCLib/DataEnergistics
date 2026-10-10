package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;

/** One complete data-mimetic output row. Values are textual registry/enum ids at the configuration boundary. */
public record MimeticOutputEntry(
                                 DataType dataType,
                                 String recordedId,
                                 OutputFamily outputFamily,
                                 OutputKeyKind keyKind,
                                 String keyId,
                                 OutputAmountMode amountMode,
                                 long amount) {

    public MimeticOutputEntry {
        recordedId = text(recordedId);
        keyId = text(keyId);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
