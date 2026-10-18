package com.fish_dan_.data_energistics.common.terminal.player.workstation;

/**
 * Stable tab order; persisted identifiers do not depend on enum ordinals.
 */
public enum TerminalWorkspace {

    CRAFTING("crafting", 9),
    PROCESSING("processing", 9),
    SMITHING("smithing", 3),
    STONECUTTING("stonecutting", 1),
    ANVIL("anvil", 2);

    private final String id;
    private final int inputSlots;

    TerminalWorkspace(String id, int inputSlots) {
        this.id = id;
        this.inputSlots = inputSlots;
    }

    public String id() {
        return id;
    }

    public int inputSlots() {
        return inputSlots;
    }

    public int patternInputSlots() {
        return this == PROCESSING ? 81 : inputSlots;
    }

    public int patternOutputSlots() {
        return this == PROCESSING ? 27 : 3;
    }

    public boolean supportsEncoding() {
        return this != ANVIL;
    }

    public static TerminalWorkspace byId(String id) {
        for (TerminalWorkspace workspace : values()) {
            if (workspace.id.equals(id)) return workspace;
        }
        throw new IllegalArgumentException("Unknown terminal workspace: " + id);
    }
}
