package com.fish_dan_.data_energistics.configuration.rules.codec;

import com.fish_dan_.data_energistics.api.production.rule.DataProductionRule;
import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.CarrierRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ExtractorOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.MimeticOutputRule;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.RuleFormatException;
import com.fish_dan_.data_energistics.configuration.rules.schema.ExtractorOutputEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.MimeticCarrierEntry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;

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
        var rules = new Object2ObjectLinkedOpenHashMap<CarrierKey, CarrierRule>();
        ObjectArrayList<MimeticOutputRule> outputs = new ObjectArrayList<>();
        for (int index = 0; index < entries.length; index++) {
            String path = "carrierRules[" + index + "]";
            MimeticCarrierEntry entry = entries[index];
            DataType dataType = entry.dataType();
            var inputs = parseOptionalIds(source, path + ".inputIds", entry.inputIds());
            if (inputs.isEmpty()) {
                throw invalid(source, path + ".inputIds", "a carrier rule needs at least one input id",
                        "empty", "supply entity ids for MOB or item ids for ORE and CROP");
            }
            ResourceLocation recordedId = entry.recordedId().isBlank() ? null : parseId(source, path + ".recordedId", entry.recordedId());
            float progress = positive(source, path + ".progressPerInput", entry.progressPerInput());
            var settings = DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor;
            float required = entry.requirement() == 0.0F ? (float) switch (dataType) {
                case MOB -> settings.mobRequiredDamage;
                case ORE -> settings.oreRequiredAmount;
                case CROP -> settings.cropRequiredAmount;
            } : entry.requirement();
            required = positive(source, path + ".requirement", required);
            var recordedIds = new ObjectLinkedOpenHashSet<ResourceLocation>();
            for (ResourceLocation inputId : inputs) {
                ResourceLocation identity = recordedId == null ? inputId : recordedId;
                var rule = new CarrierRule(dataType, inputId, identity, progress, required);
                CarrierRule previous = rules.putIfAbsent(new CarrierKey(dataType, inputId), rule);
                if (previous != null && !previous.equals(rule)) {
                    throw invalid(source, path, "conflicting collection settings for the same input id",
                            inputId.toString(), "use the same identity, progress and requirement for additional output rows");
                }
                recordedIds.add(identity);
            }
            if (!entry.outputId().isBlank()) {
                ResourceLocation keyId = parseId(source, path + ".outputId", entry.outputId());
                long amount = nonNegative(source, path + ".amount", entry.amount());
                var key = DataProductionKeyResolver.resolve(entry.keyKind(), keyId);
                if (key != null) {
                    for (ResourceLocation identity : recordedIds) {
                        outputs.add(new MimeticOutputRule(dataType, identity,
                                new DataProductionRule(entry.outputFamily(), key, entry.amountMode(), amount)));
                    }
                }
            }
        }
        return new CarrierCompilation(new ObjectArrayList<>(rules.values()), outputs);
    }

    private static ObjectList<ExtractorOutputRule> parseExtractorOutputs(ExtractorOutputEntry[] entries, Path source) throws RuleFormatException {
        ObjectArrayList<ExtractorOutputRule> rules = new ObjectArrayList<>();
        for (int index = 0; index < entries.length; index++) {
            String path = "extractorOutputRules[" + index + "]";
            ExtractorOutputEntry entry = entries[index];
            if (entry.outputId().isBlank()) continue;
            ObjectArrayList<ResourceLocation> weaponItems = parseOptionalIds(source, path + ".weaponItems", entry.weaponItems());
            ObjectArrayList<TagKey<Item>> weaponTags = parseOptionalTags(source, path + ".weaponTags", entry.weaponTags());
            ObjectArrayList<ResourceLocation> targets = parseOptionalIds(source, path + ".targetEntityIds", entry.targetEntityIds());
            if (weaponItems.isEmpty() && weaponTags.isEmpty()) {
                throw invalid(source, path, "weapon matcher must contain an item or tag", "empty", "fill weaponItems or weaponTags");
            }
            OutputFamily family = entry.outputFamily();
            OutputKeyKind kind = entry.keyKind();
            OutputAmountMode mode = entry.amountMode();
            long amount = nonNegative(source, path + ".amount", entry.amount());
            ResourceLocation keyId = parseId(source, path + ".outputId", entry.outputId());
            var key = DataProductionKeyResolver.resolve(kind, keyId);
            if (key != null) {
                rules.add(new ExtractorOutputRule(weaponItems, weaponTags, targets,
                        new DataProductionRule(family, key, mode, amount)));
            }
        }
        return rules;
    }

    private static ObjectArrayList<ResourceLocation> parseOptionalIds(Path source, String path, String[] values) throws RuleFormatException {
        ObjectArrayList<ResourceLocation> result = new ObjectArrayList<>();
        for (String value : values) {
            if (!value.isBlank()) {
                result.add(parseId(source, path, value.trim()));
            }
        }
        return result;
    }

    private static ObjectArrayList<TagKey<Item>> parseOptionalTags(Path source, String path, String[] values) throws RuleFormatException {
        ObjectArrayList<TagKey<Item>> result = new ObjectArrayList<>();
        for (String value : values) {
            if (!value.isBlank()) {
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

    private static ResourceLocation parseId(Path source, String path, String value) throws RuleFormatException {
        if (value.isBlank()) {
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

    private record CarrierKey(DataType dataType, ResourceLocation inputId) {}

    private record CarrierCompilation(ObjectList<CarrierRule> carriers, ObjectList<MimeticOutputRule> outputs) {}
}
