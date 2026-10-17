package com.fish_dan_.data_energistics.client.terminal;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

/**
 * Pure pixel geometry: resource cells occupy the viewport around the fixed operation rectangle.
 */
@NullMarked
public record TerminalViewportLayout(int x, int y, int width, int height, int columns, int rows, int scrollHeight, ObjectList<Cell> cells) {

    public static final int CELL_SIZE = 18;
    public static final int STORAGE_LEFT = 16;
    public static final int STORAGE_TOP = 18;
    public static final int TAB_WIDTH = 12;
    public static final int OPERATION_WIDTH = 108;
    public static final int OPERATION_HEIGHT = 58;
    private static final int FRAME_WIDTH = 42;
    private static final int FRAME_HEIGHT = 20;
    private static final int MIN_WIDTH = 150;
    private static final int MIN_HEIGHT = 100;

    public static TerminalViewportLayout compute(int screenWidth, int screenHeight, int x, int y,
                                                 int requestedWidth, int requestedHeight, boolean folded) {
        int width = Math.clamp(requestedWidth, MIN_WIDTH, Math.clamp(screenWidth, MIN_WIDTH, FRAME_WIDTH + 20 * CELL_SIZE));
        int height = Math.clamp(requestedHeight, MIN_HEIGHT, Math.clamp(screenHeight, MIN_HEIGHT, FRAME_HEIGHT + 20 * CELL_SIZE));
        int columns = Math.max(6, (width - FRAME_WIDTH) / CELL_SIZE);
        int rows = Math.max(1, (height - FRAME_HEIGHT) / CELL_SIZE);
        int operationLeft = operationLeft(width);
        int operationTop = operationTop(height);
        ObjectList<Cell> cells = new ObjectArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int left = STORAGE_LEFT + column * CELL_SIZE;
                int top = STORAGE_TOP + row * CELL_SIZE;
                if (folded || left + CELL_SIZE <= operationLeft || left >= operationLeft + OPERATION_WIDTH ||
                        top + CELL_SIZE <= operationTop || top >= operationTop + OPERATION_HEIGHT)
                    cells.add(new Cell(column, row));
            }
        }
        return new TerminalViewportLayout(Math.clamp(x, 0, Math.max(0, screenWidth - width)),
                Math.clamp(y, 0, Math.max(0, screenHeight - height)), width, height, columns, rows,
                folded ? height - FRAME_HEIGHT : operationTop - STORAGE_TOP, new ObjectImmutableList<>(cells));
    }

    public int cellCount() {
        return cells.size();
    }

    public record Cell(int column, int row) {}

    public int operationLeft() {
        return operationLeft(width);
    }

    public int operationTop() {
        return operationTop(height);
    }

    private static int operationLeft(int width) {
        return width - TAB_WIDTH - OPERATION_WIDTH;
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
