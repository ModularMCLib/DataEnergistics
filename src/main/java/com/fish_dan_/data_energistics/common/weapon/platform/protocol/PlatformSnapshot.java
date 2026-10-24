package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunImpactEffects;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;

/** One immutable update shared by the complete menu and the tactical HUD. */
public record PlatformSnapshot(boolean present, boolean connected, boolean hud, boolean hudEnabled, boolean friendlyProtection, boolean attackEnabled, boolean failed, int capacity, IntSet locks, int targetMask,
                               ItemStack chip, ObjectList<GunSnapshot> guns, ObjectList<TargetSnapshot> targets, PlatformNetworkSnapshot network) {

    public static final PlatformSnapshot EMPTY = new PlatformSnapshot(false, false, false, true, true, true, false, 2, IntSets.emptySet(), GunSettings.HOSTILE, ItemStack.EMPTY, ObjectLists.emptyList(), ObjectLists.emptyList(), PlatformNetworkSnapshot.EMPTY);
    public static final Codec<PlatformSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("present").forGetter(PlatformSnapshot::present),
            Codec.BOOL.fieldOf("connected").forGetter(PlatformSnapshot::connected),
            Codec.BOOL.fieldOf("hud").forGetter(PlatformSnapshot::hud),
            Codec.BOOL.optionalFieldOf("hud_enabled", true).forGetter(PlatformSnapshot::hudEnabled),
            Codec.BOOL.fieldOf("protection").forGetter(PlatformSnapshot::friendlyProtection),
            Codec.BOOL.optionalFieldOf("attack_enabled", true).forGetter(PlatformSnapshot::attackEnabled),
            Codec.BOOL.fieldOf("failed").forGetter(PlatformSnapshot::failed),
            Codec.intRange(2, 8).fieldOf("capacity").forGetter(PlatformSnapshot::capacity),
            Codec.INT.listOf().xmap(list -> IntSets.unmodifiable(new IntOpenHashSet(list)), IntArrayList::new).fieldOf("locks").forGetter(PlatformSnapshot::locks),
            Codec.intRange(0, 31).fieldOf("target_mask").forGetter(PlatformSnapshot::targetMask),
            ItemStack.OPTIONAL_CODEC.fieldOf("chip").forGetter(PlatformSnapshot::chip),
            GunSnapshot.CODEC.listOf(0, 8).<ObjectList<GunSnapshot>>xmap(ObjectArrayList::new, list -> list).fieldOf("guns").forGetter(PlatformSnapshot::guns),
            TargetSnapshot.CODEC.listOf().<ObjectList<TargetSnapshot>>xmap(ObjectArrayList::new, list -> list).fieldOf("targets").forGetter(PlatformSnapshot::targets),
            PlatformNetworkSnapshot.CODEC.fieldOf("network").forGetter(PlatformSnapshot::network)).apply(instance, PlatformSnapshot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlatformSnapshot> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static PlatformSnapshot capture(PlatformRuntime runtime) {
        ItemStack chip = runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT);
        boolean hud = ItemResearchUpgrades.of(chip).ids().contains(BiochipResearch.HUD);
        boolean protection = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true);
        var guns = new ObjectArrayList<GunSnapshot>();
        for (var gun : runtime.guns) {
            ItemStack item = runtime.inventory.getStackInSlot(gun.slot());
            if (!item.isEmpty()) guns.add(new GunSnapshot(gun.slot(), item, item.getOrDefault(DEDataComponents.GUN_SETTINGS, GunSettings.DEFAULT), gun.heat(), gun.overheated(), gun.actual().ordinal(), gun.target(), gun.state(), gun.flight(), gun.origin(), gun.destination()));
        }
        var locks = new IntOpenHashSet();
        for (var id : runtime.locks) {
            if (runtime.player.serverLevel().getEntity(id) instanceof LivingEntity living) locks.add(living.getId());
        }
        var targets = new ObjectArrayList<TargetSnapshot>();
        if (hud) {
            GunTargeting targeting = new GunTargeting(runtime.player, protection);
            var assigned = new IntOpenHashSet();
            for (var gun : guns) if (gun.settings().enabled() && gun.target() >= 0) assigned.add(gun.target());
            var candidates = new ObjectArrayList<>(runtime.player.level().getEntitiesOfClass(LivingEntity.class, runtime.player.getBoundingBox().inflate(GunTargeting.RADAR_RADIUS), entity -> entity != runtime.player && entity.isAlive() && !entity.isSpectator() && runtime.player.distanceToSqr(entity) <= GunTargeting.RADAR_RADIUS_SQUARED));
            candidates.sort(Comparator.<LivingEntity>comparingInt(entity -> locks.contains(entity.getId()) ? 0 : assigned.contains(entity.getId()) ? 1 : 2).thenComparingDouble(runtime.player::distanceToSqr));
            for (LivingEntity entity : candidates) {
                int color = entity instanceof Player player && targeting.allied(player) || runtime.player.isAlliedTo(entity) ? 0x75e8a6 : entity instanceof Enemy || locks.contains(entity.getId()) ? 0xff626f : 0xffd367;
                var bounds = entity.getBoundingBox();
                boolean visible = targeting.visible(runtime.player.getEyePosition(), entity.getEyePosition()) || targeting.visible(runtime.player.getEyePosition(), bounds.getCenter());
                targets.add(new TargetSnapshot(entity.getId(), entity.getHealth(), entity.getMaxHealth(), entity.getPersistentData().getInt(GunImpactEffects.BINARY), color,
                        entity.getDisplayName(), new Vec3(bounds.minX, bounds.minY, bounds.minZ), new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ),
                        entity.position().subtract(entity.xo, entity.yo, entity.zo), visible));
            }
        }
        var connection = TerminalQuantumAccess.resolve(runtime.player);
        return new PlatformSnapshot(true, connection != null, hud, runtime.platform.getOrDefault(DEDataComponents.PLATFORM_HUD_ENABLED, true), protection,
                runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ATTACK_ENABLED, true), runtime.failed, BiochipResearch.capacity(chip), IntSets.unmodifiable(locks),
                runtime.platform.getOrDefault(DEDataComponents.PLATFORM_TARGETS, GunSettings.HOSTILE), chip,
                ObjectLists.unmodifiable(guns), ObjectLists.unmodifiable(targets), connection == null ? PlatformNetworkSnapshot.EMPTY : PlatformNetworkSnapshot.capture(connection));
    }
}
