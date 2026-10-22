package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.api.production.rule.OutputKeyKind;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.util.amount.AmountExpressionParser;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import dev.toma.configuration.config.adapter.TypeAdapter;
import dev.toma.configuration.config.adapter.TypeAdapter.AdapterContext;
import dev.toma.configuration.config.adapter.TypeAdapter.TypeAttributes;
import dev.toma.configuration.config.adapter.TypeAdapter.TypeSerializer;
import dev.toma.configuration.config.adapter.TypeAdapterManager;
import dev.toma.configuration.config.adapter.TypeMapper;
import dev.toma.configuration.config.adapter.TypeMatcher;
import dev.toma.configuration.config.exception.ConfigValueMissingException;
import dev.toma.configuration.config.format.IConfigFormat;
import dev.toma.configuration.config.value.AbstractArrayValue;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.IConfigValue;
import dev.toma.configuration.config.value.IConfigValueReadable;
import dev.toma.configuration.config.value.IHierarchical;
import dev.toma.configuration.config.value.IntValue;
import dev.toma.configuration.config.value.ValueData;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registers the record arrays used by the independent data-production configuration.
 *
 * <p>
 * The configuration library has no object-array adapter. These values keep the public fields
 * as {@code record[]} while exposing each record as editable child values. YAML stores an array
 * as a map containing a length and numbered entries, which is the object hierarchy supported by
 * the library's format API.
 * </p>
 */
public final class DataProductionRuleConfigurationAdapter {

    public static final ResourceLocation MIMETIC_ARRAY_MATCHER = ResourceLocation.fromNamespaceAndPath(
            "data_energistics", "mimetic_carrier_entry_array");
    public static final ResourceLocation EXTRACTOR_ARRAY_MATCHER = ResourceLocation.fromNamespaceAndPath(
            "data_energistics", "extractor_output_entry_array");

    private static final char FIELD_SEPARATOR = '\u001F';
    private static final char ARRAY_SEPARATOR = '\u001E';

    private static final EntryDefinition<MimeticCarrierEntry> MIMETIC_DEFINITION = new EntryDefinition<>() {

        @Override
        public Class<MimeticCarrierEntry> entryType() {
            return MimeticCarrierEntry.class;
        }

        @Override
        public MimeticCarrierEntry defaultEntry() {
            return new MimeticCarrierEntry(
                    DataType.CROP,
                    new String[0],
                    "",
                    1.0F,
                    0.0F,
                    OutputFamily.LOOT,
                    OutputKeyKind.ITEM,
                    "",
                    OutputAmountMode.FIXED,
                    1L);
        }

        @Override
        public MimeticCarrierEntry[] newArray(int length) {
            return new MimeticCarrierEntry[length];
        }

        @Override
        public void initialize(StructuredRuleEntryValue<MimeticCarrierEntry> value, MimeticCarrierEntry entry) {
            value.addField("dataType", DataType.class, entry.dataType(), MimeticCarrierEntry::dataType);
            value.addField("inputIds", String[].class, entry.inputIds(), MimeticCarrierEntry::inputIds);
            value.addField("recordedId", String.class, entry.recordedId(), MimeticCarrierEntry::recordedId);
            value.addField("progressPerInput", float.class, entry.progressPerInput(), MimeticCarrierEntry::progressPerInput);
            value.addField("requirement", float.class, entry.requirement(), MimeticCarrierEntry::requirement);
            value.addField("outputFamily", OutputFamily.class, entry.outputFamily(), MimeticCarrierEntry::outputFamily);
            value.addField("keyKind", OutputKeyKind.class, entry.keyKind(), MimeticCarrierEntry::keyKind);
            value.addField("outputId", String.class, entry.outputId(), MimeticCarrierEntry::outputId);
            value.addField("amountMode", OutputAmountMode.class, entry.amountMode(), MimeticCarrierEntry::amountMode);
            value.addField("amount", long.class, entry.amount(), MimeticCarrierEntry::amount);
        }

        @Override
        public MimeticCarrierEntry fromFields(Object2ObjectLinkedOpenHashMap<String, Object> fields) {
            return new MimeticCarrierEntry(
                    field(fields, "dataType", DataType.class),
                    field(fields, "inputIds", String[].class),
                    field(fields, "recordedId", String.class),
                    field(fields, "progressPerInput", Float.class),
                    field(fields, "requirement", Float.class),
                    field(fields, "outputFamily", OutputFamily.class),
                    field(fields, "keyKind", OutputKeyKind.class),
                    field(fields, "outputId", String.class),
                    field(fields, "amountMode", OutputAmountMode.class),
                    field(fields, "amount", Long.class));
        }

        @Override
        public String encode(MimeticCarrierEntry entry) {
            return String.join(String.valueOf(FIELD_SEPARATOR),
                    entry.dataType().name(),
                    String.join(String.valueOf(ARRAY_SEPARATOR), entry.inputIds()),
                    entry.recordedId(),
                    Float.toString(entry.progressPerInput()),
                    Float.toString(entry.requirement()),
                    entry.outputFamily().name(),
                    entry.keyKind().name(),
                    entry.outputId(),
                    entry.amountMode().name(),
                    Long.toString(entry.amount()));
        }

        @Override
        public MimeticCarrierEntry decode(String encoded) {
            String[] fields = encoded.split(String.valueOf(FIELD_SEPARATOR), -1);
            return new MimeticCarrierEntry(
                    Enum.valueOf(DataType.class, field(fields, 0)),
                    splitArray(field(fields, 1)),
                    field(fields, 2),
                    decimal(fields, 3),
                    decimal(fields, 4),
                    Enum.valueOf(OutputFamily.class, field(fields, 5)),
                    Enum.valueOf(OutputKeyKind.class, field(fields, 6)),
                    field(fields, 7),
                    Enum.valueOf(OutputAmountMode.class, field(fields, 8)),
                    number(fields, 9));
        }
    };

