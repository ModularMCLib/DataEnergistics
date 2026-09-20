package com.fish_dan_.data_energistics.common.crafting.packaged.execution;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Causal ownership of drops created by a claimed machine, never inferred from nearby item types. */
@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class PackagedEntityCapture {

    private static final String OWNER = "data_energistics_packaged_operation";
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final Reference2ObjectOpenHashMap<ServerLevel, PackagedMachineClaims> CLAIMS = new Reference2ObjectOpenHashMap<>();

    private PackagedEntityCapture() {}

    public static void run(ServerLevel level, UUID operation, Runnable action) {
        withScope(new Scope(level, operation), action);
    }

    public static void machineTick(ServerLevel level, BlockPos position, Runnable tick) {
        var claims = CLAIMS.get(level);
        var owner = claims == null ? null : claims.owner(position);
        withScope(owner == null ? null : new Scope(level, owner), tick);
    }

    private static void withScope(@Nullable Scope scope, Runnable action) {
        var previous = CURRENT.get();
        if (scope == null) CURRENT.remove();
        else CURRENT.set(scope);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static boolean ownedBy(ItemEntity entity, UUID operation) {
        return operation.equals(owner(entity));
    }

    public static @Nullable UUID owner(ItemEntity entity) {
        var data = entity.getPersistentData();
        return data.hasUUID(OWNER) ? data.getUUID(OWNER) : null;
    }

    @SubscribeEvent
    public static void loaded(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) CLAIMS.put(level, PackagedMachineClaims.get(level));
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) CLAIMS.remove(level);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        var scope = CURRENT.get();
        if (scope == null || event.isCanceled() || event.loadedFromDisk() || event.getLevel() != scope.level() ||
                !(event.getEntity() instanceof ItemEntity item) || owner(item) != null)
            return;
        item.getPersistentData().putUUID(OWNER, scope.operation());
        // A provider can be offline longer than the vanilla five-minute item lifetime.
        // These physical inputs/products remain owned until the operation collects them.
        item.setUnlimitedLifetime();
    }

    private record Scope(ServerLevel level, UUID operation) {}
}
