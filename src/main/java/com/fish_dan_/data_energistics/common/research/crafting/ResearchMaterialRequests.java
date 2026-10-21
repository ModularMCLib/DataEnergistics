package com.fish_dan_.data_energistics.common.research.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.common.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.common.network.packets.research.ResearchMaterialFavoritesPayload;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Server-thread planning queue shared by station UUID; submitted standalone jobs belong to AE2's CPUs. */
public final class ResearchMaterialRequests {

    private final Object2ObjectOpenHashMap<RequestKey, Pending> pending = new Object2ObjectOpenHashMap<>();

    /** Used by the world-owned polling set; completed and cancelled calculations release their hosts and grids. */
    public boolean hasPendingCalculations() {
        return !pending.isEmpty();
    }

    /** Cancels only unsubmitted planning work when the owning server stops. AE2 owns submitted CPU jobs. */
    public void cancelCalculations() {
        for (Pending request : pending.values()) request.calculation.cancel(true);
        pending.clear();
    }

    /**
     * Requests the missing portion of a validated node's costs without withdrawing research materials.
     * The caller has checked the node, target, prerequisites and online grid. Repeated requests share
     * one calculation for each grid/key and account for the final outputs of already running jobs.
     */
    public Component request(ResearchStationBlockEntity host, ServerPlayer player, ResearchNode node, IGrid grid) {
        Object2LongMap<AEKey> costs = new Object2LongLinkedOpenHashMap<>();
        for (ResearchInput input : node.inputs()) costs.mergeLong(input.key(), input.amount(), Math::addExact);
        ObjectList<GenericStack> favorites = new ObjectArrayList<>();
        int calculating = 0;
        int waiting = 0;
        IActionSource source = IActionSource.ofPlayer(player, host);
        for (var entry : costs.object2LongEntrySet()) {
            RequestKey key = new RequestKey(grid, entry.getKey());
            Pending existing = lookup(key);
            if (existing != null) {
                if (entry.getLongValue() > existing.required) {
                    pending.put(key, new Pending(key, existing.host, existing.player, entry.getLongValue(),
                            existing.amount, existing.calculation));
                }
                waiting++;
                continue;
            }
            long missing = missing(grid, key.resource, entry.getLongValue(), source);
            if (missing == 0) {
                waiting++;
                continue;
            }
            if (!grid.getCraftingService().isCraftable(key.resource)) {
                favorites.add(new GenericStack(key.resource, missing));
            } else {
                try {
                    begin(key, host, player, entry.getLongValue(), missing);
                    calculating++;
                } catch (RuntimeException error) {
                    Data_Energistics.LOGGER.error("Cannot calculate research materials for station {}, resource {}, player {}",
                            host.stationId(), key.resource, player.getUUID(), error);
                    favorites.add(new GenericStack(key.resource, missing));
                    player.sendSystemMessage(message("material_calculation_failed", key.resource.getDisplayName()));
                }
            }
        }
        favorite(player, favorites);
        return message("materials_requested", calculating, waiting, favorites.size());
    }

