package com.fish_dan_.data_energistics.integration.ae.ae2.grid;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.function.BiConsumer;

/**
 * Server-thread access to mounted exact storage, before falling back to AE2's long-sized transfer API.
 * Mount traversal follows extraction priority and defers mount changes until traversal finishes.
 */
public interface ExactNetworkStorageAccess {

    /**
     * Extracts a positive request from exact mounts only, visiting each mount once. The callback immediately takes
     * ownership of each positive extraction and records its concrete source for rollback. It must not reenter the
     * network or throw. Returns the amount transferred; an unavailable or reentrant network transfers zero.
     */
    BigInteger data_energistics$extractExact(AEKey key, BigInteger amount, IActionSource source,
                                             BiConsumer<ExactExtractableStorage, BigInteger> received);
}
