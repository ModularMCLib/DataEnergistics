package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;

/** One immutable update shared by the complete menu and the tactical HUD. */
public record PlatformSnapshot(boolean present, boolean connected, boolean hud, boolean friendlyProtection, boolean failed, int capacity, int lock,
                               ItemStack chip, ObjectList<GunSnapshot> guns, ObjectList<TargetSnapshot> targets) {

    public static final PlatformSnapshot EMPTY = new PlatformSnapshot(false, false, false, true, false, 2, -1, ItemStack.EMPTY, ObjectLists.emptyList(), ObjectLists.emptyList());
    public static final Codec<PlatformSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("present").forGetter(PlatformSnapshot::present),
            Codec.BOOL.fieldOf("connected").forGetter(PlatformSnapshot::connected),
            Codec.BOOL.fieldOf("hud").forGetter(PlatformSnapshot::hud),
            Codec.BOOL.fieldOf("protection").forGetter(PlatformSnapshot::friendlyProtection),
            Codec.BOOL.fieldOf("failed").forGetter(PlatformSnapshot::failed),
            Codec.intRange(2, 8).fieldOf("capacity").forGetter(PlatformSnapshot::capacity),
            Codec.INT.fieldOf("lock").forGetter(PlatformSnapshot::lock),
            ItemStack.OPTIONAL_CODEC.fieldOf("chip").forGetter(PlatformSnapshot::chip),
            GunSnapshot.CODEC.listOf(0, 8).<ObjectList<GunSnapshot>>xmap(ObjectArrayList::new, list -> list).fieldOf("guns").forGetter(PlatformSnapshot::guns),
            TargetSnapshot.CODEC.listOf(0, 24).<ObjectList<TargetSnapshot>>xmap(ObjectArrayList::new, list -> list).fieldOf("targets").forGetter(PlatformSnapshot::targets)).apply(instance, PlatformSnapshot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlatformSnapshot> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static PlatformSnapshot capture(PlatformRuntime runtime) {
        ItemStack chip = runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT);
        boolean hud = ItemResearchUpgrades.of(chip).ids().contains(BiochipResearch.HUD);
        boolean protection = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true);
        var guns = new ObjectArrayList<GunSnapshot>();
        for (var gun : runtime.guns) {
            ItemStack item = runtime.inventory.getStackInSlot(gun.slot());
            if (!item.isEmpty()) guns.add(new GunSnapshot(gun.slot(), item, item.getOrDefault(DEDataComponents.GUN_SETTINGS, GunSettings.DEFAULT), gun.heat(), gun.overheated(), gun.actual().ordinal(), gun.target(), gun.state()));
        }
        LivingEntity locked = runtime.lock == null ? null : runtime.player.serverLevel().getEntity(runtime.lock) instanceof LivingEntity living ? living : null;
        int lock = locked == null ? -1 : locked.getId();
        var targets = new ObjectArrayList<TargetSnapshot>();
        if (hud) {
            GunTargeting targeting = new GunTargeting(runtime.player, protection);
            var candidates = new ObjectArrayList<>(runtime.player.level().getEntitiesOfClass(LivingEntity.class, runtime.player.getBoundingBox().inflate(64), entity -> entity != runtime.player && entity.isAlive() && !entity.isSpectator() && runtime.player.distanceToSqr(entity) <= 4096));
            candidates.sort(Comparator.<LivingEntity>comparingInt(entity -> entity.getId() == lock ? 0 : 1).thenComparingDouble(runtime.player::distanceToSqr));
            for (int index = 0; index < Math.min(24, candidates.size()); index++) {
                LivingEntity entity = candidates.get(index);
                int color = entity instanceof Player player && targeting.allied(player) || runtime.player.isAlliedTo(entity) ? 0x75e8a6 : entity instanceof Enemy || entity.getId() == lock ? 0xff626f : 0xffd367;
                targets.add(new TargetSnapshot(entity.getId(), entity.getHealth(), entity.getMaxHealth(), entity.getPersistentData().getInt(GunImpactEffects.BINARY), color));
            }
        }
        return new PlatformSnapshot(true, runtime.connected, hud, protection, runtime.failed, BiochipResearch.capacity(chip), lock, chip,
                ObjectLists.unmodifiable(guns), ObjectLists.unmodifiable(targets));
    }
}
