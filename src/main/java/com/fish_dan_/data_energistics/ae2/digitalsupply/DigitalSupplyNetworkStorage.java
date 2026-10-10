package com.fish_dan_.data_energistics.ae2.digitalsupply;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.network.chat.Component;

/**
 * View of a grid inventory used by Digital Supply Interface transfers.
 *
 * <p>
 * The presence mount remains a read-only recognition view. This transfer view removes its marker contribution
 * from ordinary quantity reports, while insert and extract continue to use the real grid storage.
 * </p>
 */
public final class DigitalSupplyNetworkStorage implements MEStorage {

    private final MEStorage delegate;
    private final PresenceMarkerStorage markers;

    public DigitalSupplyNetworkStorage(MEStorage delegate, PresenceMarkerStorage markers) {
        this.delegate = delegate;
        this.markers = markers;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return this.delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return this.delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return this.delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        KeyCounter actual = new KeyCounter();
        this.delegate.getAvailableStacks(actual);
        for (AEKey marker : this.markers.keys()) {
            long reported = actual.get(marker);
            if (reported <= 0L) continue;
            long real = this.delegate.extract(marker, reported, Actionable.SIMULATE, IActionSource.empty());
            actual.remove(marker, reported);
            if (real > 0L) actual.add(marker, real);
        }
        out.addAll(actual);
    }

    @Override
    public Component getDescription() {
        return this.delegate.getDescription();
    }
}
