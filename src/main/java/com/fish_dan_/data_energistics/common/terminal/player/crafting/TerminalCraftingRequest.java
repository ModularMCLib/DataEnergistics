package com.fish_dan_.data_energistics.common.terminal.player.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Owns a cancellable AE2 calculation for one overlay lifetime and submits only on its original live grid.
 */
public final class TerminalCraftingRequest {

    private @Nullable Future<ICraftingPlan> pending;
    private @Nullable ICraftingPlan plan;
    private @Nullable IGrid grid;
    private long frequency;
    private String status = "idle";
    private String message = "";
    private final Int2ObjectMap<ICraftingCPU> cpus = new Int2ObjectOpenHashMap<>();
    private final Reference2IntMap<ICraftingCPU> cpuIds = new Reference2IntOpenHashMap<>();
    private @Nullable IGrid cpuGrid;
    private int nextCpuId;

    public void begin(ServerPlayer player, TerminalConnection connection, AEKey key, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Crafting amount must be positive");
        cancel();
        grid = connection.grid();
        frequency = connection.frequency();
        var source = connection.source();
        pending = grid.getCraftingService().beginCraftingCalculation(player.level(), () -> source, key, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        status = "calculating";
    }

    public void poll(@Nullable TerminalConnection connection) {
        if (grid != null && (connection == null || connection.grid() != grid || connection.frequency() != frequency)) {
            cancel();
            status = "failed";
            message = "offline";
        }
        if (pending == null || !pending.isDone()) return;
        try {
            plan = pending.get();
            status = plan.simulation() ? "missing" : "ready";
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            status = "failed";
            message = "calculation_failed";
            Data_Energistics.LOGGER.error("Interrupted player terminal crafting calculation", failure);
        } catch (ExecutionException failure) {
            status = "failed";
            message = "calculation_failed";
            Data_Energistics.LOGGER.error("Player terminal crafting calculation failed", failure.getCause());
        } finally {
            pending = null;
        }
    }

    public String start(TerminalConnection connection, int cpuIndex) {
        poll(connection);
        if (plan == null || !status.equals("ready") || grid != connection.grid() || frequency != connection.frequency())
            return "invalid_plan";
        if (cpuIndex < -1) throw new IllegalArgumentException("Invalid terminal crafting CPU");
        if (cpuIndex >= 0 && !cpus.containsKey(cpuIndex)) return "invalid_plan";
        ICraftingCPU cpu = cpuIndex < 0 ? null : cpus.get(cpuIndex);
        if (cpuIndex >= 0 && (cpuGrid != grid || !grid.getCraftingService().getCpus().contains(cpu)))
            return "invalid_plan";
        var result = grid.getCraftingService().submitJob(plan, null, cpu, false, connection.source());
        if (result.successful()) {
            status = "submitted";
            plan = null;
            message = "";
            return "submitted";
        }
        status = "failed";
        message = String.valueOf(result.errorCode());
        return "craft_failed";
    }

    public void cancel() {
        if (pending != null) pending.cancel(true);
        pending = null;
        plan = null;
        grid = null;
        status = "idle";
        message = "";
    }

    public CompoundTag snapshot(ServerPlayer player, @Nullable TerminalConnection connection) {
        poll(connection);
        CompoundTag tag = new CompoundTag();
        tag.putString("status", status);
        tag.putString("message", message);
        if (plan != null) {
            tag.putLong("bytes", plan.bytes());
            tag.put("used", resources(player, plan.usedItems()));
            tag.put("missing", resources(player, plan.missingItems()));
        }
        if (connection == null || connection.grid() != cpuGrid) {
            cpuGrid = connection == null ? null : connection.grid();
            cpus.clear();
            cpuIds.clear();
        }
        ListTag cpuTags = new ListTag();
        if (connection != null) {
            var available = connection.grid().getCraftingService().getCpus();
            var entries = cpus.int2ObjectEntrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (!available.contains(entry.getValue())) {
                    cpuIds.removeInt(entry.getValue());
                    entries.remove();
                }
            }
            for (ICraftingCPU cpu : connection.grid().getCraftingService().getCpus()) {
                if (!cpuIds.containsKey(cpu)) {
                    cpuIds.put(cpu, nextCpuId);
                    cpus.put(nextCpuId++, cpu);
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt("id", cpuIds.getInt(cpu));
                entry.putString("name", cpu.getName() == null ? "" : cpu.getName().getString());
                entry.putBoolean("busy", cpu.isBusy());
                entry.putLong("storage", cpu.getAvailableStorage());
                var job = cpu.getJobStatus();
                if (job != null) {
                    entry.putLong("remaining", Math.max(0, job.totalItems() - job.progress()));
                    entry.put("output", GenericStack.writeTag(player.registryAccess(), job.crafting()));
                }
                cpuTags.add(entry);
            }
        }
        tag.put("cpus", cpuTags);
        return tag;
    }

    private static ListTag resources(ServerPlayer player, KeyCounter counter) {
        ListTag result = new ListTag();
        for (var entry : counter) {
            if (result.size() == 512) break;
            result.add(GenericStack.writeTag(player.registryAccess(), new GenericStack(entry.getKey(), entry.getLongValue())));
        }
        return result;
    }
}
