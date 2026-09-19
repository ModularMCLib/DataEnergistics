package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

import java.math.BigInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Routes ordered Trinity crafting output batches without allowing CPU-reserved items to leak into general storage.
 */
public final class TrinityPatternOutputRouter {

    private static final BigInteger PHYSICAL_CHUNK = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * Describes the externally visible effects committed during one routing pass.
     *
     * @param progressed     whether any pending output was durably consumed
     * @param storageChanged whether main storage accepted at least one item
     */
    public record RouteResult(boolean progressed, boolean storageChanged) {}

    /**
     * Supplies the amount that crafting CPUs are currently waiting for.
     */
    @FunctionalInterface
    public interface RequestedAmount {

        /**
         * @param key crafted item key
         * @return current requested amount across the lease grid's crafting CPUs
         */
        long get(AEItemKey key);
    }

    /**
     * Inserts an item amount into either crafting CPUs or the host's main storage.
     */
    @FunctionalInterface
    public interface OutputSink {

        /**
         * @param key    crafted item key
         * @param amount positive amount offered
         * @param mode   side-effect-free simulation or mutating insertion
         * @return accepted amount in the inclusive range from zero to {@code amount}
         */
        long insert(AEItemKey key, long amount, Actionable mode);
    }

    /** Mutable cursor over one route's authoritative ordered pending outputs. */
    public interface PendingOutputCursor extends AutoCloseable {

        /**
         * Advances past the previously retained entry and selects the next pending entry.
         *
         * @return whether {@link #current()} now exposes an entry
         */
        boolean advance();

        /**
         * @return selected immutable entry; valid only after {@link #advance()} returned true
         */
        TrinityItemAmount current();

        /**
         * Atomically consumes an externally inserted amount from the selected entry and checkpoints the new state.
         * This method must durably mark its owner changed before it returns.
         *
         * @param amount positive amount no greater than the selected entry amount
         */
        default void consumeCurrent(long amount) {
            consumeCurrent(BigInteger.valueOf(amount));
        }

        /** Consumes an exact amount without projecting it through an AE2 long counter. */
        void consumeCurrent(BigInteger amount);

        /** Releases the route's exclusive cursor state. */
        @Override
        void close();
    }

    /**
     * Routes each pending entry to waiting CPUs first and only offers its non-requested portion to main storage.
     * Pending order is significant: when an entry retains a CPU-requested amount, the router ends the pass without
     * advancing the cursor. The current entry's non-requested portion may still enter main storage before that barrier.
     * A remainder caused only by main-storage capacity does not block later entries. Each pass offers at most one
     * long-sized chunk from each existing entry; any exact tail remains in its authoritative entry.
     *
     * @param pending         exclusive cursor over authoritative route-owned outputs
     * @param requestedAmount lease-grid CPU request lookup
     * @param cpuSink         lease-grid crafting CPU insertion
     * @param storageSink     Trinity main storage insertion
     * @return committed routing effects, including whether main storage actually changed
     */
    public RouteResult route(PendingOutputCursor pending,
                             RequestedAmount requestedAmount,
                             OutputSink cpuSink,
                             OutputSink storageSink) {
        boolean progressed = false;
        boolean storageChanged = false;
        while (pending.advance()) {
            TrinityItemAmount output = pending.current();
            AEItemKey key = output.key();
            long amount = output.exactAmount().min(PHYSICAL_CHUNK).longValueExact();
            long requestedBefore = checkedRequestedAmount(requestedAmount.get(key));
            long cpuOffer = Math.min(amount, requestedBefore);
            long insertedIntoCpu = insertTwoPhase(cpuSink, key, cpuOffer, "crafting CPU");
            long requestedRemainder = cpuOffer - insertedIntoCpu;
            if (insertedIntoCpu > 0L) {
                pending.consumeCurrent(insertedIntoCpu);
                progressed = true;
            }

            long storageOffer = amount - cpuOffer;
            long insertedIntoStorage = insertTwoPhase(storageSink, key, storageOffer, "main storage");
            if (insertedIntoStorage > 0L) {
                pending.consumeCurrent(insertedIntoStorage);
                progressed = true;
                storageChanged = true;
            }
            if (requestedRemainder > 0L) {
                return new RouteResult(progressed, storageChanged);
            }
            if (requestedBefore == Long.MAX_VALUE && output.exactAmount().compareTo(PHYSICAL_CHUNK) > 0) {
                // A saturated AE request cannot describe the exact reserved tail. Re-query it on the next pass before
                // advancing to another entry or deciding that any part of that tail belongs to main storage.
                return new RouteResult(progressed, storageChanged);
            }
        }
        return new RouteResult(progressed, storageChanged);
    }

