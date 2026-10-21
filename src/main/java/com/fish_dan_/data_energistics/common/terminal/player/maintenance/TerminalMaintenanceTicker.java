package com.fish_dan_.data_energistics.common.terminal.player.maintenance;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Polls AE2 plans on the server thread and keeps per-entry requests below their configured ceiling.
 * Machine sources keep background requests in automation CPU selection and out of player crafting pins.
 */
@NullMarked
public final class TerminalMaintenanceTicker {

    private static final int CHECK_INTERVAL = 10;
    private static final int RETRY_DELAY = 40;
    private final Object2ObjectOpenHashMap<UUID, Pending> calculations = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, @Nullable Refill> refills = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, Status> statuses = new Object2ObjectOpenHashMap<>();
    private final Object2IntOpenHashMap<UUID> retryAt = new Object2IntOpenHashMap<>();

    public void tick(ServerPlayer player, TerminalMaintenanceState state, @Nullable TerminalConnection connection) {
        ObjectList<TerminalMaintenanceEntry> entries = state.entries();
        refills.keySet().removeIf(id -> {
            TerminalMaintenanceEntry entry = find(entries, id);
            return entry == null || !entry.enabled() || entry.resource() == null || entry.upper() == 0 || entry.lower() > entry.upper();
        });
        if (connection == null || !player.isAlive()) {
            for (Pending pending : calculations.values()) pending.calculation.cancel(true);
            calculations.clear();
            for (TerminalMaintenanceEntry entry : entries)
                statuses.put(entry.id(), entry.enabled() && entry.resource() != null ? Status.LACKING : Status.FREE);
            return;
        }
        ObjectList<Pending> completed = new ObjectArrayList<>();
        Iterator<Pending> iterator = calculations.values().iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            TerminalMaintenanceEntry current = find(entries, pending.entry.id());
            if (current == null || !current.equals(pending.entry) ||
                    connection.grid() != pending.grid || connection.frequency() != pending.frequency || !player.isAlive()) {
                pending.calculation.cancel(true);
                iterator.remove();
                statuses.put(pending.entry.id(), Status.FREE);
            } else if (pending.calculation.isDone()) {
                iterator.remove();
                completed.add(pending);
            }
        }

        for (Pending pending : completed) finish(player, state, connection, pending);
        if (player.tickCount % CHECK_INTERVAL != 0) return;

        var crafting = connection.grid().getCraftingService();
        var cpus = crafting.getCpus();
        for (TerminalMaintenanceEntry entry : entries) {
            UUID id = entry.id();
            AEKey key = entry.resource();
            if (!entry.enabled() || key == null) {
                statuses.put(id, Status.FREE);
                continue;
            }
            if (entry.upper() == 0) {
                statuses.put(id, Status.IDLE);
                continue;
            }
            if (entry.lower() > entry.upper()) {
                statuses.put(id, Status.LACKING);
                continue;
            }
            long stored = connection.storage().getAvailableStacks().get(key);
            boolean refill = refilling(entry, connection, key, stored);
            if (calculating(key)) {
                statuses.put(id, Status.IDLE);
                continue;
            }

            long inProgress = Math.max(crafting.getRequestedAmount(key), craftingAmount(cpus, key));
            long total = add(stored, inProgress);
            if (inProgress > 0) {
                statuses.put(id, Status.SYNTHESIZING);
                continue;
            }
            if (!refill) {
                statuses.put(id, Status.IDLE);
                continue;
            }
            if (!crafting.isCraftable(key)) {
                statuses.put(id, Status.LACKING);
                retryAt.put(id, player.tickCount + RETRY_DELAY);
                continue;
            }
            if (retryAt.getInt(id) > player.tickCount) continue;

            long amount = Math.min(entry.batch(), entry.upper() - total);
            if (amount <= 0) {
                statuses.put(id, Status.IDLE);
                continue;
            }
            try {
                Future<ICraftingPlan> calculation = crafting.beginCraftingCalculation(player.level(),
                        () -> IActionSource.ofMachine(connection.host()), key, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
                calculations.put(id, new Pending(entry, connection.grid(), connection.frequency(), amount, calculation));
                statuses.put(id, Status.IDLE);
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error("Cannot plan terminal stock maintenance for player {}, resource {}",
                        player.getUUID(), key, failure);
                statuses.put(id, Status.LACKING);
                retryAt.put(id, player.tickCount + RETRY_DELAY);
            }
        }
    }

    public CompoundTag snapshot(TerminalMaintenanceState state, HolderLookup.Provider registries) {
        CompoundTag tag = state.save(registries);
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        ObjectList<TerminalMaintenanceEntry> source = state.entries();
        for (int index = 0; index < entries.size(); index++) {
            TerminalMaintenanceEntry entry = source.get(index);
            Status status = entry.enabled() && entry.resource() != null ? statuses.getOrDefault(entry.id(), Status.IDLE) : Status.FREE;
            entries.getCompound(index).putString("status", status.id);
        }
        return tag;
    }

