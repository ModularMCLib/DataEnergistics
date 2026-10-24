package com.fish_dan_.data_energistics.common.weapon.platform.runtime;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.effect.RadixRedundancyEffectLogic;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.RadarDelta;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.TargetSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;

/** Loaded-entity enumeration is periodic; visibility work and delta transport have independent per-tick budgets. */
public final class PlatformRadar {

    private final ObjectArrayList<LivingEntity> candidates = new ObjectArrayList<>();
    private final Int2ObjectOpenHashMap<TargetSnapshot> cache = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<TargetSnapshot> changed = new Int2ObjectOpenHashMap<>();
    private final IntArrayList removed = new IntArrayList();
    private int cursor;
    private boolean reset = true;
    private boolean running;

    public void tick(PlatformRuntime runtime) {
        var player = runtime.player;
        boolean enabled = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_HUD_ENABLED, true) && ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformChipUpgrades.HUD);
        if (!enabled) {
            if (running) {
                PlatformRpc.radar(player, new RadarDelta(true, new ObjectArrayList<>(), new IntArrayList()));
                cache.clear();
                candidates.clear();
                changed.clear();
                removed.clear();
                running = false;
                reset = true;
            }
            return;
        }
        boolean refresh = !running || player.tickCount % 20 == 0;
        running = true;
        boolean protect = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true);
        GunTargeting targeting = new GunTargeting(player, protect);
        if (refresh) {
            candidates.clear();
            candidates.addAll(player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GunTargeting.RADAR_RADIUS),
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator() && player.distanceToSqr(entity) <= GunTargeting.RADAR_RADIUS_SQUARED));
            candidates.sort(Comparator.<LivingEntity>comparingInt(entity -> runtime.locks.contains(entity.getUUID()) ? 0 : 1).thenComparingDouble(player::distanceToSqr));
            var present = new IntOpenHashSet();
            for (LivingEntity entity : candidates) present.add(entity.getId());
            var iterator = cache.keySet().iterator();
            while (iterator.hasNext()) {
                int id = iterator.nextInt();
                if (!present.contains(id)) {
                    iterator.remove();
                    changed.remove(id);
                    removed.add(id);
                }
            }
            cursor = candidates.isEmpty() ? 0 : cursor % candidates.size();
        }
        for (int count = 0; count < Math.min(8, candidates.size()); count++) {
            LivingEntity entity = candidates.get(cursor++);
            if (cursor == candidates.size()) cursor = 0;
            if (!entity.isAlive() || entity.level() != player.level()) continue;
            var bounds = entity.getBoundingBox();
            int color = entity instanceof Player ally && targeting.allied(ally) || player.isAlliedTo(entity) ? 0x75e8a6 : entity instanceof Enemy || runtime.locks.contains(entity.getUUID()) ? 0xff626f : 0xffd367;
            boolean visible = targeting.visible(player.getEyePosition(), bounds.getCenter());
            var snapshot = new TargetSnapshot(entity.getId(), entity.getHealth(), entity.getMaxHealth(), RadixRedundancyEffectLogic.layers(entity), color, entity.getDisplayName(),
                    new Vec3(bounds.minX, bounds.minY, bounds.minZ), new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ), entity.position().subtract(entity.xo, entity.yo, entity.zo), visible);
            if (!snapshot.equals(cache.put(entity.getId(), snapshot))) changed.put(entity.getId(), snapshot);
        }
        if (player.tickCount % 5 != 0) return;
        var batch = new ObjectArrayList<TargetSnapshot>();
        var iterator = changed.values().iterator();
        while (iterator.hasNext() && batch.size() < 128) {
            batch.add(iterator.next());
            iterator.remove();
        }
        if (reset || !batch.isEmpty() || !removed.isEmpty()) PlatformRpc.radar(player, new RadarDelta(reset, batch, new IntArrayList(removed)));
        reset = false;
        removed.clear();
    }
}
