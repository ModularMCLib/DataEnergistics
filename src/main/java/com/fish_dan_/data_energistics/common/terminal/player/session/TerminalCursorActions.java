package com.fish_dan_.data_energistics.common.terminal.player.session;

/** Cursor-dependent requests also synchronize the creative screen's client-owned carried stack. */
public final class TerminalCursorActions {

    private TerminalCursorActions() {}

    public static boolean usesCursor(String action) {
        return switch (action) {
            case "storage", "blank_pattern", "slot", "slot_drag", "ghost", "pin", "result", "cut", "stow" -> true;
            default -> false;
        };
    }
}
