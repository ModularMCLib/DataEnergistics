package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Orders intersecting screen primitives without comparing every target with every other target. */
final class TacticalOverlapLayers {

    private static final int CELL_SIZE = 32;
    private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
    private final ObjectArrayList<Cell> cellPool = new ObjectArrayList<>();
    private final ObjectArrayList<Entry> entries = new ObjectArrayList<>();
    private final IntArrayList visited = new IntArrayList();
    private int usedCells;

    int add(Area area, boolean text) {
        int minimumX = (int) Math.floor(area.left() / CELL_SIZE), minimumY = (int) Math.floor(area.top() / CELL_SIZE);
        int maximumX = (int) Math.floor(area.right() / CELL_SIZE), maximumY = (int) Math.floor(area.bottom() / CELL_SIZE);
        int layer = 0, stamp = entries.size() + 1;
        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                Cell cell = cells.get((long) x << 32 | y & 0xffffffffL);
                if (cell == null) continue;
                int limit = Math.max(cell.rectangleLayer + (text ? 1 : 0), cell.textLayer < 0 ? -1 : cell.textLayer + 1);
                if (layer >= limit) continue;
                // Newest entries usually contain the maximum layer, so dense piles stop after one intersection.
                for (int cursor = cell.members.size() - 1; cursor >= 0 && layer < limit; cursor--) {
                    int index = cell.members.getInt(cursor);
                    if (visited.getInt(index) == stamp) continue;
                    visited.set(index, stamp);
                    Entry previous = entries.get(index);
                    if (area.intersects(previous.area())) {
                        layer = Math.max(layer, previous.layer() + (text || previous.text() ? 1 : 0));
                    }
                }
            }
        }
        int index = entries.size();
        entries.add(new Entry(area, layer, text));
        visited.add(0);
        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                long key = (long) x << 32 | y & 0xffffffffL;
                Cell cell = cells.get(key);
                if (cell == null) {
                    if (usedCells == cellPool.size()) cellPool.add(new Cell());
                    cell = cellPool.get(usedCells++);
                    cell.members.clear();
                    cell.rectangleLayer = cell.textLayer = -1;
                    cells.put(key, cell);
                }
                cell.members.add(index);
                if (text) cell.textLayer = Math.max(cell.textLayer, layer);
                else cell.rectangleLayer = Math.max(cell.rectangleLayer, layer);
            }
        }
        return layer;
    }

    void clear() {
        cells.clear();
        entries.clear();
        visited.clear();
        usedCells = 0;
    }

    record Area(float left, float top, float right, float bottom) {

        boolean intersects(Area other) {
            return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
        }
    }

    private record Entry(Area area, int layer, boolean text) {}

    private static final class Cell {

        private final IntArrayList members = new IntArrayList();
        private int rectangleLayer = -1;
        private int textLayer = -1;
    }
}