    public void close() {
        for (Pending pending : calculations.values()) pending.calculation.cancel(true);
        calculations.clear();
        refills.clear();
        statuses.clear();
        retryAt.clear();
    }

    private void finish(ServerPlayer player, TerminalMaintenanceState state, TerminalConnection connection, Pending pending) {
        TerminalMaintenanceEntry current = find(state.entries(), pending.entry.id());
        if (current == null || !current.equals(pending.entry) || connection.grid() != pending.grid ||
                connection.frequency() != pending.frequency || !player.isAlive())
            return;
        var crafting = connection.grid().getCraftingService();
        AEKey key = current.resource();
        if (key == null) return;
        long stored = connection.storage().getAvailableStacks().get(key);
        long inProgress = Math.max(crafting.getRequestedAmount(key), craftingAmount(crafting.getCpus(), key));
        long capacity = Math.max(0, current.upper() - add(stored, inProgress));
        if (!refilling(current, connection, key, stored) || pending.amount > capacity) {
            statuses.put(current.id(), Status.IDLE);
            return;
        }
        if (inProgress > 0) {
            statuses.put(current.id(), Status.SYNTHESIZING);
            return;
        }
        try {
            ICraftingPlan plan = pending.calculation.get();
            if (plan.simulation() || !plan.missingItems().isEmpty()) {
                statuses.put(current.id(), Status.LACKING);
                retryAt.put(current.id(), player.tickCount + RETRY_DELAY);
                return;
            }
            var result = crafting.submitJob(plan, null, null, true, IActionSource.ofMachine(connection.host()));
            var error = result.errorCode();
            Status status = switch (error) {
                case null -> Status.SYNTHESIZING;
                case NO_CPU_FOUND, NO_SUITABLE_CPU_FOUND, CPU_BUSY, CPU_OFFLINE, CPU_TOO_SMALL -> Status.NO_CPU;
                case INCOMPLETE_PLAN, MISSING_INGREDIENT -> Status.LACKING;
            };
            statuses.put(current.id(), status);
            if (error == null) {
                retryAt.removeInt(current.id());
            } else {
                retryAt.put(current.id(), player.tickCount + RETRY_DELAY);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            Data_Energistics.LOGGER.warn("Terminal stock maintenance planning interrupted for player {}, resource {}",
                    player.getUUID(), key, failure);
            statuses.put(current.id(), Status.LACKING);
            retryAt.put(current.id(), player.tickCount + RETRY_DELAY);
        } catch (ExecutionException | RuntimeException failure) {
            Data_Energistics.LOGGER.error("Terminal stock maintenance failed for player {}, resource {}",
                    player.getUUID(), key, failure);
            statuses.put(current.id(), Status.LACKING);
            retryAt.put(current.id(), player.tickCount + RETRY_DELAY);
        }
    }

    private boolean refilling(TerminalMaintenanceEntry entry, TerminalConnection connection, AEKey key, long stored) {
        UUID id = entry.id();
        if (stored >= entry.upper()) {
            refills.remove(id);
            return false;
        }
        Refill refill = refills.get(id);
        if (refill != null && key.equals(refill.resource) && connection.grid() == refill.grid && connection.frequency() == refill.frequency)
            return true;
        refills.remove(id);
        if (stored > entry.lower()) return false;
        // Crossing the lower threshold starts a cycle; only actual network stock ends it at the upper threshold.
        refills.put(id, new Refill(key, connection.grid(), connection.frequency()));
        return true;
    }

    private boolean calculating(AEKey key) {
        for (Pending pending : calculations.values())
            if (key.equals(pending.entry.resource())) return true;
        return false;
    }

    private static long craftingAmount(Iterable<? extends ICraftingCPU> cpus, AEKey key) {
        long amount = 0;
        for (var cpu : cpus) {
            var status = cpu.getJobStatus();
            if (status != null && key.equals(status.crafting().what()))
                amount = add(amount, status.crafting().amount());
        }
        return amount;
    }

    private static long add(long first, long second) {
        return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second;
    }

    private static @Nullable TerminalMaintenanceEntry find(
                                                           ObjectList<TerminalMaintenanceEntry> entries, UUID id) {
        for (TerminalMaintenanceEntry entry : entries)
            if (entry.id().equals(id)) return entry;
        return null;
    }

    private enum Status {

        FREE("free"),
        IDLE("idle"),
        LACKING("lacking"),
        NO_CPU("no_cpu"),
        SYNTHESIZING("synthesizing"),
        EXPORTING("exporting");

        private final String id;

        Status(String id) {
            this.id = id;
        }
    }

    private record Pending(TerminalMaintenanceEntry entry, IGrid grid, long frequency, long amount,
                           Future<ICraftingPlan> calculation) {}

    private record Refill(AEKey resource, IGrid grid, long frequency) {}
}
