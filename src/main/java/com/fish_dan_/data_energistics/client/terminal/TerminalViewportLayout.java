package com.fish_dan_.data_energistics.client.terminal;

/**
 * Pure pixel geometry shared by placement, resizing, paging and folding; no Minecraft state is consulted.
 */
public record TerminalViewportLayout(int x, int y, int width, int height, int columns, int rows, int workspaceHeight) {

    public static final int CELL_SIZE = 18;
    public static final int STORAGE_LEFT = 20;
    public static final int STORAGE_TOP = 28;
    private static final int FRAME_WIDTH = 40;
    private static final int FRAME_HEIGHT = 54;
    private static final int WORKSPACE_HEIGHT = 96;
    private static final int MIN_WIDTH = 148;
    private static final int MIN_HEIGHT = 168;

    public static TerminalViewportLayout compute(int screenWidth, int screenHeight, int x, int y,
                                                 int requestedWidth, int requestedHeight, boolean folded) {
        int width = Math.clamp(requestedWidth, MIN_WIDTH, Math.max(MIN_WIDTH, Math.min(screenWidth, FRAME_WIDTH + 20 * CELL_SIZE)));
        int height = Math.clamp(requestedHeight, MIN_HEIGHT, Math.max(MIN_HEIGHT, Math.min(screenHeight, FRAME_HEIGHT + 20 * CELL_SIZE)));
        int workspaceHeight = folded ? 0 : WORKSPACE_HEIGHT;
        int columns = Math.max(6, (width - FRAME_WIDTH) / CELL_SIZE);
        int rows = Math.max(1, (height - FRAME_HEIGHT - workspaceHeight) / CELL_SIZE);
        return new TerminalViewportLayout(Math.clamp(x, 0, Math.max(0, screenWidth - width)),
                Math.clamp(y, 0, Math.max(0, screenHeight - height)), width, height, columns, rows, workspaceHeight);
    }

    public int cellCount() {
        return columns * rows;
    }

    public int storageWidth() {
        return columns * CELL_SIZE;
    }

    public int storageHeight() {
        return rows * CELL_SIZE;
    }

    public int maximumFirstRow(int totalEntries) {
        return (int) Math.max(0, ((long) totalEntries + columns - 1) / columns - rows);
    }

    public int clampOffset(int offset, int totalEntries) {
        return Math.min(Math.max(0, offset / columns), maximumFirstRow(totalEntries)) * columns;
    }
}
