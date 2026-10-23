package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.research.team.ResearchTeams;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunSettings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.UUID;

/** Server-thread targeting policy captured once for a selection or projectile impact. */
public record GunTargeting(ServerPlayer owner, boolean protectFriendlies, ObjectSet<UUID> teams) {

    public static final int FIRE_CONTROL_RADIUS = 5 * 16;
    public static final int FIRE_CONTROL_RADIUS_SQUARED = FIRE_CONTROL_RADIUS * FIRE_CONTROL_RADIUS;
    public static final int RADAR_RADIUS = 32 * 16;
    public static final int RADAR_RADIUS_SQUARED = RADAR_RADIUS * RADAR_RADIUS;

    public GunTargeting(ServerPlayer owner, boolean protectFriendlies) {
        this(owner, protectFriendlies, ResearchTeams.subjectIds(owner));
    }

    public boolean allied(Player target) {
        if (target == owner || owner.isAlliedTo(target)) return true;
        if (!(target instanceof ServerPlayer player)) return false;
        for (UUID team : ResearchTeams.subjectIds(player)) if (teams.contains(team)) return true;
        return false;
    }

    public boolean canAttack(LivingEntity target) {
        if (!target.isAlive() || target == owner || target.level() != owner.level()) return false;
        if (target instanceof ServerPlayer player && (player.isCreative() || player.isSpectator() || player.hasPermissions(2) || !owner.server.isPvpAllowed() || !owner.canHarmPlayer(player))) return false;
        return !protectFriendlies || !(target instanceof Player player && allied(player)) && !(target instanceof TamableAnimal animal && owner.getUUID().equals(animal.getOwnerUUID()));
    }

    public boolean matches(LivingEntity target, int mask) {
        int category = target instanceof Player ? GunSettings.PLAYERS : target instanceof NeutralMob ? GunSettings.NEUTRAL : target instanceof Enemy ? GunSettings.HOSTILE : target.getType().getCategory() == MobCategory.CREATURE || target.getType().getCategory() == MobCategory.WATER_CREATURE ? GunSettings.FRIENDLY : GunSettings.OTHER;
        return (mask & category) != 0;
    }

    public boolean visible(Vec3 origin, Vec3 destination) {
        return GunRayTrace.clip(owner.level(), owner, origin, destination).getType() == HitResult.Type.MISS;
    }
}
