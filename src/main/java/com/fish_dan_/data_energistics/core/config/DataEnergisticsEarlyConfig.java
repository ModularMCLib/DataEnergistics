package com.fish_dan_.data_energistics.core.config;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/** Immutable startup snapshot. Load on the bootstrap thread; readers may share it after publication. */
public final class DataEnergisticsEarlyConfig {

    public static final String NETWORK_BACKED_BLANK_PATTERN_SLOT = "pattern_encoding.network_backed_blank_pattern_slot";
    private static final Logger LOGGER = LogManager.getLogger("DataEnergisticsEarlyConfig");
    private static final Path CONFIG_PATH = Path.of("config", "data_energistics", "data_energistics-early.properties");
    private final Path configFile;
    private final Object2ObjectMap<String, Option> options;

    private DataEnergisticsEarlyConfig(Path configFile, Object2ObjectMap<String, Option> options) {
        this.configFile = configFile;
        ObjectArrayList<String> names = new ObjectArrayList<>(options.keySet());
        names.sort(String::compareTo);
        Object2ObjectLinkedOpenHashMap<String, Option> ordered = new Object2ObjectLinkedOpenHashMap<>();
        for (String name : names) ordered.put(name, options.get(name));
        this.options = Object2ObjectMaps.unmodifiable(ordered);
    }

    /** Returns the single startup snapshot; editing the file requires restarting the game. */
    public static DataEnergisticsEarlyConfig get() {
        return InstanceHolder.INSTANCE;
    }

    /** Forces startup loading while the Mixin plugin is constructed. */
    public static void initialize() {
        DataEnergisticsEarlyConfig config = get();
        LOGGER.info("Loaded {} early Mixin rules with {} visible override(s) from {}",
                config.getOptionCount(), config.getOptionOverrideCount(), CONFIG_PATH);
    }

    /** Returns true for unknown keys; only a registered, confirmed exclusion disables the feature. */
    public boolean isEnabled(String key) {
        return !this.options.containsKey(key) || this.options.get(key).isEnabled();
    }

    /** Finds root-to-exact-class rules; any disabled ancestor takes precedence. */
    public @Nullable Option getEffectiveOptionForMixin(String relativeMixinClassName) {
        Option effective = null;
        int split = relativeMixinClassName.indexOf('.');
        while (true) {
            String key = split == -1 ? relativeMixinClassName : relativeMixinClassName.substring(0, split);
            if (this.options.containsKey(key)) {
                Option candidate = this.options.get(key);
                effective = candidate;
                if (!candidate.isEnabled()) return candidate;
            }
            if (split == -1) return effective;
            split = relativeMixinClassName.indexOf('.', split + 1);
        }
    }

    /** Read-only registered rules, in stable name order; option values are immutable. */
    public Object2ObjectMap<String, Option> getOptionMap() {
        return this.options;
    }

    /** Counts all rules, including hidden environment gates. */
    public int getOptionCount() {
        return this.options.size();
    }

    /** Counts visible rules explicitly selected by users, Mods, or delegates. */
    public int getOptionOverrideCount() {
        int count = 0;
        for (Option option : this.options.values()) {
            if (!option.isHidden() && option.isOverridden()) count++;
        }
        return count;
    }

    /** Persists this snapshot as UTF-8 without BOM; filesystem failures are reported to the caller. */
    public void save() throws IOException {
        EarlyConfigProperties.write(this.configFile, this.options);
    }

    /** Copies already resolved rules into a sorted snapshot; prefer EarlyConfigBuilder for registration. */
    public static DataEnergisticsEarlyConfig create(Path configFile, Object2ObjectMap<String, Option> options) {
        return new DataEnergisticsEarlyConfig(configFile, options);
    }

    private static DataEnergisticsEarlyConfig loadDefault() {
        return EarlyMixinRuleDiscovery.discover(CONFIG_PATH, FMLLoader.isProduction(),
                DatagenModLoader.isRunningDataGen()).load();
    }

    private static final class InstanceHolder {

        private static final DataEnergisticsEarlyConfig INSTANCE = loadDefault();
    }
}
