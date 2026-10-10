package com.fish_dan_.data_energistics.configuration.rules.codec;

import com.fish_dan_.data_energistics.api.production.rule.DataProductionRule;
import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ExtractorOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ItemRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.MimeticOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.RuleFormatException;
import com.fish_dan_.data_energistics.configuration.rules.schema.ExtractorOutputEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.MimeticCarrierEntry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.nio.file.Path;

/** Converts typed configuration records into complete immutable rule snapshots. */
public final class DataExtractorRuleEntries {

    private DataExtractorRuleEntries() {}

    public static LoadedRules compile(
                                      MimeticCarrierEntry[] carriers,
                                      ExtractorOutputEntry[] extractorOutputs,
                                      Path source) throws RuleFormatException {
        CarrierCompilation parsed = parseMimeticCarriers(carriers, source);
        return new LoadedRules(parsed.carriers(), parsed.outputs(),
                parseExtractorOutputs(extractorOutputs, source));
    }

    private static CarrierCompilation parseMimeticCarriers(MimeticCarrierEntry[] entries, Path source) throws RuleFormatException {
        ObjectArrayList<ItemRule> rules = new ObjectArrayList<>(entries.length);
        ObjectArrayList<MimeticOutputRule> outputs = new ObjectArrayList<>();
        var indexes = new Object2IntLinkedOpenHashMap<CarrierKey>();
        indexes.defaultReturnValue(-1);
        for (int index = 0; index < entries.length; index++) {
            String path = "mimeticCarriers[" + index + "]";
            MimeticCarrierEntry entry = entries[index];
            Slot slot = requireEnum(source, path + ".slot", entry.slot());
            DataType dataType = requireEnum(source, path + ".dataType", entry.dataType());
            ResourceLocation inputItem = parseId(source, path + ".inputItem", entry.inputItem());
            ResourceLocation recordedItem = parseId(source, path + ".recordedId", entry.recordedId());
            float progress = positive(source, path + ".progressPerItem", entry.progressPerItem());
            float required = positive(source, path + ".requiredAmount", entry.requiredAmount());
            CarrierKey carrierKey = new CarrierKey(slot, inputItem);
            int previous = indexes.putIfAbsent(carrierKey, index);
            if (previous != -1) {
                throw invalid(source, path, "duplicate carrier row; the same slot and input item first appear at mimeticCarriers[" + previous + "]",
                        inputItem.toString(), "keep exactly one carrier row for this slot and input item");
            }
            rules.add(new ItemRule(slot, dataType, inputItem, recordedItem, progress, required));
            requireOutputLengths(entry, path, source);
            for (int outputIndex = 0; outputIndex < entry.outputFamilies().length; outputIndex++) {
                String outputPath = path + ".outputs[" + outputIndex + "]";
                OutputFamily family = requireEnum(source, outputPath + ".outputFamily", entry.outputFamilies()[outputIndex]);
                OutputKeyKind kind = requireEnum(source, outputPath + ".keyKind", entry.keyKinds()[outputIndex]);
                OutputAmountMode mode = requireEnum(source, outputPath + ".amountMode", entry.amountModes()[outputIndex]);
                ResourceLocation keyId = parseId(source, outputPath + ".keyId", entry.keyIds()[outputIndex]);
                long amount = nonNegative(source, outputPath + ".amount", entry.amounts()[outputIndex]);
                var key = DataProductionKeyResolver.resolve(kind, keyId);
                if (key != null) {
                    outputs.add(new MimeticOutputRule(dataType, recordedItem,
                            new DataProductionRule(family, key, mode, amount)));
                }
            }
        }
        return new CarrierCompilation(ObjectLists.unmodifiable(rules), ObjectLists.unmodifiable(outputs));
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

    private static void requireOutputLengths(MimeticCarrierEntry entry, String path, Path source) throws RuleFormatException {
        int expected = entry.outputFamilies().length;
        requireLength(source, path + ".keyKinds", expected, entry.keyKinds().length);
        requireLength(source, path + ".keyIds", expected, entry.keyIds().length);
        requireLength(source, path + ".amountModes", expected, entry.amountModes().length);
        requireLength(source, path + ".amounts", expected, entry.amounts().length);
    }

    private static void requireLength(Path source, String path, int expected, int actual) throws RuleFormatException {
        if (expected != actual) {
            throw invalid(source, path, "output arrays must have the same length",
                    "expected=" + expected + ", actual=" + actual,
                    "add or remove entries so every output array in this carrier has the same size");
        }
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

    private record CarrierCompilation(ObjectList<ItemRule> carriers, ObjectList<MimeticOutputRule> outputs) {}
}
