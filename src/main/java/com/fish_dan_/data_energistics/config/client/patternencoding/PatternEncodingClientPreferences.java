package com.fish_dan_.data_energistics.config.client.patternencoding;

import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.common.menu.patternprovider.PatternProviderClickStatistic;
import com.fish_dan_.data_energistics.config.persistence.JsonConfiguration;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.util.Comparator;
import java.util.function.BooleanSupplier;

import static com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingConfiguration.MAX_SERVER_PROFILES;
import static com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingConfiguration.MAX_STATISTICS_PER_PROFILE;
import static com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingConfiguration.MAX_STATISTICS_TOTAL;

/** Client-main-thread preferences and per-server history. File persistence belongs to the registered configuration. */
public final class PatternEncodingClientPreferences {

    private static final Comparator<PatternProviderClickStatistic> EVICTION_ORDER = Comparator
            .comparingLong(PatternProviderClickStatistic::count)
            .thenComparingLong(PatternProviderClickStatistic::lastUsedEpochMillis)
            .thenComparing(PatternProviderClickStatistic::stableKey);

    private final JsonConfiguration<PatternEncodingConfiguration> configuration;
    private final BooleanSupplier mainThreadCheck;
    private final Clock clock;
    private final Object2ObjectLinkedOpenHashMap<String, ServerProfile> serverProfiles = new Object2ObjectLinkedOpenHashMap<>();
    private final ServerProfile sessionProfile = new ServerProfile(0L);
    private boolean uploadEnabled;
    private boolean previewPanelPinned;
    private boolean patternSourceEnabled;
    private ResourceLocation lastWorkstation;
    private PreviewPanelPosition previewPanelPosition;
    private PreviewPanelSize previewPanelSize;
    private final ProviderDetailPanelPosition providerDetailPanel;
    private String activeServerProfile = "";

    public PatternEncodingClientPreferences(JsonConfiguration<PatternEncodingConfiguration> configuration,
                                            BooleanSupplier mainThreadCheck, Clock clock) {
        this.configuration = configuration;
        this.mainThreadCheck = mainThreadCheck;
        this.clock = clock;
        PatternEncodingConfiguration saved = configuration.get();
        var preferences = saved.preferences();
        this.uploadEnabled = preferences.uploadEnabled();
        this.previewPanelPinned = preferences.previewPanelPinned();
        this.patternSourceEnabled = preferences.patternSourceEnabled();
        this.lastWorkstation = preferences.lastWorkstation();
        this.previewPanelPosition = preferences.previewPanel().position();
        this.previewPanelSize = preferences.previewPanel().size();
        this.providerDetailPanel = preferences.providerDetailPanel();
        for (var entry : saved.serverProfiles().object2ObjectEntrySet()) {
            var profile = new ServerProfile(entry.getValue().lastAccessEpochMillis());
            for (var statistic : entry.getValue().clickStatistics()) profile.statistics.put(statistic.stableKey(), statistic);
            this.serverProfiles.put(entry.getKey(), profile);
        }
    }

    public boolean uploadEnabled() {
        checkMainThread();
        return this.uploadEnabled;
    }

    public void setUploadEnabled(boolean enabled) {
        checkMainThread();
        this.uploadEnabled = enabled;
        if (!enabled) {
            this.previewPanelPinned = false;
        }
        save();
    }

    public boolean previewPanelPinned() {
        checkMainThread();
        return this.previewPanelPinned;
    }

    public void setPreviewPanelPinned(boolean pinned) {
        checkMainThread();
        this.previewPanelPinned = pinned && this.uploadEnabled;
        save();
    }

    public boolean patternSourceEnabled() {
        checkMainThread();
        return this.patternSourceEnabled;
    }

    public void setPatternSourceEnabled(boolean enabled) {
        checkMainThread();
        this.patternSourceEnabled = enabled;
        save();
    }

    public ResourceLocation lastWorkstation() {
        checkMainThread();
        return this.lastWorkstation;
    }

    public void setLastWorkstation(ResourceLocation workstation) {
        checkMainThread();
        this.lastWorkstation = workstation;
        save();
    }

    public PreviewPanelPosition previewPanelPosition() {
        checkMainThread();
        return this.previewPanelPosition;
    }

    public PreviewPanelSize previewPanelSize() {
        checkMainThread();
        return this.previewPanelSize;
    }

    public void setPreviewPanelGeometry(double xPercent, double yPercent, int width, int height) {
        checkMainThread();
        this.previewPanelPosition = new PreviewPanelPosition(xPercent, yPercent);
        this.previewPanelSize = new PreviewPanelSize(width, height);
        save();
    }

