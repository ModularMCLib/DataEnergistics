package com.fish_dan_.data_energistics.config.client.hud.tactical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/** Slot-based order survives removal; only occupied slots contribute to visible counts. */
public record WeaponHudLayout(IntList left, IntList right) {

    public static final WeaponHudLayout EMPTY = new WeaponHudLayout(IntLists.emptyList(), IntLists.emptyList());
    private static final Codec<IntList> SLOTS = Codec.intRange(0, 7).listOf(0, 8).xmap(IntArrayList::new, slots -> slots);
    public static final Codec<WeaponHudLayout> CODEC = RecordCodecBuilder.<WeaponHudLayout>create(instance -> instance.group(
            SLOTS.optionalFieldOf("left", IntLists.emptyList()).forGetter(WeaponHudLayout::left),
            SLOTS.optionalFieldOf("right", IntLists.emptyList()).forGetter(WeaponHudLayout::right))
            .apply(instance, WeaponHudLayout::new)).validate(layout -> {
                int mask = 0;
                for (WeaponHudSide side : WeaponHudSide.values()) {
                    for (var iterator = layout.slots(side).iterator(); iterator.hasNext();) {
                        int slot = iterator.nextInt();
                        if ((mask & 1 << slot) != 0) return DataResult.error(() -> "A weapon slot may only occur once in the HUD layout");
                        mask |= 1 << slot;
                    }
                }
                return DataResult.success(layout);
            });

    public WeaponHudLayout {
        left = IntLists.unmodifiable(new IntArrayList(left));
        right = IntLists.unmodifiable(new IntArrayList(right));
    }

    public IntList slots(WeaponHudSide side) {
        return side == WeaponHudSide.LEFT ? left : right;
    }

    /** Assigns new occupied slots once, balancing visible weapons without moving remembered slots. */
    public WeaponHudLayout assign(int occupiedMask) {
        int assigned = 0, leftCount = 0, rightCount = 0;
        for (var iterator = left.iterator(); iterator.hasNext();) {
            int slot = iterator.nextInt();
            assigned |= 1 << slot;
            if ((occupiedMask & 1 << slot) != 0) leftCount++;
        }
        for (var iterator = right.iterator(); iterator.hasNext();) {
            int slot = iterator.nextInt();
            assigned |= 1 << slot;
            if ((occupiedMask & 1 << slot) != 0) rightCount++;
        }
        if ((occupiedMask & ~assigned) == 0) return this;
        IntList nextLeft = new IntArrayList(left), nextRight = new IntArrayList(right);
        for (int slot = 0; slot < 8; slot++) {
            if ((occupiedMask & ~assigned & 1 << slot) == 0) continue;
            if (leftCount <= rightCount) {
                nextLeft.add(slot);
                leftCount++;
            } else {
                nextRight.add(slot);
                rightCount++;
            }
        }
        return new WeaponHudLayout(nextLeft, nextRight);
    }

    public IntList visible(WeaponHudSide side, int occupiedMask) {
        IntList visible = new IntArrayList();
        for (var iterator = slots(side).iterator(); iterator.hasNext();) {
            int slot = iterator.nextInt();
            if ((occupiedMask & 1 << slot) != 0) visible.add(slot);
        }
        return visible;
    }

    /** The insertion index counts visible weapons after excluding the dragged slot. */
    public WeaponHudLayout move(int slot, WeaponHudSide side, int insertionIndex, int occupiedMask) {
        IntList nextLeft = new IntArrayList(left), nextRight = new IntArrayList(right);
        nextLeft.rem(slot);
        nextRight.rem(slot);
        IntList target = side == WeaponHudSide.LEFT ? nextLeft : nextRight;
        int index = 0, visible = 0;
        while (index < target.size() && visible < insertionIndex) {
            if ((occupiedMask & 1 << target.getInt(index)) != 0) visible++;
            index++;
        }
        if (visible != insertionIndex) throw new IllegalArgumentException("Weapon insertion index exceeds the visible column");
        target.add(index, slot);
        return new WeaponHudLayout(nextLeft, nextRight);
    }
}
