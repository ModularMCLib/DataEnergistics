package com.fish_dan_.data_energistics.common.terminal.player.maintenance;

import appeng.api.stacks.AEKey;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable request thresholds tied to one marked network resource.
 */
public record TerminalMaintenanceEntry(UUID id, @Nullable AEKey resource, long lower, long upper, long batch,
                                       boolean enabled) {

    public TerminalMaintenanceEntry {
        if (lower < 0 || upper < 0 || batch <= 0)
            throw new IllegalArgumentException("Invalid terminal maintenance amounts");
    }

    public TerminalMaintenanceEntry resource(@Nullable AEKey value) {
        return new TerminalMaintenanceEntry(id, value, lower, upper, batch, enabled);
    }

    public TerminalMaintenanceEntry lower(long value) {
        return new TerminalMaintenanceEntry(id, resource, value, upper, batch, enabled);
    }

    public TerminalMaintenanceEntry upper(long value) {
        return new TerminalMaintenanceEntry(id, resource, lower, value, batch, enabled);
    }

    public TerminalMaintenanceEntry batch(long value) {
        return new TerminalMaintenanceEntry(id, resource, lower, upper, value, enabled);
    }

    public TerminalMaintenanceEntry enabled(boolean value) {
        return new TerminalMaintenanceEntry(id, resource, lower, upper, batch, value);
    }
}
