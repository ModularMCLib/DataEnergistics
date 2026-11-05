package com.fish_dan_.data_energistics.util.client.vision;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Fixed-size primitive pages. Ownership transfers after capture, avoiding a final whole-grid flatten/copy. */
@OnlyIn(Dist.CLIENT)
public final class VisionOccupancy {

    private static final int PAGE_BITS = 10;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private final ObjectArrayList<long[]> pages = new ObjectArrayList<>();
    private int size;

    public void append(long value) {
        int offset = size & (PAGE_SIZE - 1);
        if (offset == 0) pages.add(new long[PAGE_SIZE]);
        pages.get(size++ >> PAGE_BITS)[offset] = value;
    }

    public boolean empty() {
        return size == 0;
    }

    public boolean contains(long cell) {
        int low = 0, high = size;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (get(middle) <= cell) low = middle + 1;
            else high = middle;
        }
        return (low & 1) != 0;
    }

    public boolean fills(long cells) {
        return size == 2 && get(0) == 0 && get(1) == cells;
    }

    private long get(int index) {
        return pages.get(index >> PAGE_BITS)[index & (PAGE_SIZE - 1)];
    }
}
