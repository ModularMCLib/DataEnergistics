package com.fish_dan_.data_energistics.configuration.rules.codec;

import com.fish_dan_.data_energistics.api.production.rule.DataProductionRule;
import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ConfiguredStack;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ExtractorOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ItemRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.MimeticOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.OutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.RuleFormatException;
import com.fish_dan_.data_energistics.configuration.rules.schema.CarrierRuleSchema;
import com.fish_dan_.data_energistics.configuration.rules.schema.CarrierRuleEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.ExtractorOutputEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.MimeticOutputEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.OutputRuleEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.OutputRuleSchema;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.nio.file.Path;

/** Converts typed configuration records into complete immutable rule snapshots. */
public final class DataExtractorRuleEntries {

    private DataExtractorRuleEntries() {}

    public static LoadedRules compile(
                                      CarrierRuleEntry[] carriers,
                                      OutputRuleEntry[] outputs,
                                      MimeticOutputEntry[] mimeticOutputs,
                                      ExtractorOutputEntry[] extractorOutputs,
                                      Path source) throws RuleFormatException {
        ObjectList<ItemRule> carrierRules = parseCarrierEntries(carriers, source);
        ObjectList<MimeticOutputRule> mimetic = mimeticOutputs.length > 0
                ? parseMimeticOutputs(mimeticOutputs, source)
                : migrateLegacyOutputs(outputs, source);
        return new LoadedRules(carrierRules, parseOutputEntries(outputs, source), mimetic,
                parseExtractorOutputs(extractorOutputs, source));
    }

    public static LoadedRules compile(
                                      CarrierRuleSchema carriers,
                                      OutputRuleSchema outputs,
                                      Path source) throws RuleFormatException {
        return new LoadedRules(parseCarriers(carriers, source), parseOutputs(outputs, source));
    }

    private static ObjectList<ItemRule> parseCarrierEntries(CarrierRuleEntry[] entries, Path source) throws RuleFormatException {
        ObjectArrayList<ItemRule> rules = new ObjectArrayList<>(entries.length);
        var indexes = new Object2IntLinkedOpenHashMap<CarrierKey>();
        indexes.defaultReturnValue(-1);
        for (int index = 0; index < entries.length; index++) {
            String path = "carrierRules[" + index + "]";
            CarrierRuleEntry entry = entries[index];
            Slot slot = requireEnum(source, path + ".slot", entry.slot());
            DataType dataType = requireEnum(source, path + ".dataType", entry.dataType());
            ResourceLocation inputItem = parseId(source, path + ".inputItem", entry.inputItem());
            ResourceLocation recordedItem = parseId(source, path + ".recordedItem", entry.recordedItem());
            float progress = positive(source, path + ".progressPerItem", entry.progressPerItem());
            float required = positive(source, path + ".requiredAmount", entry.requiredAmount());
            CarrierKey key = new CarrierKey(slot, inputItem);
            int previous = indexes.putIfAbsent(key, index);
            if (previous != -1) {
                throw invalid(source, path, "duplicate carrier row; the same slot and input item first appear at carrierRules[" + previous + "]",
                        inputItem.toString(), "keep exactly one carrier row for this slot and input item");
            }
            rules.add(new ItemRule(slot, dataType, inputItem, recordedItem, progress, required));
        }
        return ObjectLists.unmodifiable(rules);
    }

