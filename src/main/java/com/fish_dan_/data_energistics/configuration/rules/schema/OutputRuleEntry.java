package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;

/** One complete legacy loot replacement rule, retained for configuration migration. */
public record OutputRuleEntry(DataType dataType, String recordedItem, String item, int count) {

    public OutputRuleEntry {
        recordedItem = text(recordedItem);
        item = text(item);
    }

    public static OutputRuleEntry[] defaults() {
        return new OutputRuleEntry[] {
                new OutputRuleEntry(DataType.CROP, "minecraft:oak_sapling", "minecraft:oak_log", 4),
                new OutputRuleEntry(DataType.CROP, "minecraft:oak_sapling", "minecraft:oak_leaves", 2),
                new OutputRuleEntry(DataType.CROP, "minecraft:oak_sapling", "minecraft:stick", 2),
                new OutputRuleEntry(DataType.CROP, "minecraft:oak_sapling", "minecraft:apple", 1),
                new OutputRuleEntry(DataType.ORE, "minecraft:gold_ore", "minecraft:gold_ore", 1)
        };
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
