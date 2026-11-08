package com.fish_dan_.data_energistics.core.config;

import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Predicate;

/** Startup-thread construction boundary. Built snapshots do not retain mutable builder state. */
public final class EarlyConfigBuilder {

    private static final Logger LOGGER = LogManager.getLogger("DataEnergisticsEarlyConfig");
    private final Path configFile;
    private final Object2ObjectMap<String, Definition> definitions = new Object2ObjectLinkedOpenHashMap<>();

    /** Creates a builder without reading or changing the supplied configuration path. */
    public EarlyConfigBuilder(Path configFile) {
        this.configFile = configFile;
    }

    /** Registers a visible rule; malformed or duplicate names fail immediately. */
    public EarlyConfigBuilder rule(String name, boolean enabled) {
        validateName(name);
        if (this.definitions.putIfAbsent(name, new Definition(enabled)) != null) {
            throw new IllegalStateException("Early Mixin rule already defined: " + name);
        }
        return this;
    }

    /** Registers an environment-controlled rule whose value cannot be changed by a properties entry. */
    public EarlyConfigBuilder hidden(String name, boolean enabled) {
        rule(name, enabled);
        definition(name).hidden = true;
        return this;
    }

    /** Registers a hidden selector following an existing rule, optionally inverted; dependencies remain mandatory. */
    public EarlyConfigBuilder delegate(String name, String delegateName, boolean inverted) {
        definition(delegateName);
        hidden(name, true);
        Definition definition = definition(name);
        definition.delegate = delegateName;
        definition.inverted = inverted;
        return this;
    }

    /** Appends one-line documentation to an existing rule; line breaks are rejected. */
    public EarlyConfigBuilder comment(String name, String... comments) {
        Definition definition = definition(name);
        for (String comment : comments) {
            if (comment.indexOf('\n') >= 0 || comment.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Comments must contain one line per entry");
            }
            definition.comments.add(comment);
        }
        return this;
    }

    /** Records a Mod source for an existing rule. Any disabling source wins over enabling sources and users. */
    public EarlyConfigBuilder modOverride(String name, boolean enabled, String modId) {
        Definition definition = definition(name);
        if (definition.modOverrides.containsKey(modId) && definition.modOverrides.getBoolean(modId) != enabled) {
            throw new IllegalArgumentException("Conflicting override from Mod " + modId + " for " + name);
        }
        definition.modOverrides.put(modId, enabled);
        return this;
    }

    /** Adds hard dependency gates to an existing rule; users and delegates cannot bypass missing Mods. */
    public EarlyConfigBuilder requireMods(String name, Predicate<String> modPresent, String... modIds) {
        Definition definition = definition(name);
        for (String modId : modIds) {
            if (!modPresent.test(modId)) definition.available = false;
        }
        return this;
    }

    /** Registers a class and all its package ancestors, retaining any explicitly registered values. */
    public EarlyConfigBuilder mixin(String relativeClassName) {
        validateName(relativeClassName);
        int end = relativeClassName.indexOf('.');
        while (end != -1) {
            this.definitions.putIfAbsent(relativeClassName.substring(0, end), new Definition(true));
            end = relativeClassName.indexOf('.', end + 1);
        }
        this.definitions.putIfAbsent(relativeClassName, new Definition(true));
        return this;
    }

    /** Reads and normalizes the file. Unreadable input allows visible rules and is preserved for diagnosis. */
    public DataEnergisticsEarlyConfig load() {
        Properties properties;
        try {
            properties = EarlyConfigProperties.read(this.configFile);
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not read early Mixin settings from {}; allowing rules by default", this.configFile, exception);
            properties = new Properties();
            for (var entry : this.definitions.object2ObjectEntrySet()) {
                if (!entry.getValue().hidden) properties.setProperty(entry.getKey(), "true");
            }
            // Preserve an unreadable file so its contents remain available for diagnosis.
            return build(properties);
        }
        DataEnergisticsEarlyConfig config = build(properties);
        try {
            EarlyConfigProperties.write(this.configFile, config.getOptionMap());
        } catch (IOException exception) {
            LOGGER.error("Could not write early Mixin settings to {}; retaining in-memory defaults", this.configFile, exception);
        }
        return config;
    }

