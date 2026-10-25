package com.fish_dan_.data_energistics.common.weapon.platform.ability;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.NetworkShotPayment;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForgeMod;

import org.jspecify.annotations.Nullable;

/** Equipment-owned grants and fixed per-tick payments; disabling releases only grants made by this instance. */
public final class PlatformAbilityRuntime {

    public int active;
    private boolean flightGranted;
    private static final ResourceLocation FLIGHT_GRANT = Data_Energistics.id("weapon_platform_flight");
    private @Nullable MobEffectInstance nightVision;

    public void tick(PlatformRuntime runtime, @Nullable TerminalConnection connection) {
        var player = runtime.player;
        var settings = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT);
        var research = ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids();
        active = 0;
        for (PlatformAbility ability : PlatformAbility.values()) {
            if (ability == PlatformAbility.SHIELD || ability == PlatformAbility.DOMAIN || !settings.enabled(ability) || !research.contains(ability.research) || connection == null) continue;
            NetworkShotPayment.pay(runtime.platform, connection, ability.energy, GunAmmunition.AE, false, ammo -> active |= ability.flag);
        }
        if ((active & PlatformAbility.SATIETY.flag) != 0) player.getFoodData().setFoodLevel(20);
        var motion = player.getData(DEAttachments.PLATFORM_MOTION);
        motion.detached = false;
        motion.locked = (active & PlatformAbility.MOVEMENT_PROTECTION.flag) != 0;
        boolean noclip = (active & PlatformAbility.NOCLIP.flag) != 0;
        if (!noclip && motion.noclip && !player.level().noCollision(player, player.getBoundingBox().deflate(0.01))) {
            noclip = true;
            active |= PlatformAbility.NOCLIP.flag;
        }
        motion.noclip = noclip;
        if (noclip) player.noPhysics = true;
        if ((active & PlatformAbility.FLIGHT.flag) != 0) {
            if (!flightGranted) {
                player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).addTransientModifier(new AttributeModifier(FLIGHT_GRANT, 1, AttributeModifier.Operation.ADD_VALUE));
                flightGranted = true;
                player.onUpdateAbilities();
            }
        } else releaseFlight(player);
        if ((active & PlatformAbility.NIGHT_VISION.flag) != 0) {
            var current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || !current.isInfiniteDuration() && current.getDuration() < 210) {
                var effect = new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false);
                player.addEffect(effect);
                nightVision = player.getEffect(MobEffects.NIGHT_VISION);
            }
        } else releaseNightVision(player);
    }

    public void close(ServerPlayer player) {
        var motion = player.getData(DEAttachments.PLATFORM_MOTION);
        motion.locked = false;
        motion.detached = motion.noclip;
        if (player.level().noCollision(player, player.getBoundingBox().deflate(0.01))) motion.noclip = false;
        active = motion.noclip ? PlatformAbility.NOCLIP.flag : 0;
        releaseFlight(player);
        releaseNightVision(player);
    }

    private void releaseFlight(ServerPlayer player) {
        if (!flightGranted) return;
        flightGranted = false;
        player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).removeModifier(FLIGHT_GRANT);
        if (!player.mayFly()) player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    private void releaseNightVision(ServerPlayer player) {
        if (nightVision != null && player.getEffect(MobEffects.NIGHT_VISION) == nightVision && nightVision.getDuration() <= 220) player.removeEffect(MobEffects.NIGHT_VISION);
        nightVision = null;
    }
}
