package com.fish_dan_.data_energistics.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Single-owner task admission with bounded, streamed results from virtual threads. The owner alone submits,
 * polls and cancels; workers only use their Work context. Emitted values transfer ownership to the queue,
 * then to the polling caller. Invalidated values are released on the emitting or owner thread, so release
 * must be thread-safe and must not require a GPU context. No Minecraft world or renderer is retained here.
 */
public final class BoundedVirtualTaskQueue<T> implements AutoCloseable {

    private final int parallelism;
    private final Consumer<T> release;
    private final ExecutorService executor;
    private final Semaphore workers;
    private final Semaphore resultSlots;
    private final Object handoff = new Object();
    private final ConcurrentLinkedQueue<Envelope<T>> results = new ConcurrentLinkedQueue<>();
    private final Long2ObjectOpenHashMap<Ticket> tasks = new Long2ObjectOpenHashMap<>();
    private long generation;
    private boolean closed;

    /** Creates a queue with positive concurrency/capacity limits; thread names identify the owning subsystem. */
    public BoundedVirtualTaskQueue(String threadPrefix, int parallelism, int resultCapacity, Consumer<T> release) {
        if (parallelism < 1 || resultCapacity < 1) throw new IllegalArgumentException("Task queue limits must be positive");
        this.parallelism = parallelism;
        this.release = release;
        workers = new Semaphore(parallelism);
        resultSlots = new Semaphore(resultCapacity);
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(threadPrefix, 0).factory());
    }

    /** Owner-thread admission check; completed but unconsumed tasks retain their slot to bound memory. */
    public boolean hasCapacity() {
        return !closed && tasks.size() < parallelism && workers.availablePermits() > 0;
    }

    /** Reports whether the owner still has a current task for this primitive key. */
    public boolean contains(long key) {
        return tasks.containsKey(key);
    }

    /** Starts one current task. The caller must have capacity and must cancel an older task with the same key. */
    public void submit(long key, Consumer<Work<T>> calculation) {
        if (!hasCapacity() || tasks.containsKey(key) || !workers.tryAcquire()) throw new IllegalStateException("No task slot or duplicate task key: " + key);
        var ticket = new Ticket(key, generation);
        var work = new Work<>(this, ticket);
        FutureTask<Boolean> future = new FutureTask<>(() -> {
            try {
                work.checkCancelled();
                calculation.accept(work);
                work.publish(null, null, true);
            } catch (RuntimeException | Error failure) {
                if (ticket.cancelled.get() || Thread.currentThread().isInterrupted()) ticket.cancelled.set(true);
                else {
                    try {
                        work.publish(null, failure, true);
                    } catch (CancellationException stopped) {
                        ticket.cancelled.set(true);
                    }
                }
            }
            return Boolean.TRUE;
        });
        ticket.future = future;
        tasks.put(key, ticket);
        try {
            executor.execute(() -> {
                if (!ticket.started.compareAndSet(false, true)) return;
                try {
                    future.run();
                } finally {
                    workers.release();
                }
            });
        } catch (RuntimeException | Error failure) {
            tasks.remove(key);
            if (ticket.started.compareAndSet(false, true)) workers.release();
            throw failure;
        }
    }

    /**
     * Polls without waiting. Nonterminal values belong to the caller, who must release them after use.
     * Terminal results contain no value and may contain a worker failure; cancelled generations are discarded.
     */
    public @Nullable Result<T> poll() {
        Envelope<T> envelope;
        while ((envelope = results.poll()) != null) {
            resultSlots.release();
            Ticket ticket = envelope.ticket();
            Result<T> result = envelope.result();
            if (ticket.generation != generation || !tasks.containsKey(ticket.key) || tasks.get(ticket.key) != ticket) {
                if (result.value() != null) release.accept(result.value());
                continue;
            }
            if (result.terminal()) tasks.remove(ticket.key);
            return result;
        }
        var iterator = tasks.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Ticket ticket = entry.getValue();
            if (ticket.cancelled.get() && ticket.future.isDone()) {
                long key = entry.getLongKey();
                iterator.remove();
                return new Result<>(key, null, new CancellationException("Worker stopped without publishing a result"), true);
            }
        }
        return null;
    }

    /** Invalidates a key and interrupts its worker without waiting. Unpolled values are released immediately. */
    public void cancel(long key) {
        if (!tasks.containsKey(key)) return;
        Ticket ticket = tasks.remove(key);
        synchronized (handoff) {
            ticket.cancelled.set(true);
            ticket.future.cancel(true);
            if (ticket.started.compareAndSet(false, true)) workers.release();
            results.removeIf(envelope -> {
                if (envelope.ticket() != ticket) return false;
                if (envelope.result().value() != null) release.accept(envelope.result().value());
                resultSlots.release();
                return true;
            });
        }
    }

    /** Advances the lifecycle generation, cancels every task and releases queued values without joining workers. */
    public void clear() {
        generation++;
        for (long key : tasks.keySet().toLongArray()) cancel(key);
    }

    /** Permanently closes admission and interrupts workers; never blocks the owner waiting for termination. */
    @Override
    public void close() {
        clear();
        closed = true;
        executor.shutdownNow();
    }

    /** A worker's immutable task key and its privately owned cancellation/handoff context. */
    public static final class Work<T> {

        private final BoundedVirtualTaskQueue<T> owner;
        private final Ticket ticket;
        @Getter
        private final long key;

        private Work(BoundedVirtualTaskQueue<T> owner, Ticket ticket) {
            this.owner = owner;
            this.ticket = ticket;
            key = ticket.key;
        }

        /** Cooperative cancellation predicate for bounded loops; has no side effects. */
        public boolean cancelled() {
            return ticket.cancelled.get() || Thread.currentThread().isInterrupted();
        }

        /** Throws CancellationException when a task revision was invalidated or its thread was interrupted. */
        public void checkCancelled() {
            if (cancelled()) throw new CancellationException();
        }

        /**
         * Transfers a nonnull value, waiting interruptibly for result capacity. On cancellation or rejection,
         * this method releases the supplied value before throwing; the worker must never release it again.
         */
        public void emit(T value) {
            try {
                publish(value, null, false);
            } catch (RuntimeException | Error failure) {
                owner.release.accept(value);
                throw failure;
            }
        }

        private void publish(@Nullable T value, @Nullable Throwable failure, boolean terminal) {
            checkCancelled();
            try {
                owner.resultSlots.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancellationException();
            }
            boolean transferred = false;
            try {
                synchronized (owner.handoff) {
                    checkCancelled();
                    owner.results.add(new Envelope<>(ticket, new Result<>(key, value, failure, terminal)));
                    transferred = true;
                }
            } finally {
                if (!transferred) owner.resultSlots.release();
            }
        }
    }

    /** Owner-thread delivery; value is present only for data, failure only for terminal failure. */
    public record Result<T>(long key, @Nullable T value, @Nullable Throwable failure, boolean terminal) {}

    private record Envelope<T>(Ticket ticket, Result<T> result) {}

    private static final class Ticket {

        private final long key;
        private final long generation;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();
        private FutureTask<Boolean> future = new FutureTask<>(() -> Boolean.FALSE);

        private Ticket(long key, long generation) {
            this.key = key;
            this.generation = generation;
        }
    }
}