    private static final EntryDefinition<ExtractorOutputEntry> EXTRACTOR_DEFINITION = new EntryDefinition<>() {

        @Override
        public Class<ExtractorOutputEntry> entryType() {
            return ExtractorOutputEntry.class;
        }

        @Override
        public ExtractorOutputEntry defaultEntry() {
            return new ExtractorOutputEntry(
                    new String[0],
                    new String[0],
                    new String[0],
                    OutputFamily.LOOT,
                    OutputKeyKind.ITEM,
                    "",
                    OutputAmountMode.FIXED,
                    1L);
        }

        @Override
        public ExtractorOutputEntry[] newArray(int length) {
            return new ExtractorOutputEntry[length];
        }

        @Override
        public void initialize(StructuredRuleEntryValue<ExtractorOutputEntry> value, ExtractorOutputEntry entry) {
            value.addField("weaponItems", String[].class, entry.weaponItems(), ExtractorOutputEntry::weaponItems);
            value.addField("weaponTags", String[].class, entry.weaponTags(), ExtractorOutputEntry::weaponTags);
            value.addField("targetEntityIds", String[].class, entry.targetEntityIds(), ExtractorOutputEntry::targetEntityIds);
            value.addField("outputFamily", OutputFamily.class, entry.outputFamily(), ExtractorOutputEntry::outputFamily);
            value.addField("keyKind", OutputKeyKind.class, entry.keyKind(), ExtractorOutputEntry::keyKind);
            value.addField("outputId", String.class, entry.outputId(), ExtractorOutputEntry::outputId);
            value.addField("amountMode", OutputAmountMode.class, entry.amountMode(), ExtractorOutputEntry::amountMode);
            value.addField("amount", long.class, entry.amount(), ExtractorOutputEntry::amount);
        }

        @Override
        public ExtractorOutputEntry fromFields(Object2ObjectLinkedOpenHashMap<String, Object> fields) {
            return new ExtractorOutputEntry(
                    field(fields, "weaponItems", String[].class),
                    field(fields, "weaponTags", String[].class),
                    field(fields, "targetEntityIds", String[].class),
                    field(fields, "outputFamily", OutputFamily.class),
                    field(fields, "keyKind", OutputKeyKind.class),
                    field(fields, "outputId", String.class),
                    field(fields, "amountMode", OutputAmountMode.class),
                    field(fields, "amount", Long.class));
        }

        @Override
        public String encode(ExtractorOutputEntry entry) {
            return String.join(String.valueOf(FIELD_SEPARATOR),
                    String.join(",", entry.weaponItems()),
                    String.join(",", entry.weaponTags()),
                    String.join(",", entry.targetEntityIds()),
                    entry.outputFamily().name(),
                    entry.keyKind().name(),
                    entry.outputId(),
                    entry.amountMode().name(),
                    Long.toString(entry.amount()));
        }

        @Override
        public ExtractorOutputEntry decode(String encoded) {
            String[] fields = encoded.split(String.valueOf(FIELD_SEPARATOR), -1);
            return new ExtractorOutputEntry(
                    splitComma(field(fields, 0)),
                    splitComma(field(fields, 1)),
                    splitComma(field(fields, 2)),
                    Enum.valueOf(OutputFamily.class, field(fields, 3)),
                    Enum.valueOf(OutputKeyKind.class, field(fields, 4)),
                    field(fields, 5),
                    Enum.valueOf(OutputAmountMode.class, field(fields, 6)),
                    number(fields, 7));
        }
    };

