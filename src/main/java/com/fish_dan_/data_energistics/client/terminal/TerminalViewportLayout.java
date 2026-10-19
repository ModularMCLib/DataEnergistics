package com.fish_dan_.data_energistics.client.terminal;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

/**
 * Pure pixel geometry: fixed pin rows sit above the resource viewport surrounding the operation rectangle.
 */
@NullMarked
public record TerminalViewportLayout(int x, int y, int width, int height, int columns, int rows, int scrollHeight, int pinnedRows, int operationWidth, ObjectList<Cell> cells) {

    public static final int CELL_SIZE = 18;
    public static final int ENTRY_SIZE = 10;
    public static final int STORAGE_LEFT = 16;
    public static final int STORAGE_TOP = 18;
    public static final int TAB_WIDTH = 12;
    public static final int OPERATION_WIDTH = 108;
    public static final int OPERATION_HEIGHT = 58;
    public static final int PROCESSING_WIDTH = 124;
    public static final int STONECUTTING_WIDTH = 116;
    public static final int ENCODING_WIDTH = 24;
    private static final int FRAME_WIDTH = STORAGE_LEFT + TAB_WIDTH + ENCODING_WIDTH;
    private static final int FRAME_HEIGHT = 20;
    public static final int MIN_WIDTH = STORAGE_LEFT + PROCESSING_WIDTH + ENCODING_WIDTH + TAB_WIDTH;
    private static final int MIN_HEIGHT = 100;

    public static TerminalViewportLayout compute(int screenWidth, int screenHeight, float xPercent, float yPercent,
                                                 int requestedWidth, int requestedHeight, boolean expanded, boolean folded, int operationWidth, int manualPinRows, int automaticPins) {
        int width = Math.clamp(requestedWidth, MIN_WIDTH, Math.clamp(screenWidth, MIN_WIDTH, FRAME_WIDTH + 20 * CELL_SIZE));
        int height = Math.clamp(requestedHeight, MIN_HEIGHT, Math.clamp(screenHeight, MIN_HEIGHT, FRAME_HEIGHT + 20 * CELL_SIZE));
        int columns = Math.max(6, (width - FRAME_WIDTH) / CELL_SIZE);
        int rows = Math.max(1, (height - FRAME_HEIGHT) / CELL_SIZE);
        int operationLeft = operationLeft(width, operationWidth);
        int operationTop = operationTop(height);
        int availableScrollHeight = folded ? height - FRAME_HEIGHT : operationTop - STORAGE_TOP;
        int requestedPinRows = manualPinRows + (automaticPins + columns - 1) / columns;
        int pinnedRows = Math.clamp(availableScrollHeight / CELL_SIZE - 1, 0, requestedPinRows);
        ObjectList<Cell> cells = new ObjectArrayList<>();
        for (int row = pinnedRows; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int left = STORAGE_LEFT + column * CELL_SIZE;
                int top = STORAGE_TOP + row * CELL_SIZE;
                if (folded || left + CELL_SIZE <= operationLeft || left >= operationLeft + operationWidth ||
                        top + CELL_SIZE <= operationTop || top >= operationTop + OPERATION_HEIGHT)
                    cells.add(new Cell(column, row));
            }
        }
        int x = Math.round(Math.clamp(xPercent, 0, 1) * Math.max(0, screenWidth - (expanded ? width : ENTRY_SIZE)));
        int y = Math.round(Math.clamp(yPercent, 0, 1) * Math.max(0, screenHeight - (expanded ? height : ENTRY_SIZE)));
        return new TerminalViewportLayout(x, y, width, height, columns, rows,
                availableScrollHeight - pinnedRows * CELL_SIZE, pinnedRows, operationWidth, new ObjectImmutableList<>(cells));
    }

    /** Keeps the top-left corner fixed while resizing within the remaining screen area. */
    public TerminalViewportLayout resize(int screenWidth, int screenHeight, int requestedWidth, int requestedHeight,
                                         boolean folded, int operationWidth, int manualPinRows, int automaticPins) {
        TerminalViewportLayout resized = compute(screenWidth - x, screenHeight - y, 0, 0,
                requestedWidth, requestedHeight, true, folded, operationWidth, manualPinRows, automaticPins);
        return new TerminalViewportLayout(x, y, resized.width, resized.height, resized.columns, resized.rows,
                resized.scrollHeight, resized.pinnedRows, resized.operationWidth, resized.cells);
    }

    public int maximumPinnedRows() {
        return Math.max(0, (scrollHeight + pinnedRows * CELL_SIZE) / CELL_SIZE - 1);
    }

    public int cellCount() {
        return cells.size();
    }

    public record Cell(int column, int row) {}

    public int operationLeft() {
        return operationLeft(width, operationWidth);
    }

    public int operationTop() {
        return operationTop(height);
    }

    private static int operationLeft(int width, int operationWidth) {
        return width - TAB_WIDTH - operationWidth + 1;
    }

    private static int operationTop(int height) {
        return height - OPERATION_HEIGHT - 2;
    }

    public int storageWidth() {
        return columns * CELL_SIZE;
    }

    public int storageHeight() {
        return rows * CELL_SIZE;
    }

    public int maximumFirstRow(int totalEntries) {
        return (int) ((Math.max(0L, (long) totalEntries - cellCount()) + columns - 1) / columns);
    }

    public int clampOffset(int offset, int totalEntries) {
        return Math.clamp(offset / columns, 0, maximumFirstRow(totalEntries)) * columns;
    }
}
