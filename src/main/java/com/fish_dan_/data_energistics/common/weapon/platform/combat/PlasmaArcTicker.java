package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.combat.damage.DirectHealthDamage;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.projectile.FloatingGunProjectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;
import java.util.UUID;

/** Builds a degree-two connection graph once per world update; cooldowns belong to each shooter and victim. */
public final class PlasmaArcTicker {

    private final Object2LongOpenHashMap<ArcVictim> hits = new Object2LongOpenHashMap<>();

    @SubscribeEvent
    public void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 5 != 0) return;
        long now = level.getGameTime();
        hits.object2LongEntrySet().removeIf(entry -> entry.getLongValue() + 10 <= now);
        var owners = new Object2ObjectOpenHashMap<ServerPlayer, ObjectArrayList<FloatingGunProjectile>>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof FloatingGunProjectile ball && ball.form() == GunForm.PLASMA && ball.getOwner() instanceof ServerPlayer owner && owner.isAlive())
                owners.computeIfAbsent(owner, ignored -> new ObjectArrayList<>()).add(ball);
        }
        for (var entry : owners.object2ObjectEntrySet()) {
            var balls = entry.getValue();
            var edges = new ObjectArrayList<Arc>();
            for (int first = 0; first < balls.size(); first++) {
                FloatingGunProjectile a = balls.get(first);
                GunTargeting targeting = new GunTargeting(entry.getKey(), a.protectsFriendlies());
                for (int second = first + 1; second < balls.size(); second++) {
                    FloatingGunProjectile b = balls.get(second);
                    double distance = a.distanceToSqr(b);
                    if (distance <= 36 && targeting.visible(a.position(), b.position())) edges.add(new Arc(a, b, distance));
                }
            }
            edges.sort(Comparator.comparingDouble(Arc::distance).thenComparingInt(arc -> arc.first.getId()).thenComparingInt(arc -> arc.second.getId()));
            var degrees = new Int2IntOpenHashMap();
            for (Arc edge : edges) {
                if (degrees.get(edge.first.getId()) == 2 || degrees.get(edge.second.getId()) == 2) continue;
                degrees.addTo(edge.first.getId(), 1);
                degrees.addTo(edge.second.getId(), 1);
                GunTargeting targeting = new GunTargeting(entry.getKey(), edge.first.protectsFriendlies() || edge.second.protectsFriendlies());
                var from = edge.first.position();
                var to = edge.second.position();
                GunImpactEffects.trail(level, from, to, edge.first.ammunition().color, 0.6f);
                for (GunRayTrace.Hit hit : GunRayTrace.collect(level, edge.first, from, to, 0.15, targeting::canAttack)) {
                    LivingEntity target = hit.target();
                    var key = new ArcVictim(entry.getKey().getUUID(), target.getUUID());
                    if (hits.containsKey(key)) continue;
                    hits.put(key, now);
                    DirectHealthDamage.hurt(target, DirectHealthDamage.source(target, entry.getKey()), 2);
                }
            }
        }
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        hits.clear();
    }

    private record Arc(FloatingGunProjectile first, FloatingGunProjectile second, double distance) {}

    private record ArcVictim(UUID owner, UUID target) {}
}
