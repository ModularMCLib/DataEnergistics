package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderClickStatistic;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Owns the local pattern-encoding preferences and per-server provider history used by client menus.
 *
 * <p>
 * Implementations are client-main-thread confined so UI changes and network acknowledgements cannot race file
 * persistence.
 * </p>
 */
public interface PatternEncodingClientPreferences {

    /**
     * Returns the local upload preference, defaulting to enabled when absent.
     */
    boolean uploadEnabled();

    /**
     * Persists the local upload preference immediately.
     */
    void setUploadEnabled(boolean enabled);

    /**
     * Returns whether opening a pattern-encoding terminal should show the upload panel by default.
     */
    boolean previewPanelPinned();

    /**
     * Persists the upload-panel default-open preference immediately.
     */
    void setPreviewPanelPinned(boolean pinned);

    /**
     * Returns the local source-writing preference, defaulting to enabled when absent.
     */
    boolean patternSourceEnabled();

    /**
     * Persists the local source-writing preference immediately.
     */
    void setPatternSourceEnabled(boolean enabled);

    /**
     * Returns the last selected workstation, or {@code null} when cleared or absent.
     */
    @Nullable
    ResourceLocation lastWorkstation();

    /**
     * Persists an explicit workstation value, including an explicit {@code null}.
     */
    void setLastWorkstation(@Nullable ResourceLocation workstation);

    /**
     * Returns the persisted upload-panel position as percentages of the available screen area, or empty when
     * automatic placement is active.
     */
    Optional<PreviewPanelPosition> previewPanelPosition();

    /** Persists an upload-panel position as horizontal and vertical percentages in the range {@code [0, 1]}. */
    void setPreviewPanelPosition(double xPercent, double yPercent);

    /**
     * Clears the percentage upload-panel position and restores automatic placement.
     */
    void clearPreviewPanelPosition();

    /**
     * Returns the optional screen-local provider-detail panel position shared by encoding terminals.
     */
    Optional<ProviderDetailPanelPosition> providerDetailPanelPosition();

    /**
     * Persists the provider-detail panel position without synchronizing it to the server.
     */
    void setProviderDetailPanelPosition(int relativeX, int relativeY);

    /**
     * Selects the isolated server profile that subsequent statistic operations use.
     */
    void activateServerProfile(String profileDigest);

    /**
     * Clears connection-scoped state so the next server cannot inherit statistics.
     */
    void deactivateServerProfile();

    /**
     * Returns statistics for the requested context and currently synchronized provider digests.
     */
    ObjectList<PatternProviderClickStatistic> statistics(PatternEncodingRankingContext context,
                                                         ObjectCollection<String> providerDigests);

    /**
     * Records one authoritative absolute count from the server and persists it idempotently.
     */
    void recordUpload(PatternEncodingRankingContext context, String providerDigest,
                      long absoluteCount, long successEpochMillis);

    record PreviewPanelPosition(double xPercent, double yPercent) {}

    record ProviderDetailPanelPosition(int relativeX, int relativeY) {}
}
