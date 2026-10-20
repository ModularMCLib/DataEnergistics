package com.fish_dan_.data_energistics.common.terminal.player.connection;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;

/**
 * A freshly resolved server-thread connection; callers revalidate before each transaction.
 */
public record TerminalConnection(long frequency, IGrid grid, IActionHost host, IActionSource source) {

    public MEStorage storage() {
        return grid.getStorageService().getInventory();
    }
}
