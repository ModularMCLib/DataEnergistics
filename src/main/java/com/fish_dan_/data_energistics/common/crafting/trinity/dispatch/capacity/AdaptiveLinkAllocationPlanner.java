package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Allocates one counted adaptive request across the configured connector links.
 *
 * <p>
 * Round-robin mode uses a bounded max-min distribution in configured rotation order, so every link with
 * available capacity participates in the same dispatch. Priority mode consumes the links in registration order and
 * moves to the next link only after the current link is full.
 * </p>
 */
public final class AdaptiveLinkAllocationPlanner {

    private AdaptiveLinkAllocationPlanner() {}

    /**
     * Plans one immutable link allocation without reading or changing provider state.
     *
     * @param capacities     per-link non-negative logical capacities
     * @param requestedCount logical crafts still available to this dispatch
     * @param cursor         persistent round-robin cursor; priority mode always starts at the first link
     * @param policy         connector routing policy
     * @return per-link allocations and the cursor successor after a successful commit
     */
    public static Allocation plan(long[] capacities, long requestedCount, int cursor, ConnectorPolicy policy) {
        if (requestedCount < 0L) {
            throw new IllegalArgumentException("Adaptive link allocation request must not be negative");
        }
        if (cursor < 0) {
            throw new IllegalArgumentException("Adaptive link allocation cursor must not be negative");
        }
        long[] normalized = capacities.clone();
        for (long capacity : normalized) {
            if (capacity < 0L) {
                throw new IllegalArgumentException("Adaptive link capacity must not be negative");
            }
        }
        if (normalized.length == 0 || requestedCount == 0L) {
            return new Allocation(new long[normalized.length], normalized.length == 0 ? 0 : Math.floorMod(cursor, normalized.length));
        }
        return policy == ConnectorPolicy.PRIORITY ? planPriority(normalized, requestedCount) :
                planRoundRobin(normalized, requestedCount, Math.floorMod(cursor, normalized.length));
    }

    private static Allocation planPriority(long[] capacities, long requestedCount) {
        long[] allocation = new long[capacities.length];
        long remaining = requestedCount;
        int last = -1;
        for (int index = 0; index < capacities.length && remaining > 0L; index++) {
            long count = Math.min(capacities[index], remaining);
            allocation[index] = count;
            if (count > 0L) {
                last = index;
                remaining -= count;
            }
        }
        return new Allocation(allocation, last < 0 ? 0 : (last + 1) % capacities.length);
    }

    private static Allocation planRoundRobin(long[] capacities, long requestedCount, int start) {
        int[] eligible = Arrays.stream(indexOrder(capacities.length, start))
                .filter(index -> capacities[index] > 0L)
                .toArray();
        long[] allocation = new long[capacities.length];
        if (eligible.length == 0) {
            return new Allocation(allocation, start);
        }

        long initial = Math.min(requestedCount, eligible.length);
        for (int offset = 0; offset < initial; offset++) {
            allocation[eligible[offset]] = 1L;
        }
        BigInteger remaining = BigInteger.valueOf(requestedCount - initial);
        if (remaining.signum() > 0) {
            long[] residual = new long[eligible.length];
            for (int offset = 0; offset < eligible.length; offset++) {
                residual[offset] = capacities[eligible[offset]] - 1L;
            }
            distributeResidual(residual, allocation, eligible, remaining);
        }

        int last = eligible[(int) initial - 1];
        for (int offset = eligible.length - 1; offset >= 0; offset--) {
            if (allocation[eligible[offset]] > 0L) {
                last = eligible[offset];
                break;
            }
        }
        return new Allocation(allocation, (last + 1) % capacities.length);
    }

    private static void distributeResidual(long[] residual, long[] allocation, int[] eligible, BigInteger remaining) {
        long[] sorted = residual.clone();
        Arrays.sort(sorted);
        long level = 0L;
        int active = eligible.length;
        int sortedIndex = 0;
        long remainder = 0L;
        while (sortedIndex < sorted.length && remaining.signum() > 0) {
            long nextLevel = sorted[sortedIndex];
            long levelIncrease = nextLevel - level;
            BigInteger required = BigInteger.valueOf(levelIncrease).multiply(BigInteger.valueOf(active));
            if (remaining.compareTo(required) < 0) {
                BigInteger[] division = remaining.divideAndRemainder(BigInteger.valueOf(active));
                level = Math.addExact(level, division[0].longValueExact());
                remainder = division[1].longValueExact();
                break;
            }
            level = nextLevel;
            remaining = remaining.subtract(required);
            while (sortedIndex < sorted.length && sorted[sortedIndex] == level) {
                sortedIndex++;
                active--;
            }
        }

        for (int offset = 0; offset < eligible.length; offset++) {
            long count = Math.min(residual[offset], level);
            if (remainder > 0L && residual[offset] > level) {
                count = Math.incrementExact(count);
                remainder--;
            }
            allocation[eligible[offset]] = Math.addExact(allocation[eligible[offset]], count);
        }
    }

    private static int[] indexOrder(int size, int start) {
        int[] order = new int[size];
        for (int offset = 0; offset < size; offset++) {
            order[offset] = (start + offset) % size;
        }
        return order;
    }

    /** Immutable result of one adaptive link allocation. */
    public record Allocation(long[] counts, int nextCursor) {

        public Allocation {
            if (nextCursor < 0) {
                throw new IllegalArgumentException("Adaptive link allocation cursor must not be negative");
            }
            counts = counts.clone();
        }

        @Override
        public long[] counts() {
            return this.counts.clone();
        }
    }
}
