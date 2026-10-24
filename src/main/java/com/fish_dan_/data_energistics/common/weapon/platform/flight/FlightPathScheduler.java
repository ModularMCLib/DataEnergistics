package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Fair server-wide budget; waiting guns share 2048 expansions per tick in sixty-four-node slices. */
public final class FlightPathScheduler {
    private static final ObjectArrayFIFOQueue<FlightPathSearch> QUEUE = new ObjectArrayFIFOQueue<>();
    static void submit(FlightPathSearch search) { QUEUE.enqueue(search); }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        int budget = 2048;
        while (budget > 0 && !QUEUE.isEmpty()) {
            FlightPathSearch search = QUEUE.dequeue();
            if (!search.complete() && !search.advance(64)) QUEUE.enqueue(search);
            budget -= 64;
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { QUEUE.clear(); }
}
