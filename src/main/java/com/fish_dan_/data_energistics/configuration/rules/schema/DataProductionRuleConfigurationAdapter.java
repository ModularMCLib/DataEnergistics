package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import dev.toma.configuration.config.adapter.TypeAdapter;
import dev.toma.configuration.config.adapter.TypeAdapterManager;
import dev.toma.configuration.config.adapter.TypeMapper;
import dev.toma.configuration.config.adapter.TypeMatcher;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.StringArrayValue;
import dev.toma.configuration.config.value.ValueData;

/** Registers complete typed rule-record arrays before the main configuration holder is created. */
public final class DataProductionRuleConfigurationAdapter {

    private static final ResourceLocation MIMETIC_MATCHER = ResourceLocation.fromNamespaceAndPath(
            "data_energistics", "mimetic_carrier_entry_array");
    private static final ResourceLocation EXTRACTOR_MATCHER = ResourceLocation.fromNamespaceAndPath(
            "data_energistics", "extractor_output_entry_array");
    private static final char SEPARATOR = '\u001F';
    private static final char ARRAY_SEPARATOR = '\u001E';

    private DataProductionRuleConfigurationAdapter() {}

    public static void register() {
        TypeAdapterManager.registerTypeMapper(
                MimeticCarrierEntry[].class,
                TypeMapper.of(DataProductionRuleConfigurationAdapter::encodeMimeticCarrier,
                        DataProductionRuleConfigurationAdapter::decodeMimeticCarrier));
        TypeAdapterManager.registerTypeAdapter(
                new TypeMatcher.NamedMatcherImpl(MIMETIC_MATCHER, MimeticCarrierEntry[].class::equals),
                new EntryArrayAdapter<>());
        TypeAdapterManager.registerTypeMapper(
                ExtractorOutputEntry[].class,
                TypeMapper.of(DataProductionRuleConfigurationAdapter::encodeExtractor,
                        DataProductionRuleConfigurationAdapter::decodeExtractor));
        TypeAdapterManager.registerTypeAdapter(
                new TypeMatcher.NamedMatcherImpl(EXTRACTOR_MATCHER, ExtractorOutputEntry[].class::equals),
                new EntryArrayAdapter<>());
    }

    private static String[] encodeMimeticCarrier(MimeticCarrierEntry[] entries) {
        String[] encoded = new String[entries.length];
        for (int index = 0; index < entries.length; index++) {
            MimeticCarrierEntry entry = entries[index];
            encoded[index] = String.join(String.valueOf(SEPARATOR), name(entry.slot()), name(entry.dataType()),
                    entry.inputItem(), entry.recordedId(), Float.toString(entry.progressPerItem()),
                    Float.toString(entry.requiredAmount()),
                    encodeEnums(entry.outputFamilies()),
                    encodeEnums(entry.keyKinds()),
                    encodeStrings(entry.keyIds()),
                    encodeEnums(entry.amountModes()),
                    encodeLongs(entry.amounts()));
        }
        return encoded;
    }

    private static MimeticCarrierEntry[] decodeMimeticCarrier(String[] encoded) {
        MimeticCarrierEntry[] entries = new MimeticCarrierEntry[encoded.length];
        for (int index = 0; index < encoded.length; index++) {
            String[] fields = encoded[index].split(String.valueOf(SEPARATOR), -1);
            entries[index] = new MimeticCarrierEntry(enumValue(Slot.class, fields, 0),
                    enumValue(DataType.class, fields, 1), field(fields, 2), field(fields, 3),
                    decimal(fields, 4), decimal(fields, 5),
                    decodeOutputFamilies(field(fields, 6)),
                    decodeKeyKinds(field(fields, 7)),
                    splitArray(field(fields, 8)),
                    decodeAmountModes(field(fields, 9)),
                    longArray(field(fields, 10)));
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
            entries[index] = new ExtractorOutputEntry(splitComma(field(fields, 0)), splitComma(field(fields, 1)), splitComma(field(fields, 2)),
                    enumValue(OutputFamily.class, fields, 3), enumValue(OutputKeyKind.class, fields, 4), field(fields, 5),
                    enumValue(OutputAmountMode.class, fields, 6), number(fields, 7));
        }
        return entries;
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index] : "";
    }

    private static String name(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private static String encodeStrings(String[] values) {
        return String.join(String.valueOf(ARRAY_SEPARATOR), values);
    }

    private static String encodeLongs(long[] values) {
        String[] encoded = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            encoded[index] = Long.toString(values[index]);
        }
        return encodeStrings(encoded);
    }

    private static String encodeEnums(Enum<?>[] values) {
        String[] encoded = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            encoded[index] = name(values[index]);
        }
        return encodeStrings(encoded);
    }

    private static OutputFamily[] decodeOutputFamilies(String value) {
        String[] values = splitArray(value);
        OutputFamily[] result = new OutputFamily[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = enumValue(OutputFamily.class, values, index);
        }
        return result;
    }

    private static OutputKeyKind[] decodeKeyKinds(String value) {
        String[] values = splitArray(value);
        OutputKeyKind[] result = new OutputKeyKind[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = enumValue(OutputKeyKind.class, values, index);
        }
        return result;
    }

    private static OutputAmountMode[] decodeAmountModes(String value) {
        String[] values = splitArray(value);
        OutputAmountMode[] result = new OutputAmountMode[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = enumValue(OutputAmountMode.class, values, index);
        }
        return result;
    }

    private static long[] longArray(String value) {
        String[] values = splitArray(value);
        long[] result = new long[values.length];
        for (int index = 0; index < values.length; index++) {
            try {
                result[index] = Long.parseLong(values[index]);
            } catch (NumberFormatException ignored) {
                result[index] = 0L;
            }
        }
        return result;
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

    private static String[] splitArray(String value) {
        return value.isBlank() ? new String[0] : value.split(String.valueOf(ARRAY_SEPARATOR), -1);
    }

    private static String[] splitComma(String value) {
        return value.isBlank() ? new String[0] : value.split(",", -1);
    }

    private static long number(String[] fields, int index) {
        try {
            return Long.parseLong(field(fields, index));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static float decimal(String[] fields, int index) {
        try {
            return Float.parseFloat(field(fields, index));
        } catch (NumberFormatException ignored) {
            return 0.0F;
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
