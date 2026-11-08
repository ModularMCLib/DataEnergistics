package com.fish_dan_.data_energistics.config.client.hud.tactical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.function.Predicate;

/** Remembered mixed order survives hidden widgets and removed weapons; only visible entries consume space. */
public record HudSidebarLayout(ObjectList<HudSidebarEntry> left, ObjectList<HudSidebarEntry> right) {

    public static final HudSidebarLayout EMPTY = new HudSidebarLayout(ObjectLists.emptyList(), ObjectLists.emptyList());
    private static final Codec<ObjectList<HudSidebarEntry>> ENTRIES = HudSidebarEntry.CODEC.listOf(0, 15).xmap(ObjectArrayList::new, entries -> entries);
    public static final Codec<HudSidebarLayout> CODEC = RecordCodecBuilder.<HudSidebarLayout>create(instance -> instance.group(
            ENTRIES.optionalFieldOf("left", ObjectLists.emptyList()).forGetter(HudSidebarLayout::left),
            ENTRIES.optionalFieldOf("right", ObjectLists.emptyList()).forGetter(HudSidebarLayout::right))
            .apply(instance, HudSidebarLayout::new)).validate(layout -> {
                var entries = new ObjectOpenHashSet<HudSidebarEntry>();
                for (WeaponHudSide side : WeaponHudSide.values()) {
                    for (HudSidebarEntry entry : layout.entries(side)) {
                        if (!entries.add(entry)) return DataResult.error(() -> "A component may only occur once in the sidebar layout");
                    }
                }
                return DataResult.success(layout);
            });

    public HudSidebarLayout {
        left = ObjectLists.unmodifiable(new ObjectArrayList<>(left));
        right = ObjectLists.unmodifiable(new ObjectArrayList<>(right));
    }

    public ObjectList<HudSidebarEntry> entries(WeaponHudSide side) {
        return side == WeaponHudSide.LEFT ? left : right;
    }

    /** Assigns new occupied slots once, balancing visible weapons without moving remembered slots. */
    public HudSidebarLayout assign(int occupiedMask) {
        int assigned = 0, leftCount = 0, rightCount = 0;
        for (HudSidebarEntry entry : left) {
            if (entry instanceof HudSidebarEntry.Weapon(int slot)) {
                assigned |= 1 << slot;
                if ((occupiedMask & 1 << slot) != 0) leftCount++;
            }
        }
        for (HudSidebarEntry entry : right) {
            if (entry instanceof HudSidebarEntry.Weapon(int slot)) {
                assigned |= 1 << slot;
                if ((occupiedMask & 1 << slot) != 0) rightCount++;
            }
        }
        if ((occupiedMask & ~assigned) == 0) return this;
        ObjectList<HudSidebarEntry> nextLeft = new ObjectArrayList<>(left), nextRight = new ObjectArrayList<>(right);
        for (int slot = 0; slot < 8; slot++) {
            if ((occupiedMask & ~assigned & 1 << slot) == 0) continue;
            if (leftCount <= rightCount) {
                nextLeft.add(new HudSidebarEntry.Weapon(slot));
                leftCount++;
            } else {
                nextRight.add(new HudSidebarEntry.Weapon(slot));
                rightCount++;
            }
        }
        return new HudSidebarLayout(nextLeft, nextRight);
    }

    public ObjectList<HudSidebarEntry> visible(WeaponHudSide side, Predicate<HudSidebarEntry> visibleEntry) {
        ObjectList<HudSidebarEntry> visible = new ObjectArrayList<>();
        for (HudSidebarEntry entry : entries(side)) if (visibleEntry.test(entry)) visible.add(entry);
        return visible;
    }

    public boolean contains(HudSidebarEntry entry) {
        return left.contains(entry) || right.contains(entry);
    }

    public HudSidebarLayout remove(HudSidebarEntry entry) {
        ObjectList<HudSidebarEntry> nextLeft = new ObjectArrayList<>(left), nextRight = new ObjectArrayList<>(right);
        nextLeft.remove(entry);
        nextRight.remove(entry);
        return new HudSidebarLayout(nextLeft, nextRight);
    }

    /** The insertion index counts visible entries after excluding the dragged entry. */
    public HudSidebarLayout move(HudSidebarEntry entry, WeaponHudSide side, int insertionIndex, Predicate<HudSidebarEntry> visibleEntry) {
        var removed = remove(entry);
        ObjectList<HudSidebarEntry> nextLeft = new ObjectArrayList<>(removed.left), nextRight = new ObjectArrayList<>(removed.right);
        ObjectList<HudSidebarEntry> target = side == WeaponHudSide.LEFT ? nextLeft : nextRight;
        int index = 0, visible = 0;
        while (index < target.size() && visible < insertionIndex) {
            if (visibleEntry.test(target.get(index))) visible++;
            index++;
        }
        if (visible != insertionIndex) throw new IllegalArgumentException("Insertion index exceeds the visible sidebar");
        target.add(index, entry);
        return new HudSidebarLayout(nextLeft, nextRight);
    }
}
