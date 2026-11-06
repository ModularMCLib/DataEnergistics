package com.fish_dan_.data_energistics.config.client.patternencoding;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.common.menu.patternprovider.PatternProviderClickStatistic;
import com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingClientPreferences.PreviewPanelPosition;
import com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingClientPreferences.PreviewPanelSize;
import com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingClientPreferences.ProviderDetailPanelPosition;
import com.fish_dan_.data_energistics.config.persistence.ConfigurationCodecs;

import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.regex.Pattern;

/**
 * Validated, immutable file snapshot. Session selection and statistic eviction belong to the client preference object.
 */
public record PatternEncodingConfiguration(Preferences preferences, Object2ObjectMap<String, ServerProfile> serverProfiles) {

    public static final int MAX_STATISTICS_PER_PROFILE = 2048;
    public static final int MAX_STATISTICS_TOTAL = 8192;
    public static final int MAX_SERVER_PROFILES = 32;
    public static final int MIN_PANEL_OFFSET = -8192;
    public static final int MAX_PANEL_OFFSET = 8192;
    private static final Pattern PROFILE_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    public static final PatternEncodingConfiguration DEFAULT = new PatternEncodingConfiguration(Preferences.DEFAULT, Object2ObjectMaps.emptyMap());
    public static final Codec<PatternEncodingConfiguration> CODEC = RecordCodecBuilder.<PatternEncodingConfiguration>create(instance -> instance.group(
            Preferences.CODEC.optionalFieldOf("preferences", Preferences.DEFAULT).forGetter(PatternEncodingConfiguration::preferences),
            Codec.unboundedMap(Codec.STRING, ServerProfile.CODEC).xmap(Object2ObjectLinkedOpenHashMap::new, values -> values)
                    .optionalFieldOf("serverProfiles", new Object2ObjectLinkedOpenHashMap<>())
                    .forGetter(value -> new Object2ObjectLinkedOpenHashMap<>(value.serverProfiles())))
            .apply(instance, PatternEncodingConfiguration::new)).validate(value -> {
                if (value.serverProfiles.size() > MAX_SERVER_PROFILES) {
                    return DataResult.error(() -> "Client preference server profiles exceed " + MAX_SERVER_PROFILES);
                }
                int statistics = 0;
                for (var entry : value.serverProfiles.object2ObjectEntrySet()) {
                    if (!PROFILE_DIGEST.matcher(entry.getKey()).matches()) {
                        return DataResult.error(() -> "Invalid server profile digest: " + entry.getKey());
                    }
                    statistics += entry.getValue().clickStatistics().size();
                }
                return statistics > MAX_STATISTICS_TOTAL ? DataResult.error(() -> "Client preference statistics exceed " + MAX_STATISTICS_TOTAL) :
                        DataResult.success(value);
            });