    private static ObjectList<OutputRule> parseOutputEntries(OutputRuleEntry[] entries, Path source) throws RuleFormatException {
        Object2ObjectMap<OutputKey, OutputRows> grouped = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < entries.length; index++) {
            String path = "outputRules[" + index + "]";
            OutputRuleEntry entry = entries[index];
            DataType dataType = requireEnum(source, path + ".dataType", entry.dataType());
            ResourceLocation recordedItem = parseId(source, path + ".recordedItem", entry.recordedItem());
            ResourceLocation item = parseId(source, path + ".item", entry.item());
            if (entry.count() <= 0) {
                throw invalid(source, path + ".count", "output count must be positive", Integer.toString(entry.count()),
                        "use an integer between 1 and " + Integer.MAX_VALUE);
            }
            grouped.computeIfAbsent(new OutputKey(dataType, recordedItem), ignored -> new OutputRows())
                    .add(source, path, item, entry.count(), index);
        }
        ObjectArrayList<OutputRule> rules = new ObjectArrayList<>(grouped.size());
        for (Object2ObjectMap.Entry<OutputKey, OutputRows> entry : grouped.object2ObjectEntrySet()) {
            OutputKey key = entry.getKey();
            rules.add(new OutputRule(key.dataType(), key.recordedItem(), entry.getValue().stacks()));
        }
        return ObjectLists.unmodifiable(rules);
    }

    private static ObjectList<MimeticOutputRule> migrateLegacyOutputs(OutputRuleEntry[] entries, Path source) throws RuleFormatException {
        ObjectList<OutputRule> legacy = parseOutputEntries(entries, source);
        ObjectArrayList<MimeticOutputRule> migrated = new ObjectArrayList<>();
        for (OutputRule output : legacy) {
            for (ConfiguredStack stack : output.outputs()) {
                var key = DataProductionKeyResolver.resolve(OutputKeyKind.ITEM, stack.itemId());
                if (key != null) {
                    migrated.add(new MimeticOutputRule(output.dataType(), output.recordedId(),
                            new DataProductionRule(OutputFamily.LOOT, key, OutputAmountMode.FIXED, stack.count())));
                }
            }
        }
        return ObjectLists.unmodifiable(migrated);
    }

    public static LoadedRules compile(
                                      CarrierRuleSchema carriers,
                                      OutputRuleSchema legacyOutputs,
                                      MimeticOutputEntry[] mimeticOutputs,
                                      ExtractorOutputEntry[] extractorOutputs,
                                      Path source) throws RuleFormatException {
        ObjectList<ItemRule> carrierRules = parseCarriers(carriers, source);
        ObjectList<MimeticOutputRule> mimetic = mimeticOutputs.length > 0 ? parseMimeticOutputs(mimeticOutputs, source) : migrateLegacyOutputs(legacyOutputs, source);
        return new LoadedRules(carrierRules, parseOutputs(legacyOutputs, source), mimetic,
                parseExtractorOutputs(extractorOutputs, source));
    }

    private static ObjectList<MimeticOutputRule> migrateLegacyOutputs(OutputRuleSchema schema, Path source) throws RuleFormatException {
        ObjectList<OutputRule> legacy = parseOutputs(schema, source);
        ObjectArrayList<MimeticOutputRule> migrated = new ObjectArrayList<>();
        for (OutputRule output : legacy) {
            for (ConfiguredStack stack : output.outputs()) {
                ResourceLocation itemId = stack.itemId();
                var key = DataProductionKeyResolver.resolve(OutputKeyKind.ITEM, itemId);
                if (key != null) {
                    migrated.add(new MimeticOutputRule(output.dataType(), output.recordedId(),
                            new DataProductionRule(OutputFamily.LOOT, key, OutputAmountMode.FIXED, stack.count())));
                }
            }
        }
        return ObjectLists.unmodifiable(migrated);
    }

    private static ObjectList<MimeticOutputRule> parseMimeticOutputs(MimeticOutputEntry[] entries, Path source) throws RuleFormatException {
        ObjectArrayList<MimeticOutputRule> rules = new ObjectArrayList<>();
        for (int index = 0; index < entries.length; index++) {
            String path = "mimeticOutputs[" + index + "]";
            MimeticOutputEntry entry = entries[index];
            DataType dataType = requireEnum(source, path + ".dataType", entry.dataType());
            OutputFamily family = requireEnum(source, path + ".outputFamily", entry.outputFamily());
            OutputKeyKind kind = requireEnum(source, path + ".keyKind", entry.keyKind());
            OutputAmountMode mode = requireEnum(source, path + ".amountMode", entry.amountMode());
            ResourceLocation recordedId = parseId(source, path + ".recordedId", entry.recordedId());
            ResourceLocation keyId = parseId(source, path + ".keyId", entry.keyId());
            long amount = nonNegative(source, path + ".amount", entry.amount());
            var key = DataProductionKeyResolver.resolve(kind, keyId);
            if (key != null) {
                rules.add(new MimeticOutputRule(dataType, recordedId, new DataProductionRule(family, key, mode, amount)));
            }
        }
        return ObjectLists.unmodifiable(rules);
    }

    private static ObjectList<ExtractorOutputRule> parseExtractorOutputs(ExtractorOutputEntry[] entries, Path source) throws RuleFormatException {
        ObjectArrayList<ExtractorOutputRule> rules = new ObjectArrayList<>();
        for (int index = 0; index < entries.length; index++) {
            String path = "extractorOutputs[" + index + "]";
            ExtractorOutputEntry entry = entries[index];
            ObjectArrayList<ResourceLocation> weaponItems = parseOptionalIds(source, path + ".weaponItems", entry.weaponItems());
            ObjectArrayList<TagKey<net.minecraft.world.item.Item>> weaponTags = parseOptionalTags(source, path + ".weaponTags", entry.weaponTags());
            ObjectArrayList<ResourceLocation> targets = parseOptionalIds(source, path + ".targetEntityIds", entry.targetEntityIds());
            if (weaponItems.isEmpty() && weaponTags.isEmpty()) {
                throw invalid(source, path, "weapon matcher must contain an item or tag", "empty", "fill weaponItems or weaponTags");
            }
            OutputFamily family = requireEnum(source, path + ".outputFamily", entry.outputFamily());
            OutputKeyKind kind = requireEnum(source, path + ".keyKind", entry.keyKind());
            OutputAmountMode mode = requireEnum(source, path + ".amountMode", entry.amountMode());
            ResourceLocation keyId = parseId(source, path + ".keyId", entry.keyId());
            long amount = nonNegative(source, path + ".amount", entry.amount());
            var key = DataProductionKeyResolver.resolve(kind, keyId);
            if (key != null) {
                rules.add(new ExtractorOutputRule(weaponItems, weaponTags, targets,
                        new DataProductionRule(family, key, mode, amount)));
            }
        }
        return ObjectLists.unmodifiable(rules);
    }

    private static ObjectArrayList<ResourceLocation> parseOptionalIds(Path source, String path, String[] values) throws RuleFormatException {
        ObjectArrayList<ResourceLocation> result = new ObjectArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(parseId(source, path, value.trim()));
            }
        }
        return result;
    }

    private static ObjectArrayList<TagKey<net.minecraft.world.item.Item>> parseOptionalTags(Path source, String path, String[] values) throws RuleFormatException {
        ObjectArrayList<TagKey<net.minecraft.world.item.Item>> result = new ObjectArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(TagKey.create(Registries.ITEM, parseId(source, path, value.trim())));
            }
        }
        return result;
    }

    private static long nonNegative(Path source, String path, long value) throws RuleFormatException {
        if (value < 0) {
            throw invalid(source, path, "amount must not be negative", Long.toString(value), "use zero or a positive long");
        }
        return value;
    }

    private static ObjectList<ItemRule> parseCarriers(CarrierRuleSchema schema, Path source) throws RuleFormatException {
        int rowCount = schema.slots.length;
        requireLength(source, "carrierRules.dataTypes", rowCount, schema.dataTypes.length);
        requireLength(source, "carrierRules.inputItems", rowCount, schema.inputItems.length);
        requireLength(source, "carrierRules.recordedItems", rowCount, schema.recordedItems.length);
        requireLength(source, "carrierRules.progressPerItems", rowCount, schema.progressPerItems.length);
        requireLength(source, "carrierRules.requiredAmounts", rowCount, schema.requiredAmounts.length);

        ObjectArrayList<ItemRule> rules = new ObjectArrayList<>(rowCount);
        var indexes = new Object2IntLinkedOpenHashMap<CarrierKey>();
        indexes.defaultReturnValue(-1);
        for (int index = 0; index < rowCount; index++) {
            String path = "carrierRules[" + index + "]";
            Slot slot = requireEnum(source, path + ".slot", schema.slots[index]);
            DataType dataType = requireEnum(source, path + ".dataType", schema.dataTypes[index]);
            ResourceLocation inputItem = parseId(source, path + ".inputItem", schema.inputItems[index]);
            ResourceLocation recordedItem = parseId(source, path + ".recordedItem", schema.recordedItems[index]);
            float progress = positive(source, path + ".progressPerItem", schema.progressPerItems[index]);
            float required = positive(source, path + ".requiredAmount", schema.requiredAmounts[index]);

            CarrierKey key = new CarrierKey(slot, inputItem);
            int previous = indexes.putIfAbsent(key, index);
            if (previous != -1) {
                throw invalid(
                        source,
                        path,
                        "duplicate carrier row; the same slot and input item first appear at carrierRules[" +
                                previous + "]",
                        inputItem.toString(),
                        "keep exactly one carrier row for this slot and input item");
            }
            rules.add(new ItemRule(slot, dataType, inputItem, recordedItem, progress, required));
        }
        return ObjectLists.unmodifiable(rules);
    }

    private static ObjectList<OutputRule> parseOutputs(OutputRuleSchema schema, Path source) throws RuleFormatException {
        int rowCount = schema.dataTypes.length;
        requireLength(source, "outputRules.recordedItems", rowCount, schema.recordedItems.length);
        requireLength(source, "outputRules.items", rowCount, schema.items.length);
        requireLength(source, "outputRules.counts", rowCount, schema.counts.length);

        Object2ObjectMap<OutputKey, OutputRows> grouped = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < rowCount; index++) {
            String path = "outputRules[" + index + "]";
            DataType dataType = requireEnum(source, path + ".dataType", schema.dataTypes[index]);
            ResourceLocation recordedItem = parseId(source, path + ".recordedItem", schema.recordedItems[index]);
            ResourceLocation item = parseId(source, path + ".item", schema.items[index]);
            int count = schema.counts[index];
            if (count <= 0) {
                throw invalid(
                        source,
                        path + ".count",
                        "output count must be positive",
                        Integer.toString(count),
                        "use an integer between 1 and " + Integer.MAX_VALUE);
            }
            grouped.computeIfAbsent(new OutputKey(dataType, recordedItem), ignored -> new OutputRows())
                    .add(source, path, item, count, index);
        }

        ObjectArrayList<OutputRule> rules = new ObjectArrayList<>(grouped.size());
        for (Object2ObjectMap.Entry<OutputKey, OutputRows> entry : grouped.object2ObjectEntrySet()) {
            OutputKey key = entry.getKey();
            rules.add(new OutputRule(key.dataType(), key.recordedItem(), entry.getValue().stacks()));
        }
        return ObjectLists.unmodifiable(rules);
    }

    private static void requireLength(Path source, String path, int expected, int actual) throws RuleFormatException {
        if (actual != expected) {
            throw invalid(
                    source,
                    path,
                    "parallel rule arrays must have the same length",
                    "expected=" + expected + ", actual=" + actual,
                    "add or remove entries so every array in this rule group has the same size");
        }
    }

    private static <E extends Enum<E>> E requireEnum(Path source, String path, E value) throws RuleFormatException {
        if (value == null) {
            throw invalid(source, path, "enum value must not be null", "null", "select one supported value");
        }
        return value;
    }

    private static ResourceLocation parseId(Path source, String path, String value) throws RuleFormatException {
        if (value == null || value.isBlank()) {
            throw invalid(source, path, "registry id must not be blank", value, "use namespace:path");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw invalid(source, path, "invalid registry id", value, "use lowercase namespace:path syntax");
        }
        return parsed;
    }

    private static float positive(Path source, String path, float value) throws RuleFormatException {
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw invalid(
                    source,
                    path,
                    "value must be finite and positive",
                    Float.toString(value),
                    "use a finite number greater than zero");
        }
        return value;
    }

    private static RuleFormatException invalid(
                                               Path source,
                                               String path,
                                               String violation,
                                               String actual,
                                               String repair) {
        return new RuleFormatException(source, path, violation, actual, repair);
    }

    private record CarrierKey(Slot slot, ResourceLocation inputItem) {}

    private record OutputKey(DataType dataType, ResourceLocation recordedItem) {}

    private static final class OutputRows {

        private final Object2ObjectMap<ResourceLocation, IndexedStack> rows = new Object2ObjectLinkedOpenHashMap<>();

        void add(Path source, String path, ResourceLocation item, int count, int index) throws RuleFormatException {
            IndexedStack previous = this.rows.get(item);
            if (previous == null) {
                this.rows.put(item, new IndexedStack(index, new ConfiguredStack(item, count)));
            } else if (previous.stack().count() != count) {
                throw invalid(
                        source,
                        path,
                        "conflicting count for an output first declared at outputRules[" + previous.index() + "]",
                        item + "@" + count,
                        "keep one count for this output item");
            }
        }

        ObjectList<ConfiguredStack> stacks() {
            return ObjectLists.unmodifiable(new ObjectArrayList<>(this.rows.values().stream().map(IndexedStack::stack).toList()));
        }
    }

    private record IndexedStack(int index, ConfiguredStack stack) {}
}
