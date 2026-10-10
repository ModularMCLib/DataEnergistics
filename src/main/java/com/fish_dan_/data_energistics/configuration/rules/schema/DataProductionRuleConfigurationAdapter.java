package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import dev.toma.configuration.config.adapter.TypeAdapter;
import dev.toma.configuration.config.adapter.TypeAdapterManager;
import dev.toma.configuration.config.adapter.TypeMapper;
import dev.toma.configuration.config.adapter.TypeMatcher;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.StringArrayValue;
import dev.toma.configuration.config.value.ValueData;

/** Registers the two complete-rule array types before the main configuration holder is created. */
public final class DataProductionRuleConfigurationAdapter {

    private static final ResourceLocation MIMETIC_MATCHER = ResourceLocation.fromNamespaceAndPath(
            "data_energistics", "mimetic_output_entry_array");
    private static final ResourceLocation EXTRACTOR_MATCHER = ResourceLocation.fromNamespaceAndPath(
            "data_energistics", "extractor_output_entry_array");
    private static final char SEPARATOR = '\u001F';

    private DataProductionRuleConfigurationAdapter() {}

    public static void register() {
        TypeAdapterManager.registerTypeMapper(
                MimeticOutputEntry[].class,
                TypeMapper.of(DataProductionRuleConfigurationAdapter::encodeMimetic,
                        DataProductionRuleConfigurationAdapter::decodeMimetic));
        TypeAdapterManager.registerTypeAdapter(
                new TypeMatcher.NamedMatcherImpl(MIMETIC_MATCHER, MimeticOutputEntry[].class::equals),
                new EntryArrayAdapter<>());
        TypeAdapterManager.registerTypeMapper(
                ExtractorOutputEntry[].class,
                TypeMapper.of(DataProductionRuleConfigurationAdapter::encodeExtractor,
                        DataProductionRuleConfigurationAdapter::decodeExtractor));
        TypeAdapterManager.registerTypeAdapter(
                new TypeMatcher.NamedMatcherImpl(EXTRACTOR_MATCHER, ExtractorOutputEntry[].class::equals),
                new EntryArrayAdapter<>());
    }

    private static String[] encodeMimetic(MimeticOutputEntry[] entries) {
        String[] encoded = new String[entries.length];
        for (int index = 0; index < entries.length; index++) {
            MimeticOutputEntry entry = entries[index];
            encoded[index] = String.join(String.valueOf(SEPARATOR), name(entry.dataType()), entry.recordedId(),
                    name(entry.outputFamily()), name(entry.keyKind()), entry.keyId(), name(entry.amountMode()),
                    Long.toString(entry.amount()));
        }
        return encoded;
    }

    private static MimeticOutputEntry[] decodeMimetic(String[] encoded) {
        MimeticOutputEntry[] entries = new MimeticOutputEntry[encoded.length];
        for (int index = 0; index < encoded.length; index++) {
            String[] fields = encoded[index].split(String.valueOf(SEPARATOR), -1);
            entries[index] = new MimeticOutputEntry(enumValue(DataType.class, fields, 0), field(fields, 1),
                    enumValue(OutputFamily.class, fields, 2), enumValue(OutputKeyKind.class, fields, 3), field(fields, 4),
                    enumValue(OutputAmountMode.class, fields, 5), number(fields, 6));
        }
        return entries;
    }

    private static String[] encodeExtractor(ExtractorOutputEntry[] entries) {
        String[] encoded = new String[entries.length];
        for (int index = 0; index < entries.length; index++) {
            ExtractorOutputEntry entry = entries[index];
            encoded[index] = String.join(String.valueOf(SEPARATOR), String.join(",", entry.weaponItems()),
                    String.join(",", entry.weaponTags()), String.join(",", entry.targetEntityIds()),
                    name(entry.outputFamily()), name(entry.keyKind()), entry.keyId(), name(entry.amountMode()),
                    Long.toString(entry.amount()));
        }
        return encoded;
    }

    private static ExtractorOutputEntry[] decodeExtractor(String[] encoded) {
        ExtractorOutputEntry[] entries = new ExtractorOutputEntry[encoded.length];
        for (int index = 0; index < encoded.length; index++) {
            String[] fields = encoded[index].split(String.valueOf(SEPARATOR), -1);
            entries[index] = new ExtractorOutputEntry(split(field(fields, 0)), split(field(fields, 1)), split(field(fields, 2)),
                    enumValue(OutputFamily.class, fields, 3), enumValue(OutputKeyKind.class, fields, 4), field(fields, 5),
                    enumValue(OutputAmountMode.class, fields, 6), number(fields, 7));
        }
        return entries;
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index] : "";
    }

    private static String name(Enum<?> value) {
        return value.name();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String[] fields, int index) {
        String value = field(fields, index);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String[] split(String value) {
        return value.isBlank() ? new String[0] : value.split(",");
    }

    private static long number(String[] fields, int index) {
        try {
            return Long.parseLong(field(fields, index));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static final class EntryArrayAdapter<T> extends TypeAdapter<T[]> {

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public ConfigValue<T[]> serialize(TypeAttributes<T[]> attributes, Object instance, TypeSerializer serializer) {
            return (ConfigValue) new StringArrayValue(ValueData.of((TypeAttributes) attributes));
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void encodeToBuffer(ConfigValue<T[]> value, FriendlyByteBuf buffer) {
            new StringArrayValue.Adapter().encodeToBuffer((ConfigValue) value, buffer);
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public T[] decodeFromBuffer(ConfigValue<T[]> value, FriendlyByteBuf buffer) {
            return (T[]) new StringArrayValue.Adapter().decodeFromBuffer((ConfigValue) value, buffer);
        }
    }
}
