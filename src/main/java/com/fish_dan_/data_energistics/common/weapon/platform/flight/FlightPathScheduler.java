package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;

/** Fair server-wide budget; waiting guns share 2048 expansions per tick in sixty-four-node slices. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class FlightPathScheduler {

    private FlightPathScheduler() {}

    private static final ObjectArrayFIFOQueue<FlightPathSearch> QUEUE = new ObjectArrayFIFOQueue<>();

    static void submit(FlightPathSearch search) {
        QUEUE.enqueue(search);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        int budget = 2048;
        while (budget > 0 && !QUEUE.isEmpty()) {
            FlightPathSearch search = QUEUE.dequeue();
            if (!search.complete() && !search.advance(64)) QUEUE.enqueue(search);
            budget -= 64;
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }
}