    public void activateServerProfile(String profileDigest) {
        checkMainThread();
        this.activeServerProfile = profileDigest;
        long now = this.clock.millis();
        ServerProfile profile = this.serverProfiles.computeIfAbsent(
                profileDigest, ignored -> new ServerProfile(now));
        profile.lastAccessEpochMillis = Math.max(profile.lastAccessEpochMillis, now);
        trimProfiles();
        save();
    }

    public void deactivateServerProfile() {
        checkMainThread();
        this.activeServerProfile = "";
        this.sessionProfile.statistics.clear();
        this.sessionProfile.lastAccessEpochMillis = 0L;
    }

    public ObjectList<PatternProviderClickStatistic> statistics(PatternEncodingRankingContext context,
                                                                ObjectCollection<String> providerDigests) {
        checkMainThread();
        ObjectSet<String> requested = new ObjectOpenHashSet<>(providerDigests);
        ObjectList<PatternProviderClickStatistic> result = new ObjectArrayList<>();
        for (PatternProviderClickStatistic statistic : activeProfile().statistics.values()) {
            if (statistic.context().equals(context) && requested.contains(statistic.providerDigest())) {
                result.add(statistic);
            }
        }
        result.sort(Comparator.comparing(PatternProviderClickStatistic::stableKey));
        return ObjectLists.unmodifiable(result);
    }

    public void recordUpload(PatternEncodingRankingContext context, String providerDigest,
                             long absoluteCount, long successEpochMillis) {
        checkMainThread();
        PatternProviderClickStatistic incoming = new PatternProviderClickStatistic(
                context, providerDigest, absoluteCount, successEpochMillis);
        ServerProfile profile = activeProfile();
        String key = incoming.stableKey();
        profile.statistics.merge(key, incoming, (existing, next) -> new PatternProviderClickStatistic(
                next.context(),
                next.providerDigest(),
                Math.max(existing.count(), next.count()),
                Math.max(existing.lastUsedEpochMillis(), next.lastUsedEpochMillis())));
        profile.lastAccessEpochMillis = Math.max(profile.lastAccessEpochMillis, this.clock.millis());
        trimStatistics();
        if (!this.activeServerProfile.isEmpty()) {
            save();
        }
    }

    private void save() {
        trimProfiles();
        trimStatistics();
        Object2ObjectMap<String, ServerProfile> persistedProfiles = copyProfiles();
        PatternEncodingConfiguration candidate = snapshot(persistedProfiles);
        while (this.configuration.encodedBytes(candidate) > JsonConfiguration.MAXIMUM_BYTES) {
            ProfileStatistic lowest = findLowestPersistedStatistic(persistedProfiles);
            if (lowest == null) {
                throw new IllegalStateException("Client preference globals exceed the maximum file size without any evictable statistics");
            }
            lowest.profile().statistics.remove(lowest.statistic().stableKey());
            candidate = snapshot(persistedProfiles);
        }
        if (this.configuration.save(candidate)) {
            this.serverProfiles.clear();
            this.serverProfiles.putAll(persistedProfiles);
        }
    }

    private PatternEncodingConfiguration snapshot(Object2ObjectMap<String, ServerProfile> profiles) {
        var globals = new PatternEncodingConfiguration.Preferences(this.uploadEnabled, this.previewPanelPinned,
                this.patternSourceEnabled, this.lastWorkstation,
                new PatternEncodingConfiguration.PreviewPanel(this.previewPanelPosition, this.previewPanelSize),
                this.providerDetailPanel);
        Object2ObjectMap<String, PatternEncodingConfiguration.ServerProfile> saved = new Object2ObjectLinkedOpenHashMap<>();
        profiles.object2ObjectEntrySet().stream().sorted(Comparator.comparing(Object2ObjectMap.Entry::getKey)).forEach(entry -> {
            var statistics = new ObjectArrayList<>(entry.getValue().statistics.values());
            statistics.sort(Comparator.comparing(PatternProviderClickStatistic::stableKey));
            saved.put(entry.getKey(), new PatternEncodingConfiguration.ServerProfile(entry.getValue().lastAccessEpochMillis, statistics));
        });
        return new PatternEncodingConfiguration(globals, saved);
    }

    private ServerProfile activeProfile() {
        if (this.activeServerProfile.isEmpty()) {
            return this.sessionProfile;
        }
        return this.serverProfiles.computeIfAbsent(
                this.activeServerProfile, ignored -> new ServerProfile(this.clock.millis()));
    }

