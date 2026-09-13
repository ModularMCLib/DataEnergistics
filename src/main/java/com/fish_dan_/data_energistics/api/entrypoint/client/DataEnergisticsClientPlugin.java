package com.fish_dan_.data_energistics.api.entrypoint.client;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;

/**
 * Client-phase plugin for {@link DataEnergisticsEntrypoint#clientOnly()} entries.
 * Implementations must be public with a public no-argument constructor. Registration runs once on the client
 * thread during queued setup; it must not create screen widgets or retain the staging registry.
 */
@FunctionalInterface
public interface DataEnergisticsClientPlugin {

    /**
     * Declares client extensions in a non-null plugin-local transaction; failures discard that transaction.
     */
    void register(DataEnergisticsClientRegistry registry);
}