    public PatternEncodingConfiguration {
        serverProfiles = Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(serverProfiles));
    }

    public record Preferences(boolean uploadEnabled, boolean previewPanelPinned, boolean patternSourceEnabled,
                              ResourceLocation lastWorkstation, PreviewPanel previewPanel,
                              ProviderDetailPanelPosition providerDetailPanel) {

        private static final Codec<ProviderDetailPanelPosition> DETAIL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(MIN_PANEL_OFFSET, MAX_PANEL_OFFSET).fieldOf("relativeX").forGetter(ProviderDetailPanelPosition::relativeX),
                Codec.intRange(MIN_PANEL_OFFSET, MAX_PANEL_OFFSET).fieldOf("relativeY").forGetter(ProviderDetailPanelPosition::relativeY),
                Codec.BOOL.optionalFieldOf("saved", true).forGetter(ProviderDetailPanelPosition::saved))
                .apply(instance, ProviderDetailPanelPosition::new));
        public static final Preferences DEFAULT = new Preferences(true, false, true, Data_Energistics.id(""), PreviewPanel.DEFAULT, ProviderDetailPanelPosition.DEFAULT);
        public static final Codec<Preferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("uploadEnabled", true).forGetter(Preferences::uploadEnabled),
                Codec.BOOL.optionalFieldOf("previewPanelPinned", false).forGetter(Preferences::previewPanelPinned),
                Codec.BOOL.optionalFieldOf("patternSourceEnabled", true).forGetter(Preferences::patternSourceEnabled),
                ResourceLocation.CODEC.optionalFieldOf("lastWorkstation", Data_Energistics.id("")).forGetter(Preferences::lastWorkstation),
                PreviewPanel.CODEC.optionalFieldOf("previewPanel", PreviewPanel.DEFAULT).forGetter(Preferences::previewPanel),
                DETAIL_CODEC.optionalFieldOf("providerDetailPanel", ProviderDetailPanelPosition.DEFAULT).forGetter(Preferences::providerDetailPanel))
                .apply(instance, Preferences::new));

        public Preferences {
            previewPanelPinned &= uploadEnabled;
        }
    }

    public record PreviewPanel(PreviewPanelPosition position, PreviewPanelSize size) {

        private static final Codec<PreviewPanelPosition> POSITION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0, 1).fieldOf("xPercent").forGetter(PreviewPanelPosition::xPercent),
                Codec.doubleRange(0, 1).fieldOf("yPercent").forGetter(PreviewPanelPosition::yPercent),
                Codec.BOOL.optionalFieldOf("saved", true).forGetter(PreviewPanelPosition::saved))
                .apply(instance, PreviewPanelPosition::new));
        private static final Codec<PreviewPanelSize> SIZE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("width").forGetter(PreviewPanelSize::width),
                Codec.INT.fieldOf("height").forGetter(PreviewPanelSize::height),
                Codec.BOOL.optionalFieldOf("saved", true).forGetter(PreviewPanelSize::saved))
                .apply(instance, PreviewPanelSize::new));
        public static final PreviewPanel DEFAULT = new PreviewPanel(PreviewPanelPosition.DEFAULT, PreviewPanelSize.DEFAULT);
        public static final Codec<PreviewPanel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                POSITION_CODEC.optionalFieldOf("position", PreviewPanelPosition.DEFAULT).forGetter(PreviewPanel::position),
                SIZE_CODEC.optionalFieldOf("size", PreviewPanelSize.DEFAULT).forGetter(PreviewPanel::size))
                .apply(instance, PreviewPanel::new));
    }

    public record ServerProfile(long lastAccessEpochMillis, ObjectList<PatternProviderClickStatistic> clickStatistics) {

        private static final Codec<PatternProviderClickStatistic> STATISTIC_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.xmap(PatternEncodingRankingContext::of, PatternEncodingRankingContext::recipeTypeId)
                        .fieldOf("recipeTypeId").forGetter(PatternProviderClickStatistic::context),
                Codec.STRING.fieldOf("providerDigest").forGetter(PatternProviderClickStatistic::providerDigest),
                ConfigurationCodecs.NON_NEGATIVE_LONG.fieldOf("count").forGetter(PatternProviderClickStatistic::count),
                ConfigurationCodecs.NON_NEGATIVE_LONG.fieldOf("lastUsedEpochMillis").forGetter(PatternProviderClickStatistic::lastUsedEpochMillis))
                .apply(instance, PatternProviderClickStatistic::new));
        public static final Codec<ServerProfile> CODEC = RecordCodecBuilder.<ServerProfile>create(instance -> instance.group(
                ConfigurationCodecs.NON_NEGATIVE_LONG.fieldOf("lastAccessEpochMillis").forGetter(ServerProfile::lastAccessEpochMillis),
                STATISTIC_CODEC.listOf(0, MAX_STATISTICS_PER_PROFILE).xmap(ObjectImmutableList::new, values -> values)
                        .fieldOf("clickStatistics").forGetter(value -> new ObjectImmutableList<>(value.clickStatistics())))
                .apply(instance, ServerProfile::new)).validate(value -> {
                    var keys = new ObjectOpenHashSet<String>(value.clickStatistics.size());
                    for (var statistic : value.clickStatistics) {
                        if (!keys.add(statistic.stableKey())) return DataResult.error(() -> "Duplicate click statistic: " + statistic.stableKey());
                    }
                    return DataResult.success(value);
                });

        public ServerProfile {
            clickStatistics = new ObjectImmutableList<>(clickStatistics);
        }
    }
}