    /** Resolves supplied properties without touching disk. Unknown or uncertain values cannot disable injection. */
    public DataEnergisticsEarlyConfig build(Properties properties) {
        Object2BooleanMap<String> userValues = new Object2BooleanLinkedOpenHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            String name = key;
            if (!this.definitions.containsKey(name) && this.definitions.containsKey("normal." + key)) {
                name = "normal." + key;
                if (properties.containsKey(name)) {
                    LOGGER.warn("Ignoring legacy early Mixin setting '{}' because '{}' is explicitly configured", key, name);
                    continue;
                }
                LOGGER.info("Migrating early Mixin setting '{}' to '{}'", key, name);
            }
            if (!this.definitions.containsKey(name)) {
                LOGGER.warn("Unknown early Mixin setting '{}', ignoring", key);
                continue;
            }
            Definition definition = this.definitions.get(name);
            if (definition.hidden) {
                LOGGER.warn("Early Mixin setting '{}' is environment-controlled, ignoring user value", key);
                continue;
            }
            String value = properties.getProperty(key).trim();
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                userValues.put(name, value.equalsIgnoreCase("true"));
            } else {
                // An unreadable value must never disable a transformation. Only an explicit false disables it.
                LOGGER.warn("Invalid early Mixin setting {}={}; allowing injection", key, value);
                userValues.put(name, true);
            }
        }
        Object2ObjectMap<String, Option> options = new Object2ObjectLinkedOpenHashMap<>();
        for (String name : this.definitions.keySet()) buildOption(name, options, userValues, new ObjectOpenHashSet<>());
        return DataEnergisticsEarlyConfig.create(this.configFile, options);
    }

    private Option buildOption(String name, Object2ObjectMap<String, Option> options, Object2BooleanMap<String> userValues, ObjectSet<String> visiting) {
        if (options.containsKey(name)) return options.get(name);
        if (!visiting.add(name)) throw new IllegalStateException("Cyclic early Mixin rule dependency: " + name);
        Definition definition = definition(name);
        Option parent = null;
        int split = name.lastIndexOf('.');
        while (split > 0) {
            String parentName = name.substring(0, split);
            if (this.definitions.containsKey(parentName)) {
                parent = buildOption(parentName, options, userValues, visiting);
                break;
            }
            split = name.lastIndexOf('.', split - 1);
        }
        Option delegate = definition.delegate == null ? null : buildOption(definition.delegate, options, userValues, visiting);
        boolean userDefined = userValues.containsKey(name);
        boolean userEnabled = userDefined && userValues.getBoolean(name);
        Option option = Option.builder()
                .name(name)
                .defaultEnabled(definition.enabled)
                .userEnabled(userEnabled)
                .userDefined(userDefined)
                .hidden(definition.hidden)
                .available(definition.available)
                .comment(definition.comments)
                .modOverrides(definition.modOverrides)
                .parent(parent)
                .delegate(delegate)
                .delegateInverted(definition.inverted)
                .build();
        visiting.remove(name);
        options.put(name, option);
        return option;
    }

    private Definition definition(String name) {
        if (!this.definitions.containsKey(name)) throw new IllegalArgumentException("Early Mixin rule not defined: " + name);
        return this.definitions.get(name);
    }

    private static void validateName(String name) {
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new IllegalArgumentException("Invalid early Mixin rule name: " + name);
        }
    }

    private static final class Definition {

        private final boolean enabled;
        private final ObjectList<String> comments = new ObjectArrayList<>();
        private final Object2BooleanMap<String> modOverrides = new Object2BooleanLinkedOpenHashMap<>();
        private boolean hidden;
        private boolean available = true;
        private @Nullable String delegate;
        private boolean inverted;

        private Definition(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
