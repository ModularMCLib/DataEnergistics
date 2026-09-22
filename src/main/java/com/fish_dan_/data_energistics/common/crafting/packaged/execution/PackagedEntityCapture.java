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

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Causal ownership of drops created by a claimed machine, never inferred from nearby item types. */
@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class PackagedEntityCapture {

    private static final String OWNER = "data_energistics_packaged_operation";
    private static final ThreadLocal<@Nullable Scope> CURRENT = new ThreadLocal<>();

    private PackagedEntityCapture() {}

    public static void run(ServerLevel level, UUID operation, Runnable action) {
        withScope(new Scope(level, operation), action);
    }

    /** Keeps pre-installed player assets outside an enclosing operation-owned removal callback. */
    public static void unowned(Runnable action) {
        withScope(null, action);
    }

    public static void machineTick(ServerLevel level, BlockPos position, Runnable tick) {
        // Resolve the claim at the tick boundary. A cached LevelEvent.Load view can be stale
        // during world reloads, which would let native drops escape without an operation owner.
        var owner = PackagedMachineClaims.get(level).owner(position);
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

    /** Binds a native machine drop after the machine created it outside the tick capture scope. */
    public static void claim(ItemEntity entity, UUID operation) {
        entity.getPersistentData().putUUID(OWNER, operation);
        entity.setUnlimitedLifetime();
    }

    public static @Nullable UUID owner(ItemEntity entity) {
        var data = entity.getPersistentData();
        return data.hasUUID(OWNER) ? data.getUUID(OWNER) : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) ||
                !(event.getEntity() instanceof ItemEntity item) || owner(item) != null)
            return;
        var scope = CURRENT.get();
        if (scope == null || scope.level() != level) return;
        claim(item, scope.operation());
    }

    private record Scope(ServerLevel level, UUID operation) {}
}
