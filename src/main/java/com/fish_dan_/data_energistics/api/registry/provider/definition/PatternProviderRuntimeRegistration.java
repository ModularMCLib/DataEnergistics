package com.fish_dan_.data_energistics.api.registry.provider.definition;

import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderMenuOpenResult;
import com.fish_dan_.data_energistics.api.registry.provider.dispatch.PatternProviderDispatchHooks;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadataSource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderRuntimeLink;

import appeng.api.upgrades.IUpgradeInventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Adapts existing provider classes without adding Data Energistics interfaces or mixins to them.
 *
 * <p>
 * Declared during common setup and frozen with the plugin transaction. Provider operation callbacks run on the server
 * thread. Upgrade inventory callbacks and dispatch factories run on the calling host's logical thread. Callbacks must
 * not retain a live provider after its lifecycle ends. Each capability selects the first matching
 * registration in descending priority and then declaration order; subclasses match their registered parent type.
 * Identity, link and metadata callbacks return non-null values. Exceptions reach the existing operation boundary.
 * </p>
 *
 * @param registrationId         stable ID owned by the registering plugin
 * @param providerType           native class or interface whose instances the callbacks accept
 * @param priority               larger values select callbacks before lower-priority declarations
 * @param runtimeLink            optional publication-to-container callback
 * @param identitySource         optional stable external identity callback for the terminal-visible container
 * @param matchingMetadataSource optional read-only upload-ranking callback for the terminal-visible container
 * @param menuOpener             optional single-provider menu callback; PASS delegates to the native menu path
 * @param upgradeInventorySource optional live upgrade inventory lookup; null means this host has no such inventory
 * @param dispatchHooks          optional per-instance policy factory called during native construction; null declines
 *                               unsupported hosts
 * @param <T>                    original provider type, which need not depend on Data Energistics
 */
@Builder
public record PatternProviderRuntimeRegistration<T>(
                                                    ResourceLocation registrationId,
                                                    Class<T> providerType,
                                                    int priority,
                                                    @Nullable PatternProviderRuntimeLink<T> runtimeLink,
                                                    @Nullable PatternProviderIdentitySource<T> identitySource,
                                                    @Nullable PatternProviderMatchingMetadataSource<T> matchingMetadataSource,
                                                    @Nullable BiFunction<T, ServerPlayer, PatternProviderMenuOpenResult> menuOpener,
                                                    @Nullable Function<T, @Nullable IUpgradeInventory> upgradeInventorySource,
                                                    @Nullable Function<T, @Nullable PatternProviderDispatchHooks> dispatchHooks) {}
