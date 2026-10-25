package com.fish_dan_.data_energistics.common.weapon.platform.ability;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Normal input has a short-lived movement permit; pushes, direct position writes and teleports have none. */
public final class PlatformMotion {

    private PlatformMotion() {}

    public static boolean locked(Entity entity) {
        return entity instanceof Player && entity.hasData(DEAttachments.PLATFORM_MOTION) && entity.getData(DEAttachments.PLATFORM_MOTION).locked;
    }

    public static boolean blocked(Entity entity) {
        return DomainStasis.frozen(entity) || locked(entity) && entity.getData(DEAttachments.PLATFORM_MOTION).permits == 0;
    }

    public static boolean noclip(Entity entity) {
        return entity instanceof Player && entity.hasData(DEAttachments.PLATFORM_MOTION) && entity.getData(DEAttachments.PLATFORM_MOTION).noclip;
    }

    public static Vec3 inputMotion(Player player, int input) {
        double forward = ((input & 1) != 0 ? 1 : 0) - ((input & 2) != 0 ? 1 : 0);
        double sideways = ((input & 4) != 0 ? 1 : 0) - ((input & 8) != 0 ? 1 : 0);
        double vertical = ((input & 16) != 0 ? 1 : 0) - ((input & 32) != 0 ? 1 : 0);
        Vec3 motion = new Vec3(sideways, vertical, forward);
        if (motion.lengthSqr() > 1) motion = motion.normalize();
        boolean sprinting = (input & 64) != 0 || player.isSprinting();
        boolean flying = player.getAbilities().flying;
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double speed = movement.getValue();
        if ((flying || sprinting) && !movement.hasModifier(LivingEntity.SPEED_MODIFIER_SPRINTING.id())) {
            speed = movement.getAttribute().value().sanitizeValue(speed * (1 + LivingEntity.SPEED_MODIFIER_SPRINTING.amount()));
        }
        // Native input damping and ordinary-ground friction turn the movement attribute into travel per tick.
        speed *= 0.98 / (1 - 0.6 * 0.91);
        var state = player.getData(DEAttachments.PLATFORM_MOTION);
        if (flying && state.flying && (input & 64) != 0) speed = Math.max(speed, state.maximumFlightSpeed);
        return motion.yRot((float) (-player.getYRot() * Math.PI / 180)).scale(speed);
    }
}