    private Object2ObjectMap<String, ServerProfile> copyProfiles() {
        Object2ObjectMap<String, ServerProfile> copy = new Object2ObjectLinkedOpenHashMap<>();
        for (Object2ObjectMap.Entry<String, ServerProfile> entry : this.serverProfiles.object2ObjectEntrySet()) {
            ServerProfile profileCopy = new ServerProfile(entry.getValue().lastAccessEpochMillis);
            profileCopy.statistics.putAll(entry.getValue().statistics);
            copy.put(entry.getKey(), profileCopy);
        }
        return copy;
    }

    private void trimProfiles() {
        while (this.serverProfiles.size() > MAX_SERVER_PROFILES) {
            String removable = this.serverProfiles.object2ObjectEntrySet().stream()
                    .filter(entry -> !entry.getKey().equals(this.activeServerProfile))
                    .min(Comparator.<Object2ObjectMap.Entry<String, ServerProfile>>comparingLong(
                            entry -> entry.getValue().lastAccessEpochMillis)
                            .thenComparing(Object2ObjectMap.Entry::getKey))
                    .map(Object2ObjectMap.Entry::getKey)
                    .orElseThrow(() -> new IllegalStateException("Cannot evict the only active server profile"));
            this.serverProfiles.remove(removable);
        }
    }

    private void trimStatistics() {
        while (this.sessionProfile.statistics.size() > MAX_STATISTICS_PER_PROFILE) {
            removeLowestStatistic(this.sessionProfile);
        }
        for (ServerProfile profile : this.serverProfiles.values()) {
            while (profile.statistics.size() > MAX_STATISTICS_PER_PROFILE) {
                removeLowestStatistic(profile);
            }
        }
        while (totalStatisticCount() > MAX_STATISTICS_TOTAL) {
            ProfileStatistic lowest = findLowestPersistedStatistic();
            if (lowest == null) {
                throw new IllegalStateException("No persisted pattern statistic is available for eviction");
            }
            lowest.profile().statistics.remove(lowest.statistic().stableKey());
        }
    }

    @Nullable
    private ProfileStatistic findLowestPersistedStatistic() {
        return findLowestPersistedStatistic(this.serverProfiles);
    }

    @Nullable
    private static ProfileStatistic findLowestPersistedStatistic(Object2ObjectMap<String, ServerProfile> profiles) {
        ProfileStatistic lowest = null;
        for (Object2ObjectMap.Entry<String, ServerProfile> profileEntry : profiles.object2ObjectEntrySet()) {
            for (PatternProviderClickStatistic statistic : profileEntry.getValue().statistics.values()) {
                ProfileStatistic candidate = new ProfileStatistic(
                        profileEntry.getKey(), profileEntry.getValue(), statistic);
                if (lowest == null || compareProfileStatistic(candidate, lowest) < 0) {
                    lowest = candidate;
                }
            }
        }
        return lowest;
    }

    private static int compareProfileStatistic(ProfileStatistic left, ProfileStatistic right) {
        int comparison = EVICTION_ORDER.compare(left.statistic(), right.statistic());
        return comparison != 0 ? comparison : left.profileDigest().compareTo(right.profileDigest());
    }

    private int totalStatisticCount() {
        return this.serverProfiles.values().stream().mapToInt(profile -> profile.statistics.size()).sum();
    }

    private static void removeLowestStatistic(ServerProfile profile) {
        PatternProviderClickStatistic statistic = profile.statistics.values().stream().min(EVICTION_ORDER).orElseThrow();
        profile.statistics.remove(statistic.stableKey());
    }

    private void checkMainThread() {
        if (!this.mainThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Pattern encoding client preferences may only be accessed on the client main thread");
        }
    }

    public record PreviewPanelPosition(double xPercent, double yPercent, boolean saved) {

        public static final PreviewPanelPosition DEFAULT = new PreviewPanelPosition(0, 0, false);

        public PreviewPanelPosition(double xPercent, double yPercent) {
            this(xPercent, yPercent, true);
        }
    }

    public record PreviewPanelSize(int width, int height, boolean saved) {

        public static final PreviewPanelSize DEFAULT = new PreviewPanelSize(0, 0, false);

        public PreviewPanelSize(int width, int height) {
            this(width, height, true);
        }
    }

    public record ProviderDetailPanelPosition(int relativeX, int relativeY, boolean saved) {

        public static final ProviderDetailPanelPosition DEFAULT = new ProviderDetailPanelPosition(0, 0, false);
    }

    private static final class ServerProfile {

        private long lastAccessEpochMillis;
        private final Object2ObjectMap<String, PatternProviderClickStatistic> statistics = new Object2ObjectLinkedOpenHashMap<>();

        private ServerProfile(long lastAccessEpochMillis) {
            this.lastAccessEpochMillis = lastAccessEpochMillis;
        }
    }

    private record ProfileStatistic(String profileDigest, ServerProfile profile,
                                    PatternProviderClickStatistic statistic) {}
}
