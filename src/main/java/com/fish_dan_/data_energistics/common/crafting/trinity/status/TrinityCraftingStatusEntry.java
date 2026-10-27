package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingStatusEntry;

import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Server-formatted quantities; inherited long values are only AE2's non-authoritative UI compatibility view. */
public final class TrinityCraftingStatusEntry extends CraftingStatusEntry {

    private final TrinityStatusAmount stored;
    private final TrinityStatusAmount active;
    private final TrinityStatusAmount pending;
    private final TrinityStatusAmount resident;
    private final TrinityStatusSortKey workOrder;
    private final TrinityStatusSortKey storedOrder;

    /** A null key is allowed only in an incremental update whose serial already belongs to the receiving screen. */
    public TrinityCraftingStatusEntry(long serial, @Nullable AEKey key, TrinityStatusAmount stored, TrinityStatusAmount active,
                                      TrinityStatusAmount pending, TrinityStatusAmount resident,
                                      TrinityStatusSortKey workOrder, TrinityStatusSortKey storedOrder) {
        super(serial, key, stored.amount(), active.amount(), pending.amount());
        if (serial < 0) {
            throw new IllegalArgumentException("A crafting status entry requires a non-negative serial");
        }
        this.stored = stored;
        this.active = active;
        this.pending = pending;
        this.resident = resident;
        this.workOrder = workOrder;
        this.storedOrder = storedOrder;
    }

    /** Formats with the resolved key even when a delta omits that key on the wire. */
    public static TrinityCraftingStatusEntry capture(long serial, AEKey key, boolean includeKey,
                                                     BigInteger stored, BigInteger active, BigInteger pending, BigInteger resident) {
        return new TrinityCraftingStatusEntry(serial, includeKey ? key : null,
                TrinityStatusAmount.of(key, stored), TrinityStatusAmount.of(key, active),
                TrinityStatusAmount.of(key, pending), TrinityStatusAmount.of(key, resident),
                TrinityStatusSortKey.of(active.add(pending)), TrinityStatusSortKey.of(stored.add(resident)));
    }

    public TrinityStatusSortKey workOrder() {
        return this.workOrder;
    }

    public TrinityStatusSortKey storedOrder() {
        return this.storedOrder;
    }

    public TrinityStatusAmount stored() {
        return this.stored;
    }

    public TrinityStatusAmount active() {
        return this.active;
    }

    public TrinityStatusAmount pending() {
        return this.pending;
    }

    public TrinityStatusAmount resident() {
        return this.resident;
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted() && this.resident.amount() == 0;
    }

    @Override
    public int compareTo(CraftingStatusEntry other) {
        TrinityStatusSortKey otherWork = other instanceof TrinityCraftingStatusEntry formatted ?
                formatted.workOrder : TrinityStatusSortKey.of(BigInteger.valueOf(other.getActiveAmount()).add(BigInteger.valueOf(other.getPendingAmount())));
        int byWork = otherWork.compareTo(this.workOrder);
        if (byWork != 0) {
            return byWork;
        }
        TrinityStatusSortKey otherStored = other instanceof TrinityCraftingStatusEntry formatted ?
                formatted.storedOrder : TrinityStatusSortKey.of(BigInteger.valueOf(other.getStoredAmount()));
        return otherStored.compareTo(this.storedOrder);
    }
}
