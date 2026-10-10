package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;

/** One complete carrier-recording rule. */
public record CarrierRuleEntry(
                              Slot slot,
                              DataType dataType,
                              String inputItem,
                              String recordedItem,
                              float progressPerItem,
                              float requiredAmount) {

    public CarrierRuleEntry {
        inputItem = text(inputItem);
        recordedItem = text(recordedItem);
    }

    public static CarrierRuleEntry[] defaults(DefaultRuleValues defaults) {
        int rowCount = defaults.cropRules().size() + 2;
        CarrierRuleEntry[] entries = new CarrierRuleEntry[rowCount];
        int index = 0;
        for (DefaultRuleValues.CropRule crop : defaults.cropRules()) {
            entries[index++] = new CarrierRuleEntry(
                    Slot.CROP,
                    DataType.CROP,
                    crop.inputItem().toString(),
                    crop.recordedItem().toString(),
                    crop.progressPerItem(),
                    defaults.cropRequiredAmount());
        }
        entries[index++] = new CarrierRuleEntry(
                Slot.CROP,
                DataType.CROP,
                "minecraft:oak_sapling",
                "minecraft:oak_sapling",
                1.0F,
                defaults.cropRequiredAmount());
        entries[index] = new CarrierRuleEntry(
                Slot.ORE,
                DataType.ORE,
                "minecraft:raw_gold",
                "minecraft:gold_ore",
                1.0F,
                defaults.oreRequiredAmount());
        return entries;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
