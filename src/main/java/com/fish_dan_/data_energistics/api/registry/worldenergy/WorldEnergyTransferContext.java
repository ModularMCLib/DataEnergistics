package com.fish_dan_.data_energistics.api.registry.worldenergy;

import appeng.api.stacks.AEKey;

/**
 * Two-phase quantity transfer surface for one Digital Supply Interface tick.
 *
 * <p>
 * Network and world-side simulation happen before either side is committed. If the second commit accepts less
 * than the planned amount, the context compensates the first side and reports the actual transferred quantity.
 * </p>
 */
public interface WorldEnergyTransferContext {

    /** Simulates extraction from ordinary AE network storage. */
    long simulateNetworkExtract(AEKey key, long amount);

    /** Commits extraction from ordinary AE network storage. */
    long commitNetworkExtract(AEKey key, long amount);

    /** Simulates insertion into ordinary AE network storage. */
    long simulateNetworkInsert(AEKey key, long amount);

    /** Commits insertion into ordinary AE network storage. */
    long commitNetworkInsert(AEKey key, long amount);

    /**
     * Runs a network-to-world transaction using amounts expressed in AE units.
     *
     * @param key       real network key, never a presence-marker inventory entry
     * @param requested requested AE amount
     * @param world     target-side simulation and commit callback
     */
    default TransferResult networkToWorld(AEKey key, long requested, NativeTransfer world) {
        requireAmount(requested);
        if (requested == 0) {
            return TransferResult.empty();
        }
        long networkAvailable = simulateNetworkExtract(key, requested);
        long worldAcceptable = world.transfer(networkAvailable, true);
        long planned = Math.min(networkAvailable, worldAcceptable);
        if (planned <= 0) {
            return TransferResult.empty();
        }
        long extracted = commitNetworkExtract(key, planned);
        if (extracted <= 0) {
            return TransferResult.empty();
        }
        long accepted = world.transfer(extracted, false);
        if (accepted >= extracted) {
            return new TransferResult(requested, accepted, 0);
        }

        long unaccepted = extracted - Math.max(0, accepted);
        long restored = commitNetworkInsert(key, unaccepted);
        return new TransferResult(requested, Math.max(0, accepted), Math.max(0, unaccepted - restored));
    }

    /** Runs a world-to-network transaction with the same rollback guarantees. */
    default TransferResult worldToNetwork(AEKey key, long requested, NativeTransfer world) {
        requireAmount(requested);
        if (requested == 0) {
            return TransferResult.empty();
        }
        long worldAvailable = world.transfer(requested, true);
        long networkAcceptable = simulateNetworkInsert(key, worldAvailable);
        long planned = Math.min(worldAvailable, networkAcceptable);
        if (planned <= 0) {
            return TransferResult.empty();
        }
        long extracted = world.transfer(planned, false);
        if (extracted <= 0) {
            return TransferResult.empty();
        }
        long inserted = commitNetworkInsert(key, extracted);
        if (inserted >= extracted) {
            return new TransferResult(requested, inserted, 0);
        }

        long uninserted = extracted - Math.max(0, inserted);
        long restored = world.transfer(uninserted, false);
        return new TransferResult(requested, Math.max(0, inserted), Math.max(0, uninserted - restored));
    }

    private static void requireAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Transfer amount must be non-negative");
        }
    }

    /** Callback receiving an amount and a simulation flag; return the amount accepted or extracted. */
    @FunctionalInterface
    interface NativeTransfer {

        long transfer(long amount, boolean simulate);
    }

    /** Actual transfer and any unrecovered quantity after a late commit mismatch. */
    record TransferResult(long requested, long transferred, long unrecovered) {

        public TransferResult {
            if (requested < 0 || transferred < 0 || unrecovered < 0 || transferred > requested) {
                throw new IllegalArgumentException("Invalid world-energy transfer result");
            }
        }

        static TransferResult empty() {
            return new TransferResult(0, 0, 0);
        }

        public boolean completed() {
            return unrecovered == 0;
        }
    }
}