    /** Polls on the server thread even when the original station is unloaded; get() follows isDone() only. */
    public void tick(MinecraftServer server) {
        ObjectList<Ready> ready = new ObjectArrayList<>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending request = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(request.player);
            if (player == null || !connected(request)) {
                request.calculation.cancel(true);
                iterator.remove();
                if (player != null) player.sendSystemMessage(message("material_network_changed", request.key.resource.getDisplayName()));
            } else if (request.calculation.isDone()) {
                iterator.remove();
                ready.add(new Ready(request, player));
            }
        }
        for (Ready completed : ready) {
            Pending request = completed.request;
            ServerPlayer player = completed.player;
            try {
                ICraftingPlan plan = Objects.requireNonNull(request.calculation.get(), "AE2 returned a null research material plan");
                finish(request, player, plan);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                Data_Energistics.LOGGER.warn("Research material planning interrupted for station {}, resource {}, player {}",
                        request.host.stationId(), request.key.resource, request.player, error);
                player.sendSystemMessage(message("material_calculation_failed", request.key.resource.getDisplayName()));
            } catch (ExecutionException | RuntimeException error) {
                Data_Energistics.LOGGER.error("Research material request failed for station {}, resource {}, player {}",
                        request.host.stationId(), request.key.resource, request.player, error);
                player.sendSystemMessage(message("material_calculation_failed", request.key.resource.getDisplayName()));
                favorite(player, ObjectList.of(new GenericStack(request.key.resource, request.amount)));
            }
        }
    }

    private void begin(RequestKey key, ResearchStationBlockEntity host, ServerPlayer player, long required, long amount) {
        IActionSource source = IActionSource.ofPlayer(player, host);
        Future<ICraftingPlan> calculation = key.grid.getCraftingService().beginCraftingCalculation(
                host.serverLevel(), () -> source, key.resource, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        pending.put(key, new Pending(key, host, player.getUUID(), required, amount, calculation));
    }

    private void finish(Pending request, ServerPlayer player, ICraftingPlan plan) {
        IActionSource source = IActionSource.ofPlayer(player, request.host);
        long needed = missing(request.key.grid, request.key.resource, request.required, source);
        if (needed == 0) return;
        if (needed != request.amount) {
            begin(request.key, request.host, player, request.required, needed);
            return;
        }
        if (plan.simulation()) {
            Object2LongMap<AEKey> unavailable = new Object2LongLinkedOpenHashMap<>();
            unavailable.put(request.key.resource, needed);
            for (var entry : plan.missingItems()) {
                if (entry.getLongValue() > 0) unavailable.mergeLong(entry.getKey(), entry.getLongValue(), Math::max);
            }
            ObjectList<GenericStack> favorites = new ObjectArrayList<>(unavailable.size());
            unavailable.object2LongEntrySet().forEach(entry -> favorites.add(new GenericStack(entry.getKey(), entry.getLongValue())));
            favorite(player, favorites);
            player.sendSystemMessage(message("material_not_craftable", request.key.resource.getDisplayName()));
            return;
        }
        var result = request.key.grid.getCraftingService().submitJob(plan, null, null, true, source);
        if (result.successful()) {
            player.sendSystemMessage(message("material_crafting_started", request.key.resource.getDisplayName(), needed));
        } else {
            favorite(player, ObjectList.of(new GenericStack(request.key.resource, needed)));
            player.sendSystemMessage(message("material_submit_failed", request.key.resource.getDisplayName(),
                    message("craft_error_" + Objects.requireNonNull(result.errorCode()).name().toLowerCase(Locale.ROOT))));
        }
    }

    private static long missing(IGrid grid, AEKey resource, long required, IActionSource source) {
        long available = grid.getStorageService().getInventory().extract(resource, required, Actionable.SIMULATE, source);
        long missing = required - available;
        // isRequesting/getRequestedAmount also include intermediate ingredients; count final outputs only.
        for (var cpu : grid.getCraftingService().getCpus()) {
            var job = cpu.getJobStatus();
            if (job != null && job.crafting().what().equals(resource)) {
                missing -= Math.min(missing, job.crafting().amount());
            }
        }
        return missing;
    }

    private static boolean connected(Pending request) {
        return !request.host.isRemoved() && !request.host.faulted() && request.host.getMainNode().isOnline() &&
                request.host.getMainNode().getGrid() == request.key.grid;
    }

    private static void favorite(ServerPlayer player, ObjectList<GenericStack> materials) {
        ObjectOpenHashSet<AEKey> seen = new ObjectOpenHashSet<>();
        ObjectList<GenericStack> batch = new ObjectArrayList<>();
        for (GenericStack stack : materials) {
            if (!seen.add(stack.what())) continue;
            batch.add(stack);
            if (batch.size() == ResearchMaterialFavoritesPayload.MAX_MATERIALS) {
                PacketDistributor.sendToPlayer(player, new ResearchMaterialFavoritesPayload(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) PacketDistributor.sendToPlayer(player, new ResearchMaterialFavoritesPayload(batch));
    }

    private static Component message(String key, Object... arguments) {
        return Component.translatable("research.data_energistics." + key, arguments);
    }

    /** Adapts Fastutil's unannotated lookup to an explicit absent-key contract. */
    private @Nullable Pending lookup(RequestKey key) {
        return pending.containsKey(key) ? pending.get(key) : null;
    }

    private record RequestKey(IGrid grid, AEKey resource) {}

    private record Ready(Pending request, ServerPlayer player) {}

    private record Pending(RequestKey key, ResearchStationBlockEntity host, UUID player,
                           long required, long amount, Future<ICraftingPlan> calculation) {}
}