    /**
     * Routes output to a Trinity CPU through its exact public API before using long-sized storage chunks.
     * The CPU sink may accept the complete BigInteger amount in one operation; only the fallback storage sink is
     * constrained by AE2's physical long transfer boundary.
     */
    public RouteResult routeExact(PendingOutputCursor pending,
                                  Function<AEItemKey, BigInteger> requestedAmount,
                                  BiFunction<AEItemKey, BigInteger, BigInteger> cpuSink,
                                  OutputSink storageSink) {
        boolean progressed = false;
        boolean storageChanged = false;
        while (pending.advance()) {
            TrinityItemAmount output = pending.current();
            AEItemKey key = output.key();
            BigInteger amount = output.exactAmount();
            BigInteger requested = requestedAmount.apply(key);
            if (requested.signum() < 0) {
                throw new IllegalStateException("Exact crafting CPU request amount must not be negative");
            }
            BigInteger cpuOffer = amount.min(requested);
            BigInteger inserted = cpuOffer.signum() == 0 ? BigInteger.ZERO : cpuSink.apply(key, cpuOffer);
            if (inserted.signum() < 0 || inserted.compareTo(cpuOffer) > 0) {
                throw new IllegalStateException("Exact crafting CPU returned invalid insertion " + inserted);
            }
            if (inserted.signum() > 0) {
                pending.consumeCurrent(inserted);
                progressed = true;
            }
            BigInteger remainder = amount.subtract(inserted);
            if (remainder.signum() > 0 && requested.compareTo(amount) < 0) {
                long storageOffer = remainder.min(PHYSICAL_CHUNK).longValueExact();
                long stored = insertTwoPhase(storageSink, key, storageOffer, "main storage");
                if (stored > 0L) {
                    pending.consumeCurrent(BigInteger.valueOf(stored));
                    progressed = true;
                    storageChanged = true;
                }
                if (stored < storageOffer) {
                    return new RouteResult(progressed, storageChanged);
                }
            }
            if (inserted.compareTo(cpuOffer) < 0 || cpuOffer.compareTo(amount) < 0 && inserted.signum() == 0) {
                return new RouteResult(progressed, storageChanged);
            }
        }
        return new RouteResult(progressed, storageChanged);
    }

    private static long checkedRequestedAmount(long requested) {
        if (requested < 0L) {
            throw new IllegalStateException("Crafting CPU request amount must not be negative: " + requested);
        }
        return requested;
    }

    private static long insertTwoPhase(OutputSink sink, AEItemKey key, long amount, String destination) {
        if (amount <= 0L) {
            return 0L;
        }
        long simulated = checkedInsertion(sink.insert(key, amount, Actionable.SIMULATE), amount, destination, "simulate");
        if (simulated <= 0L) {
            return 0L;
        }
        long inserted = checkedInsertion(
                sink.insert(key, simulated, Actionable.MODULATE), simulated, destination, "modulate");
        if (inserted != simulated) {
            Data_Energistics.LOGGER.warn(
                    "Trinity output {} accepted {} during simulation but {} during modulation for {}",
                    destination,
                    simulated,
                    inserted,
                    key);
        }
        return inserted;
    }

    private static long checkedInsertion(long inserted, long offered, String destination, String phase) {
        if (inserted < 0L || inserted > offered) {
            throw new IllegalStateException(
                    "Trinity output " + destination + " returned invalid " + phase + " insertion " + inserted +
                            " for offer " + offered);
        }
        return inserted;
    }
}
