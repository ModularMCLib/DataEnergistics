package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.RuleFormatException;
import com.fish_dan_.data_energistics.configuration.rules.codec.DataExtractorRuleEntries;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataProductionRuleConfigurationAdapter;
import com.fish_dan_.data_energistics.configuration.rules.schema.ExtractorOutputEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.MimeticCarrierEntry;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.io.ConfigIO;

import java.util.Arrays;
import java.util.Objects;

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

    @Configurable
    @Configurable.Comment({
            "Carrier collection and mimetic output rules. Choose MOB, ORE or CROP and list inputIds.",
            "requirement is damage for MOB and item count for ORE/CROP; zero uses the machine's default requirement.",
            "Empty recordedId uses the input id. Empty outputId keeps default drops; output settings default to LOOT, ITEM, FIXED and amount 1.",
            "载体采集与拟生产出规则：选择生物 MOB、矿物 ORE 或农作物 CROP，并填写输入 ID 数组。",
            "requirement 统一表示需求量：生物按伤害累计，矿物和农作物按数量累计；0 使用机器默认需求量。",
            "记录 ID 留空时使用输入 ID；单个产出 ID 留空时使用默认掉落；产出默认使用物品掉落、固定数量 1。"
    })
    public MimeticCarrierEntry[] carrierRules = MimeticCarrierEntry.defaults(defaultRuleValues());

    @Configurable
    @Configurable.Comment({
            "Additional extractor outputs matched by weapon item/tag arrays and optional target entity ids.",
            "Each row has one outputId with its family, kind, amount mode and amount. Empty outputId keeps default behavior.",
            "数据提取器额外产出规则：按武器物品 ID、标签数组和可选的目标实体 ID 数组匹配。",
            "每条规则填写单个产出 ID 及其类别、类型、数量模式与数量；目标留空匹配所有生物，产出留空保留默认行为。"
    })
    public ExtractorOutputEntry[] extractorOutputRules = {};

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
                    this.extractorOutputRules,
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
        var settings = DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor;
        int result = Objects.hash(settings.mobRequiredDamage, settings.oreRequiredAmount, settings.cropRequiredAmount);
        for (MimeticCarrierEntry entry : this.carrierRules) {
            result = 31 * result + hashMimeticCarrierEntry(entry);
        }
        for (ExtractorOutputEntry entry : this.extractorOutputRules) {
            result = 31 * result + hashExtractorEntry(entry);
        }
        return result;
    }

    private static int hashMimeticCarrierEntry(MimeticCarrierEntry entry) {
        int result = entry.dataType().hashCode();
        result = 31 * result + Arrays.hashCode(entry.inputIds());
        result = 31 * result + entry.recordedId().hashCode();
        result = 31 * result + Float.hashCode(entry.progressPerInput());
        result = 31 * result + Float.hashCode(entry.requirement());
        result = 31 * result + entry.outputFamily().hashCode();
        result = 31 * result + entry.keyKind().hashCode();
        result = 31 * result + entry.outputId().hashCode();
        result = 31 * result + entry.amountMode().hashCode();
        result = 31 * result + Long.hashCode(entry.amount());
        return result;
    }

    private static int hashExtractorEntry(ExtractorOutputEntry entry) {
        int result = Arrays.hashCode(entry.weaponItems());
        result = 31 * result + Arrays.hashCode(entry.weaponTags());
        result = 31 * result + Arrays.hashCode(entry.targetEntityIds());
        result = 31 * result + entry.outputFamily().hashCode();
        result = 31 * result + entry.keyKind().hashCode();
        result = 31 * result + entry.outputId().hashCode();
        result = 31 * result + entry.amountMode().hashCode();
        return 31 * result + Long.hashCode(entry.amount());
    }

    private static DefaultRuleValues defaultRuleValues() {
        return new DefaultRuleValues(
                DefaultRuleValues.builtInCropRules(),
                (float) DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.cropRequiredAmount,
                (float) DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.oreRequiredAmount);
    }
}
