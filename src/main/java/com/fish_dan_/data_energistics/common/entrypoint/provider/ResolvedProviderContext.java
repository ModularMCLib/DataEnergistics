package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;

import org.jspecify.annotations.Nullable;

/** Identity and optional declaration resolved together, without repeating external identity callbacks. */
public record ResolvedProviderContext(ProviderIdentity identity, @Nullable ResolvedProviderBinding binding) {}
