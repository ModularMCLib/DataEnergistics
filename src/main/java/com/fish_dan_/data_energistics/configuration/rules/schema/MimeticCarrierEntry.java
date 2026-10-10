package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;

/** One complete carrier rule; the output arrays at the same index form one production entry. */
public record MimeticCarrierEntry(
                                  Slot slot,
                                  DataType dataType,
                                  String inputItem,
                                  String recordedId,
                                  float progressPerItem,
                                  float requiredAmount,
                                  OutputFamily[] outputFamilies,
                                  OutputKeyKind[] keyKinds,
                                  String[] keyIds,
                                  OutputAmountMode[] amountModes,
                                  long[] amounts) {

    public MimeticCarrierEntry {
        inputItem = text(inputItem);
        recordedId = text(recordedId);
        outputFamilies = outputFamilies == null ? new OutputFamily[0] : outputFamilies.clone();
        keyKinds = keyKinds == null ? new OutputKeyKind[0] : keyKinds.clone();
        keyIds = keyIds == null ? new String[0] : keyIds.clone();
        amountModes = amountModes == null ? new OutputAmountMode[0] : amountModes.clone();
        amounts = amounts == null ? new long[0] : amounts.clone();
    }

    public static MimeticCarrierEntry[] defaults(DefaultRuleValues defaults) {
        int rowCount = defaults.cropRules().size() + 2;
        MimeticCarrierEntry[] entries = new MimeticCarrierEntry[rowCount];
        int index = 0;
        for (DefaultRuleValues.CropRule crop : defaults.cropRules()) {
            entries[index++] = new MimeticCarrierEntry(
                    Slot.CROP,
                    DataType.CROP,
                    crop.inputItem().toString(),
                    crop.recordedItem().toString(),
                    crop.progressPerItem(),
                    defaults.cropRequiredAmount(),
                    new OutputFamily[0],
                    new OutputKeyKind[0],
                    new String[0],
                    new OutputAmountMode[0],
                    new long[0]);
        }
        entries[index++] = new MimeticCarrierEntry(
                Slot.CROP,
                DataType.CROP,
                "minecraft:oak_sapling",
                "minecraft:oak_sapling",
                1.0F,
                defaults.cropRequiredAmount(),
                new OutputFamily[] { OutputFamily.LOOT, OutputFamily.LOOT, OutputFamily.LOOT, OutputFamily.LOOT },
                new OutputKeyKind[] { OutputKeyKind.ITEM, OutputKeyKind.ITEM, OutputKeyKind.ITEM, OutputKeyKind.ITEM },
                new String[] { "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:stick", "minecraft:apple" },
                new OutputAmountMode[] { OutputAmountMode.FIXED, OutputAmountMode.FIXED, OutputAmountMode.FIXED, OutputAmountMode.FIXED },
                new long[] { 4, 2, 2, 1 });
        entries[index] = new MimeticCarrierEntry(
                Slot.ORE,
                DataType.ORE,
                "minecraft:raw_gold",
                "minecraft:gold_ore",
                1.0F,
                defaults.oreRequiredAmount(),
                new OutputFamily[] { OutputFamily.LOOT },
                new OutputKeyKind[] { OutputKeyKind.ITEM },
                new String[] { "minecraft:gold_ore" },
                new OutputAmountMode[] { OutputAmountMode.FIXED },
                new long[] { 1 });
        return entries;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
