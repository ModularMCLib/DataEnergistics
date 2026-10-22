package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

/** Collection requirements and optional mimetic outputs shared by one or more input ids. */
public record MimeticCarrierEntry(
                                  DataType dataType,
                                  String[] inputIds,
                                  String recordedId,
                                  float progressPerInput,
                                  float requirement,
                                  OutputFamily outputFamily,
                                  OutputKeyKind keyKind,
                                  String outputId,
                                  OutputAmountMode amountMode,
                                  long amount) {

    public MimeticCarrierEntry {
        inputIds = inputIds.clone();
    }

    public static MimeticCarrierEntry[] defaults(DefaultRuleValues defaults) {
        int rowCount = defaults.cropRules().size() + 6;
        MimeticCarrierEntry[] entries = new MimeticCarrierEntry[rowCount];
        int index = 0;
        for (DefaultRuleValues.CropRule crop : defaults.cropRules()) {
            entries[index++] = new MimeticCarrierEntry(
                    DataType.CROP,
                    new String[] { crop.inputItem().toString() },
                    crop.recordedItem().toString(),
                    crop.progressPerItem(),
                    defaults.cropRequiredAmount(),
                    OutputFamily.LOOT,
                    OutputKeyKind.ITEM,
                    "",
                    OutputAmountMode.FIXED,
                    1L);
        }
        var oakOutputs = new Object2LongLinkedOpenHashMap<String>();
        oakOutputs.put("minecraft:oak_log", 4L);
        oakOutputs.put("minecraft:oak_leaves", 2L);
        oakOutputs.put("minecraft:stick", 2L);
        oakOutputs.put("minecraft:apple", 1L);
        for (var output : oakOutputs.object2LongEntrySet()) {
            entries[index++] = new MimeticCarrierEntry(
                    DataType.CROP,
                    new String[] { "minecraft:oak_sapling" },
                    "minecraft:oak_sapling",
                    1.0F,
                    defaults.cropRequiredAmount(),
                    OutputFamily.LOOT,
                    OutputKeyKind.ITEM,
                    output.getKey(),
                    OutputAmountMode.FIXED,
                    output.getLongValue());
        }
        entries[index++] = new MimeticCarrierEntry(
                DataType.ORE,
                new String[] { "minecraft:raw_gold" },
                "minecraft:gold_ore",
                1.0F,
                defaults.oreRequiredAmount(),
                OutputFamily.LOOT,
                OutputKeyKind.ITEM,
                "minecraft:gold_ore",
                OutputAmountMode.FIXED,
                1L);
        entries[index] = new MimeticCarrierEntry(
                DataType.MOB,
                new String[] { "forbidden_arcanus:lost_soul" },
                "forbidden_arcanus:lost_soul",
                1.0F,
                64.0F,
                OutputFamily.LOOT,
                OutputKeyKind.ITEM,
                "",
                OutputAmountMode.FIXED,
                1L);
        return entries;
    }
}
