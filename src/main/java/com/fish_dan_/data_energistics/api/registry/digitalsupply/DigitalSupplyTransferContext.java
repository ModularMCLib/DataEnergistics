package com.fish_dan_.data_energistics.api.registry.digitalsupply;

import appeng.api.stacks.AEKey;

import java.util.function.LongUnaryOperator;

/**
 * Two-phase quantity transfer surface for one Digital Supply Interface tick.
 *
 * <p>
 * Network and native-side simulation happen before either side is committed. If the second commit accepts less
 * than the planned amount, the context compensates the first side and reports the actual transferred quantity.
 * </p>
 */
public interface DigitalSupplyTransferContext {

    /** Simulates extraction from ordinary AE network storage. */
    long simulateNetworkExtract(AEKey key, long amount);

    /** Commits extraction from ordinary AE network storage. */
    long commitNetworkExtract(AEKey key, long amount);

    /** Simulates insertion into ordinary AE network storage. */
    long simulateNetworkInsert(AEKey key, long amount);

    /** Commits insertion into ordinary AE network storage. */
    long commitNetworkInsert(AEKey key, long amount);

    /**
     * Runs a network-to-target transaction using amounts expressed in AE units.
     *
     * @param key       real network key, never a presence-marker inventory entry
     * @param requested requested AE amount
     * @param target    native-target simulation and commit callback
     */
    default TransferResult networkToTarget(AEKey key, long requested, NativeTransfer target) {
        requireAmount(requested);
        if (requested == 0) {
            return TransferResult.empty();
        }
        long networkAvailable = simulateNetworkExtract(key, requested);
        long targetAcceptable = requireNativeResult(networkAvailable, target.transfer(networkAvailable, true));
        long planned = Math.min(networkAvailable, targetAcceptable);
        if (planned <= 0) {
            return new TransferResult(requested, 0, 0);
        }
        long extracted = requireNativeResult(planned, commitNetworkExtract(key, planned));
        if (extracted <= 0) {
            return new TransferResult(requested, 0, 0);
        }
        long accepted = requireNativeResult(extracted, target.transfer(extracted, false));
        if (accepted >= extracted) {
            return new TransferResult(requested, accepted, 0);
        }

        long unaccepted = extracted - Math.max(0, accepted);
        long restored = commitNetworkInsert(key, unaccepted);
        return new TransferResult(requested, Math.max(0, accepted), Math.max(0, unaccepted - restored));
    }

    /** Runs a target-to-network transaction with the same rollback guarantees. */
    default TransferResult targetToNetwork(AEKey key, long requested, NativeTransfer target) {
        requireAmount(requested);
        if (requested == 0) {
            return TransferResult.empty();
        }
        long targetAvailable = requireNativeResult(requested, target.transfer(requested, true));
        long networkAcceptable = simulateNetworkInsert(key, targetAvailable);
        long planned = Math.min(targetAvailable, networkAcceptable);
        if (planned <= 0) {
            return new TransferResult(requested, 0, 0);
        }
        long extracted = requireNativeResult(planned, target.transfer(planned, false));
        if (extracted <= 0) {
            return new TransferResult(requested, 0, 0);
        }
        long inserted = requireNativeResult(extracted, commitNetworkInsert(key, extracted));
        if (inserted >= extracted) {
            return new TransferResult(requested, inserted, 0);
        }

        long uninserted = extracted - Math.max(0, inserted);
        long restored = requireNativeResult(uninserted, target.rollback(uninserted));
        return new TransferResult(requested, Math.max(0, inserted), Math.max(0, uninserted - restored));
    }

    private static void requireAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Transfer amount must be non-negative");
        }
    }

    private static long requireNativeResult(long offered, long result) {
        if (result < 0 || result > offered) {
            throw new IllegalStateException(
                    "Digital Supply target returned " + result + " for offered amount " + offered);
        }
        return result;
    }

    /** Callback receiving an amount and a simulation flag; return the amount accepted or extracted. */
    @FunctionalInterface
    interface NativeTransfer {

        long transfer(long amount, boolean simulate);

        /**
         * Restores an amount that was removed during a committed target-to-network transfer.
         * Implementations that cannot restore the native side return zero so the result exposes the unrecovered amount.
         */
        default long rollback(long amount) {
            return 0;
        }

        /** Creates a native transfer callback with an explicit rollback operation. */
        static NativeTransfer reversible(NativeTransfer transfer, LongUnaryOperator rollback) {
            return new NativeTransfer() {

                @Override
                public long transfer(long amount, boolean simulate) {
                    return transfer.transfer(amount, simulate);
                }

                @Override
                public long rollback(long amount) {
                    return rollback.applyAsLong(amount);
                }
            };
        }
    }

    /** Actual transfer and any unrecovered quantity after a late commit mismatch. */
    record TransferResult(long requested, long transferred, long unrecovered) {

        public TransferResult {
            if (requested < 0 || transferred < 0 || unrecovered < 0 || transferred > requested) {
                throw new IllegalArgumentException("Invalid digital-supply transfer result");
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