    private DataProductionRuleConfigurationAdapter() {}

    public static void register() {
        TypeAdapterManager.registerTypeAdapter(
                new TypeMatcher.NamedMatcherImpl(MIMETIC_ARRAY_MATCHER, MimeticCarrierEntry[].class::equals),
                new StructuredRuleEntryArrayAdapter<>(MIMETIC_DEFINITION));
        TypeAdapterManager.registerTypeAdapter(
                new TypeMatcher.NamedMatcherImpl(EXTRACTOR_ARRAY_MATCHER, ExtractorOutputEntry[].class::equals),
                new StructuredRuleEntryArrayAdapter<>(EXTRACTOR_DEFINITION));
    }

    public static EntryDefinition<MimeticCarrierEntry> mimeticDefinition() {
        return MIMETIC_DEFINITION;
    }

    public static EntryDefinition<ExtractorOutputEntry> extractorDefinition() {
        return EXTRACTOR_DEFINITION;
    }

    private static <T> T field(Object2ObjectLinkedOpenHashMap<String, Object> fields, String name, Class<T> type) {
        return type.cast(fields.get(name));
    }

    private static boolean accepts(IConfigValue<?> value, Class<?> type) {
        return value instanceof ConfigValue<?> configValue && type.isAssignableFrom(configValue.getValueType());
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index] : "";
    }

    private static String[] splitArray(String value) {
        return value.isBlank() ? new String[0] : value.split(String.valueOf(ARRAY_SEPARATOR), -1);
    }

    private static String[] splitComma(String value) {
        return value.isBlank() ? new String[0] : value.split(",", -1);
    }

    private static long number(String[] fields, int index) {
        return AmountExpressionParser.parseLong(field(fields, index), Long.MIN_VALUE, Long.MAX_VALUE).orElse(0L);
    }

    private static float decimal(String[] fields, int index) {
        return AmountExpressionParser.parseFloat(field(fields, index)).orElse(0.0F);
    }

    /** Describes one record type without introducing a second column-oriented schema. */
    public interface EntryDefinition<T> {

        Class<T> entryType();

        T defaultEntry();

        T[] newArray(int length);

        void initialize(StructuredRuleEntryValue<T> value, T entry);

        T fromFields(Object2ObjectLinkedOpenHashMap<String, Object> fields);

        String encode(T entry);

        T decode(String encoded);
    }

    private static final class StructuredRuleEntryArrayAdapter<T> extends TypeAdapter<T[]> {

        private final EntryDefinition<T> definition;

        private StructuredRuleEntryArrayAdapter(EntryDefinition<T> definition) {
            this.definition = definition;
        }

        @Override
        public ConfigValue<T[]> serialize(TypeAttributes<T[]> attributes, Object instance, TypeSerializer serializer) {
            return new StructuredRuleEntryArrayValue<>(ValueData.of(attributes), definition, serializer);
        }

        @Override
        public void encodeToBuffer(ConfigValue<T[]> value, FriendlyByteBuf buffer) {
            T[] entries = value.get();
            buffer.writeVarInt(entries.length);
            for (T entry : entries) {
                buffer.writeUtf(definition.encode(entry));
            }
        }

        @Override
        public T[] decodeFromBuffer(ConfigValue<T[]> value, FriendlyByteBuf buffer) {
            int length = buffer.readVarInt();
            T[] entries = definition.newArray(length);
            for (int index = 0; index < length; index++) {
                entries[index] = definition.decode(buffer.readUtf());
            }
            return entries;
        }
    }

    /** An editable object-array value backed by numbered YAML maps. */
    public static final class StructuredRuleEntryArrayValue<T> extends AbstractArrayValue<T> {

        private final EntryDefinition<T> definition;
        private final TypeSerializer serializer;

        private StructuredRuleEntryArrayValue(
                                              ValueData<T[]> valueData,
                                              EntryDefinition<T> definition,
                                              TypeSerializer serializer) {
            super(valueData);
            this.definition = definition;
            this.serializer = serializer;
        }

        public EntryDefinition<T> definition() {
            return definition;
        }

        public ConfigValue<T> createElementValue(ValueData<T> valueData) {
            TypeAttributes<T> source = valueData.getAttributes();
            TypeAttributes<T> displayAttributes = new TypeAttributes<>(
                    source.configOwner(),
                    displayId(source),
                    source.value(),
                    source.context(),
                    source.localization(),
                    source.fileComments(),
                    source.localizeComments());
            StructuredRuleEntryValue<T> value = new StructuredRuleEntryValue<>(
                    ValueData.of(displayAttributes),
                    definition,
                    serializer,
                    source.id());
            return value;
        }

        private String displayId(TypeAttributes<T> source) {
            Field owner = getSerializationContext().getOwner();
            return owner == null ? source.id() : owner.getName();
        }

        @Override
        public T createElementInstance() {
            return definition.defaultEntry();
        }

        @Override
        public ObjectArrayList<String> getChildrenKeys() {
            T[] entries = get();
            ObjectArrayList<String> keys = new ObjectArrayList<>(entries.length);
            for (int index = 0; index < entries.length; index++) {
                keys.add(Integer.toString(index));
            }
            return keys;
        }

        @Override
        public IConfigValue<?> getChildById(String id) {
            int index = parseIndex(id);
            T[] entries = get();
            if (index < 0 || index >= entries.length) {
                return null;
            }
            TypeAttributes<T> attributes = elementAttributes(id, entries[index], new ArrayElementContext<>(this, index));
            return createElementValue(ValueData.of(attributes));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Optional<IConfigValue<V>> getChild(
                                                      Iterator<String> path,
                                                      Class<V> type) {
            if (!path.hasNext()) {
                return type.isAssignableFrom(getValueType()) ? Optional.of((IConfigValue<V>) this) : Optional.empty();
            }
            IConfigValue<?> child = getChildById(path.next());
            if (child == null) {
                return Optional.empty();
            }
            if (!path.hasNext()) {
                return accepts(child, type) ? Optional.of((IConfigValue<V>) child) : Optional.empty();
            }
            return child instanceof IHierarchical hierarchical ? hierarchical.getChild(path, type) : Optional.empty();
        }

        @Override
        public <V> Optional<V> getChildValue(
                                             Iterator<String> path,
                                             Class<V> type) {
            return getChild(path, type).map(value -> type.cast(value.get()));
        }

        @Override
        protected void serialize(IConfigFormat format) {
            T[] entries = get(IConfigValueReadable.Mode.SAVED);
            Object2ObjectLinkedOpenHashMap<String, ConfigValue<?>> values = new Object2ObjectLinkedOpenHashMap<>();
            values.put("length", lengthValue(entries.length));
            for (int index = 0; index < entries.length; index++) {
                String id = Integer.toString(index);
                TypeAttributes<T> attributes = elementAttributes(id, entries[index], new NoopContext<>(this));
                values.put(id, createElementValue(ValueData.of(attributes)));
            }
            format.writeMap(getId(), values);
        }

        @Override
        protected void deserialize(IConfigFormat format) throws ConfigValueMissingException {
            int currentLength = get(IConfigValueReadable.Mode.SAVED).length;
            IntValue length = lengthValue(currentLength);
            ObjectArrayList<ConfigValue<?>> lengthValues = new ObjectArrayList<>(1);
            lengthValues.add(length);
            try {
                format.readMap(getId(), lengthValues);
            } catch (ConfigValueMissingException ignored) {
                return;
            }

            int entryCount = Math.max(0, length.get());
            T[] decoded = definition.newArray(entryCount);
            ObjectArrayList<ConfigValue<?>> values = new ObjectArrayList<>(entryCount);
            for (int index = 0; index < entryCount; index++) {
                T fallback = definition.defaultEntry();
                TypeAttributes<T> attributes = elementAttributes(
                        Integer.toString(index),
                        fallback,
                        new ArrayElementContext<>(decoded, index, this));
                ConfigValue<T> entry = createElementValue(ValueData.of(attributes));
                values.add(entry);
                decoded[index] = entry.get();
            }
            format.readMap(getId(), values);
            for (int index = 0; index < entryCount; index++) {
                if (decoded[index] == null) {
                    @SuppressWarnings("unchecked")
                    ConfigValue<T> entry = (ConfigValue<T>) values.get(index);
                    decoded[index] = entry.get();
                }
            }
            setValue(decoded);
        }

        private IntValue lengthValue(int length) {
            TypeAttributes<Integer> attributes = new TypeAttributes<>(
                    getValueData().getAttributes().configOwner(),
                    "length",
                    length,
                    new NoopContext<>(this),
                    getValueData().getAttributes().localization(),
                    new String[0],
                    false);
            return new IntValue(ValueData.of(attributes));
        }

        private TypeAttributes<T> elementAttributes(String id, T entry, AdapterContext context) {
            TypeAttributes<T[]> parent = getValueData().getAttributes();
            return new TypeAttributes<>(
                    parent.configOwner(),
                    id,
                    entry,
                    context,
                    parent.localization(),
                    new String[0],
                    false);
        }

        private static int parseIndex(String id) {
            try {
                return Integer.parseInt(id);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
    }

    /** A record entry whose components are exposed as ordinary configuration child values. */
    public static final class StructuredRuleEntryValue<T> extends ConfigValue<T> implements IHierarchical {

        private final EntryDefinition<T> definition;
        private final TypeSerializer serializer;
        private final TypeAttributes<T> elementAttributes;
        private final String serializationId;
        private final EntryState<T> state;

        private StructuredRuleEntryValue(
                                         ValueData<T> valueData,
                                         EntryDefinition<T> definition,
                                         TypeSerializer serializer,
                                         String serializationId) {
            super(valueData);
            this.definition = definition;
            this.serializer = serializer;
            this.elementAttributes = valueData.getAttributes();
            this.serializationId = serializationId;
            this.state = new EntryState<>(this, definition);
            this.state.suppress = true;
            definition.initialize(this, valueData.getDefaultValue());
            this.state.suppress = false;
        }

        public Object2ObjectLinkedOpenHashMap<String, ConfigValue<?>> fields() {
            return state.fields;
        }

        public <V> void addField(String id, Class<V> type, V raw, Function<T, V> getter) {
            var typeAttributes = TypeAdapterManager.forType(type);
            TypeAdapter<?> adapter = typeAttributes.adapter();
            if (adapter == null) {
                throw new IllegalStateException("Missing configuration adapter for record field " + id);
            }
            TypeMapper<?, ?> mapper = typeAttributes.mapper();
            Object mapped = migrate(mapper, raw);
            state.rawValues.put(id, raw);
            state.mappers.put(id, mapper);
            state.getters.put(id, getter);
            AdapterContext context = new FieldContext(adapter, editorField(id), changed -> {
                state.rawValues.put(id, rollback(mapper, changed));
                if (!state.suppress) {
                    state.applyFields();
                }
            });
            TypeAttributes<?> fieldAttributes = new TypeAttributes<>(
                    elementAttributes.configOwner(),
                    id,
                    mapped,
                    context,
                    elementAttributes.localization(),
                    new String[0],
                    false);
            try {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                ConfigValue<?> field = ((TypeAdapter) adapter).serialize(
                        fieldAttributes,
                        mapped,
                        serializer);
                field.setParent(this);
                state.fields.put(id, field);
                state.fieldKeys.add(id);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to create configuration field " + id, exception);
            }
        }

        @Override
        protected T validateValue(T value) {
            if (state != null && !state.applying) {
                state.syncFields(value);
            }
            return value;
        }

        @Override
        public void revertChanges() {
            super.revertChanges();
            state.syncFields(getActiveValue());
        }

        @Override
        public void revertChangesToDefault() {
            super.revertChangesToDefault();
            state.syncFields(getValueData().getDefaultValue());
        }

        @Override
        protected void serialize(IConfigFormat format) {
            format.writeMap(serializationId, state.fields);
        }

        @Override
        protected void deserialize(IConfigFormat format) throws ConfigValueMissingException {
            format.readMap(serializationId, state.fields.values());
            state.applyFields();
        }

        @Override
        public ObjectArrayList<String> getChildrenKeys() {
            return state.fieldKeys;
        }

        @Override
        public IConfigValue<?> getChildById(String id) {
            return state.fields.get(id);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Optional<IConfigValue<V>> getChild(
                                                      Iterator<String> path,
                                                      Class<V> type) {
            if (!path.hasNext()) {
                return type.isAssignableFrom(getValueType()) ? Optional.of((IConfigValue<V>) this) : Optional.empty();
            }
            IConfigValue<?> child = state.fields.get(path.next());
            if (child == null) {
                return Optional.empty();
            }
            if (!path.hasNext()) {
                return accepts(child, type) ? Optional.of((IConfigValue<V>) child) : Optional.empty();
            }
            return child instanceof IHierarchical hierarchical ? hierarchical.getChild(path, type) : Optional.empty();
        }

        @Override
        public <V> Optional<V> getChildValue(
                                             Iterator<String> path,
                                             Class<V> type) {
            return getChild(path, type).map(value -> type.cast(value.get()));
        }

        private static Object migrate(TypeMapper<?, ?> mapper, Object value) {
            @SuppressWarnings("unchecked")
            TypeMapper<Object, Object> typed = (TypeMapper<Object, Object>) mapper;
            return typed.migrate(value);
        }

        private static Object rollback(TypeMapper<?, ?> mapper, Object value) {
            @SuppressWarnings("unchecked")
            TypeMapper<Object, Object> typed = (TypeMapper<Object, Object>) mapper;
            return typed.rollback(value);
        }
    }

    private static final class EntryState<T> {

        private final StructuredRuleEntryValue<T> owner;
        private final EntryDefinition<T> definition;
        private final Object2ObjectLinkedOpenHashMap<String, ConfigValue<?>> fields = new Object2ObjectLinkedOpenHashMap<>();
        private final ObjectArrayList<String> fieldKeys = new ObjectArrayList<>();
        private final Object2ObjectLinkedOpenHashMap<String, Object> rawValues = new Object2ObjectLinkedOpenHashMap<>();
        private final Object2ObjectLinkedOpenHashMap<String, TypeMapper<?, ?>> mappers = new Object2ObjectLinkedOpenHashMap<>();
        private final Object2ObjectLinkedOpenHashMap<String, Function<?, ?>> getters = new Object2ObjectLinkedOpenHashMap<>();
        private boolean suppress;
        private boolean applying;

        private EntryState(StructuredRuleEntryValue<T> owner, EntryDefinition<T> definition) {
            this.owner = owner;
            this.definition = definition;
        }

        private void syncFields(T value) {
            if (suppress || fields.isEmpty()) {
                return;
            }
            suppress = true;
            try {
                for (String id : fieldKeys) {
                    @SuppressWarnings("unchecked")
                    Function<T, Object> getter = (Function<T, Object>) getters.get(id);
                    Object raw = getter.apply(value);
                    rawValues.put(id, raw);
                    @SuppressWarnings({ "rawtypes" })
                    ConfigValue field = owner.fields().get(id);
                    field.forceSetValue(StructuredRuleEntryValue.migrate(mappers.get(id), raw));
                }
            } finally {
                suppress = false;
            }
        }

        private void applyFields() {
            if (suppress || applying) {
                return;
            }
            applying = true;
            try {
                owner.setValue(definition.fromFields(rawValues));
            } finally {
                applying = false;
            }
        }
    }

    private record FieldContext(TypeAdapter<?> adapter, Field owner,
                                Consumer<Object> setter)
            implements AdapterContext {

        @Override
        public TypeAdapter<?> getAdapter() {
            return adapter;
        }

        @Override
        public Field getOwner() {
            return owner;
        }

        @Override
        public void setFieldValue(Object value) {
            setter.accept(value);
        }

        @Override
        public void setValue(Object value) {
            setter.accept(value);
        }
    }

    /**
     * Public field metadata used only by the configuration screen. The configuration library
     * chooses a widget from {@link AdapterContext#getOwner()} and requires a real {@code Field};
     * record components do not expose one, so this holder supplies the corresponding Java types.
     */
    public static final class EditorFields {

        public DataType dataType;
        public String[] inputIds;
        public String recordedId;
        public float progressPerInput;
        public float requirement;
        public String[] weaponItems;
        public String[] weaponTags;
        public String[] targetEntityIds;
        public OutputFamily outputFamily;
        public OutputKeyKind keyKind;
        public String outputId;
        public OutputAmountMode amountMode;
        public long amount;
    }

    private static Field editorField(String id) {
        try {
            return EditorFields.class.getField(id);
        } catch (NoSuchFieldException exception) {
            throw new IllegalArgumentException("Missing editor field metadata for " + id, exception);
        }
    }

    private static final class ArrayElementContext<T> implements AdapterContext {

        private final TypeAdapter<?> adapter;
        private final Field owner;
        private final T[] target;
        private final int index;
        private final StructuredRuleEntryArrayValue<T> array;

        private ArrayElementContext(T[] target, int index, StructuredRuleEntryArrayValue<T> array) {
            this.adapter = array.getAdapter();
            this.owner = array.getSerializationContext().getOwner();
            this.target = target;
            this.index = index;
            this.array = array;
        }

        private ArrayElementContext(StructuredRuleEntryArrayValue<T> array, int index) {
            this(array.get(), index, array);
        }

        @Override
        public TypeAdapter<?> getAdapter() {
            return adapter;
        }

        @Override
        public Field getOwner() {
            return owner;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void setFieldValue(Object value) {
            if (target != array.get()) {
                target[index] = (T) value;
                return;
            }
            T[] replacement = array.definition.newArray(target.length);
            System.arraycopy(target, 0, replacement, 0, target.length);
            replacement[index] = (T) value;
            array.setValue(replacement);
        }

        @Override
        public void setValue(Object value) {
            setFieldValue(value);
        }
    }

    private static final class NoopContext<T> implements AdapterContext {

        private final TypeAdapter<?> adapter;
        private final Field owner;

        private NoopContext(StructuredRuleEntryArrayValue<T> array) {
            this.adapter = array.getAdapter();
            this.owner = array.getSerializationContext().getOwner();
        }

        @Override
        public TypeAdapter<?> getAdapter() {
            return adapter;
        }

        @Override
        public Field getOwner() {
            return owner;
        }

        @Override
        public void setFieldValue(Object value) {}

        @Override
        public void setValue(Object value) {}
    }
}
