package com.fish_dan_.data_energistics.api.registry.adaptive;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Built-in Adaptive toolbar action identifiers.
 */
public final class AdaptivePatternProviderToolbarActions {

    public static final ResourceLocation BLOCKING_MODE = Data_Energistics.id("adaptive_pattern_provider/blocking_mode");
    public static final ResourceLocation LOCK_CRAFTING_MODE = Data_Energistics.id("adaptive_pattern_provider/lock_crafting_mode");
    public static final ResourceLocation PATTERN_ACCESS_TERMINAL = Data_Energistics.id("adaptive_pattern_provider/pattern_access_terminal");
    public static final ResourceLocation PREVIOUS_PAGE = Data_Energistics.id("adaptive_pattern_provider/previous_page");
    public static final ResourceLocation NEXT_PAGE = Data_Energistics.id("adaptive_pattern_provider/next_page");

    public static final ResourceLocation REDSTONE_TUNING = Data_Energistics.id("adaptive_pattern_provider/redstone_tuning");
    public static final ResourceLocation FILTERED_IMPORT = Data_Energistics.id("adaptive_pattern_provider/filtered_import");
    public static final ResourceLocation RESONATING_PULL = Data_Energistics.id("adaptive_pattern_provider/resonating_pull");
    public static final ResourceLocation CONNECTOR_MODE = Data_Energistics.id("adaptive_pattern_provider/connector_mode");
    public static final ResourceLocation CONNECTOR_POLICY = Data_Energistics.id("adaptive_pattern_provider/connector_policy");

    private static final ObjectList<AdaptivePatternProviderToolbarAction> STANDARD = ObjectList.of(
            new AdaptivePatternProviderToolbarAction(BLOCKING_MODE),
            new AdaptivePatternProviderToolbarAction(LOCK_CRAFTING_MODE),
            new AdaptivePatternProviderToolbarAction(PATTERN_ACCESS_TERMINAL),
            new AdaptivePatternProviderToolbarAction(PREVIOUS_PAGE),
            new AdaptivePatternProviderToolbarAction(NEXT_PAGE),
            new AdaptivePatternProviderToolbarAction(REDSTONE_TUNING),
            new AdaptivePatternProviderToolbarAction(CONNECTOR_POLICY));

    /** Common controls remain available when the provider slot is empty; conditional buttons hide themselves. */
    public static ObjectList<AdaptivePatternProviderToolbarAction> standard() {
        return STANDARD;
    }

    private AdaptivePatternProviderToolbarActions() {}
}
