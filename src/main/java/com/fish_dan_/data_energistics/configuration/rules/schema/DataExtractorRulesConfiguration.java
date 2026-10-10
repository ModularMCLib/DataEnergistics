package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.RuleFormatException;
import com.fish_dan_.data_energistics.configuration.rules.codec.DataExtractorRuleEntries;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.io.ConfigIO;

import java.util.Arrays;

/** Direct Configuration-owned schema for the independently stored Data Extractor rule YAML. */
@Config(
        id = DataExtractorRulesConfiguration.CONFIG_ID,
        filename = DataExtractorRulesConfiguration.FILENAME,
        group = Data_Energistics.MODID)
public final class DataExtractorRulesConfiguration {

    public static final String CONFIG_ID = Data_Energistics.MODID + "_data_extractor_rules";
    public static final String FILENAME = Data_Energistics.MODID + "/data_extractor_rules";

    static {
        DataProductionRuleConfigurationAdapter.register();
    }

    public static final ConfigHolder<DataExtractorRulesConfiguration> HOLDER = Configuration.registerConfig(DataExtractorRulesConfiguration.class, ConfigFormats.YAML);
    public static final DataExtractorRulesConfiguration INSTANCE = HOLDER.getConfigInstance();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "One complete carrier rule per entry: slot, data type, input item, recorded item, progress and required amount.",
            "每个条目是一整条载体规则：槽位、数据类型、输入物品、记录物品、进度和需求量。"
    })
    public CarrierRuleEntry[] carrierRules = CarrierRuleEntry.defaults(defaultRuleValues());

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "One complete legacy loot rule per entry: data type, recorded item, output item and count.",
            "每个条目是一整条旧战利品规则：数据类型、记录物品、输出物品和数量。"
    })
    public OutputRuleEntry[] outputRules = OutputRuleEntry.defaults();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "One complete mimetic rule per entry. Each entry contains data type, recorded id, family, key kind, key id, amount mode and amount.",
            "每个条目是一整条数据拟生规则；条目包含数据类型、记录 ID、产出类别、键类型、键 ID、数量模式和数量。"
    })
    public MimeticOutputEntry[] mimeticOutputs = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "One complete extractor rule per entry. Weapon and target ids are arrays; empty target ids match every target.",
            "每个条目是一整条数据提取器规则；武器和目标 ID 都是数组；空目标数组匹配所有目标。"
    })
    public ExtractorOutputEntry[] extractorOutputs = {};

    private transient LoadedRules cachedRules;
    private transient int cachedFingerprint;
    private transient boolean hasCachedRules;

    public DataExtractorRulesConfiguration() {}

    /** Compiles the current typed record arrays; Configuration's Auto-Sync updates the source arrays directly. */
    public synchronized LoadedRules rules() {
        int fingerprint = configurationFingerprint();
        if (this.hasCachedRules && this.cachedFingerprint == fingerprint) {
            return this.cachedRules;
        }
        try {
            LoadedRules compiled = DataExtractorRuleEntries.compile(
                    this.carrierRules,
                    this.outputRules,
                    this.mimeticOutputs,
                    this.extractorOutputs,
                    ConfigIO.getConfigFile(HOLDER).toPath());
            this.cachedRules = compiled;
            this.cachedFingerprint = fingerprint;
            this.hasCachedRules = true;
            return compiled;
        } catch (RuleFormatException exception) {
            throw new IllegalStateException("Data Extractor rule configuration is invalid", exception);
        }
    }

    private int configurationFingerprint() {
        int result = 1;
        for (CarrierRuleEntry entry : this.carrierRules) {
            result = 31 * result + hashCarrierEntry(entry);
        }
        for (OutputRuleEntry entry : this.outputRules) {
            result = 31 * result + hashOutputEntry(entry);
        }
        for (MimeticOutputEntry entry : this.mimeticOutputs) {
            result = 31 * result + hashMimeticEntry(entry);
        }
        for (ExtractorOutputEntry entry : this.extractorOutputs) {
            result = 31 * result + hashExtractorEntry(entry);
        }
        return result;
    }

    private static int hashCarrierEntry(CarrierRuleEntry entry) {
        int result = entry.slot() == null ? 0 : entry.slot().hashCode();
        result = 31 * result + (entry.dataType() == null ? 0 : entry.dataType().hashCode());
        result = 31 * result + entry.inputItem().hashCode();
        result = 31 * result + entry.recordedItem().hashCode();
        result = 31 * result + Float.hashCode(entry.progressPerItem());
        return 31 * result + Float.hashCode(entry.requiredAmount());
    }

    private static int hashOutputEntry(OutputRuleEntry entry) {
        int result = entry.dataType() == null ? 0 : entry.dataType().hashCode();
        result = 31 * result + entry.recordedItem().hashCode();
        result = 31 * result + entry.item().hashCode();
        return 31 * result + Integer.hashCode(entry.count());
    }

    private static int hashMimeticEntry(MimeticOutputEntry entry) {
        int result = entry.dataType() == null ? 0 : entry.dataType().hashCode();
        result = 31 * result + entry.recordedId().hashCode();
        result = 31 * result + (entry.outputFamily() == null ? 0 : entry.outputFamily().hashCode());
        result = 31 * result + (entry.keyKind() == null ? 0 : entry.keyKind().hashCode());
        result = 31 * result + entry.keyId().hashCode();
        result = 31 * result + (entry.amountMode() == null ? 0 : entry.amountMode().hashCode());
        return 31 * result + Long.hashCode(entry.amount());
    }

    private static int hashExtractorEntry(ExtractorOutputEntry entry) {
        int result = Arrays.hashCode(entry.weaponItems());
        result = 31 * result + Arrays.hashCode(entry.weaponTags());
        result = 31 * result + Arrays.hashCode(entry.targetEntityIds());
        result = 31 * result + (entry.outputFamily() == null ? 0 : entry.outputFamily().hashCode());
        result = 31 * result + (entry.keyKind() == null ? 0 : entry.keyKind().hashCode());
        result = 31 * result + entry.keyId().hashCode();
        result = 31 * result + (entry.amountMode() == null ? 0 : entry.amountMode().hashCode());
        return 31 * result + Long.hashCode(entry.amount());
    }

    private static DefaultRuleValues defaultRuleValues() {
        return new DefaultRuleValues(
                DefaultRuleValues.builtInCropRules(),
                (float) DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.cropRequiredAmount,
                (float) DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.oreRequiredAmount);
    }
}
