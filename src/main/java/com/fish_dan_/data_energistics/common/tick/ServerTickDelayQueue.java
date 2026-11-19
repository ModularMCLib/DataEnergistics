package com.fish_dan_.data_energistics.common.tick;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayDeque;

@SuppressWarnings("resource")
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class ServerTickDelayQueue {

    private ServerTickDelayQueue() {}

    private static final ArrayDeque<Entry> QUEUE = new ArrayDeque<>();

    public static void runNextServerTick(MinecraftServer server, Runnable task) {
        if (server == null || task == null) {
            return;
        }

        synchronized (QUEUE) {
            QUEUE.add(new Entry(server, task));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerTick(ServerTickEvent.Post event) {
        ObjectArrayList<Entry> ready;
        synchronized (QUEUE) {
            int pendingCount = QUEUE.size();
            if (pendingCount == 0) {
                return;
            }
            ready = new ObjectArrayList<>(pendingCount);
            for (int i = 0; i < pendingCount; i++) {
                Entry entry = QUEUE.removeFirst();
                if (entry.server() == event.getServer()) {
                    ready.add(entry);
                } else {
                    QUEUE.add(entry);
                }
            }
        }

        for (Entry entry : ready) {
            entry.task().run();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (QUEUE) {
            QUEUE.removeIf(entry -> entry.server() == event.getServer());
        }
    }

    private record Entry(MinecraftServer server, Runnable task) {}
}
