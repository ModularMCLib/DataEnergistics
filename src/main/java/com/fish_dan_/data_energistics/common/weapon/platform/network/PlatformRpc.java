package com.fish_dan_.data_energistics.common.weapon.platform.network;

import com.fish_dan_.data_energistics.client.render.overlay.PlatformOreRenderer;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixReboot;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.OreSurveyDelta;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.RadarDelta;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.WeaponPlatformTicker;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformMenu;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketHandler;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;

import net.minecraft.server.level.ServerPlayer;

/** LDLib2 RPC boundary. Settings are accepted only for the equipped platform's complete menu. */
public final class PlatformRpc {

    public static final String REQUEST = "data_energistics:weapon_platform_request";
    public static final String SNAPSHOT = "data_energistics:weapon_platform_snapshot";
    public static final String RADAR = "data_energistics:weapon_platform_radar";
    public static final String ORES = "data_energistics:weapon_platform_ores";
    public static final int OPEN = 0, LOCK = 1, ENABLED = 2, AMMUNITION = 3, TARGETS = 4, SELF = 5, TEAM = 6, PROTECTION = 7, ATTACK = 8, HUD = 9;
    public static final int LOCK_TOGGLE = 0, LOCK_SWEEP_START = 1, LOCK_SWEEP_END = 2, LOCK_SWEEP_REMOVE = 3;
    public static final int GUN_INHERIT_TARGETS = 10, GUN_TARGETS = 11;
    public static final int REBOOT = 12;
    public static final int ABILITY = 13, SHIELD_COLOR = 14, SCAN_RADIUS = 15;
    public static final int INPUT = 16;

    private PlatformRpc() {}

    public static void register() {
        RPCPacketDistributor.registerPacket(ORES, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> OreSurveyDelta.STREAM_CODEC.encode(buffer, (OreSurveyDelta) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] data) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(data, buffer -> args[0] = OreSurveyDelta.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) PlatformOreRenderer.accept((OreSurveyDelta) args[0]);
            }
        });
        RPCPacketDistributor.registerPacket(RADAR, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> RadarDelta.STREAM_CODEC.encode(buffer, (RadarDelta) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] data) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(data, buffer -> args[0] = RadarDelta.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) PlatformClientState.radar((RadarDelta) args[0]);
            }
        });
        RPCPacketDistributor.registerPacket(SNAPSHOT, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> PlatformSnapshot.STREAM_CODEC.encode(buffer, (PlatformSnapshot) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] data) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(data, buffer -> args[0] = PlatformSnapshot.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) PlatformClientState.accept((PlatformSnapshot) args[0]);
            }
        });
    }

    public static void push(ServerPlayer player, PlatformSnapshot snapshot) {
        RPCPacketDistributor.rpcToPlayer(player, SNAPSHOT, snapshot);
    }

    public static void radar(ServerPlayer player, RadarDelta delta) {
        RPCPacketDistributor.rpcToPlayer(player, RADAR, delta);
    }

    public static void ores(ServerPlayer player, OreSurveyDelta delta) {
        RPCPacketDistributor.rpcToPlayer(player, ORES, delta);
    }

    @RPCPacket(REQUEST)
    public static void request(RPCSender sender, int operation, int slot, int argument) {
        ServerPlayer player = sender.asPlayer();
        if (player == null) return;
        PlatformRuntime runtime = WeaponPlatformTicker.runtime(player);
        if (runtime == null) return;
        if (operation == INPUT) {
            if (argument < 0 || argument > 63) return;
            var motion = player.getData(DEAttachments.PLATFORM_MOTION);
            motion.input = argument;
            motion.inputAt = player.tickCount;
            return;
        }
        if (operation == OPEN) {
            WeaponPlatformMenu.open(player);
            return;
        }
        if (operation == LOCK) {
            switch (argument) {
                case LOCK_TOGGLE -> runtime.toggleLock();
                case LOCK_SWEEP_START, LOCK_SWEEP_REMOVE -> {
                    runtime.sweepingLocks = player.containerMenu == player.inventoryMenu;
                    runtime.removingLocks = argument == LOCK_SWEEP_REMOVE;
                }
                case LOCK_SWEEP_END -> runtime.sweepingLocks = false;
                default -> {
                    return;
                }
            }
        } else {
            if (!WeaponPlatformMenu.isOpen(player) && !(operation == ABILITY && argument == PlatformAbility.PROSPECTING.ordinal())) return;
            if (operation == ABILITY || operation == SHIELD_COLOR || operation == SCAN_RADIUS) {
                var settings = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT);
                if (operation == ABILITY) {
                    if (argument < 0 || argument >= PlatformAbility.values().length) return;
                    PlatformAbility ability = PlatformAbility.values()[argument];
                    if (ability == PlatformAbility.NOCLIP && settings.enabled(ability) && !player.level().noCollision(player, player.getBoundingBox().deflate(0.01))) return;
                    if (!ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(ability.research)) return;
                    settings = settings.toBuilder().enabled(settings.enabled() ^ ability.flag).build();
                } else if (operation == SHIELD_COLOR) settings = settings.toBuilder().shieldColor(argument).build();
                else {
                    if (argument < 16 || argument > 384) return;
                    settings = settings.toBuilder().scanRadius(argument).build();
                }
                runtime.platform.set(DEDataComponents.PLATFORM_ABILITIES, settings);
                push(player, PlatformSnapshot.capture(runtime));
                return;
            }
            if (operation == REBOOT) {
                RadixReboot.activate(player);
                push(player, PlatformSnapshot.capture(runtime));
                return;
            }
            if (operation == PROTECTION) runtime.platform.set(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, !runtime.platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
            else if (operation == ATTACK) runtime.platform.set(DEDataComponents.PLATFORM_ATTACK_ENABLED, !runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ATTACK_ENABLED, true));
            else if (operation == HUD) runtime.platform.set(DEDataComponents.PLATFORM_HUD_ENABLED, !runtime.platform.getOrDefault(DEDataComponents.PLATFORM_HUD_ENABLED, true));
            else if (operation == TARGETS) {
                if (argument < 1 || argument > 16 || Integer.bitCount(argument) != 1) return;
                runtime.platform.set(DEDataComponents.PLATFORM_TARGETS, runtime.platform.getOrDefault(DEDataComponents.PLATFORM_TARGETS, GunSettings.HOSTILE) ^ argument);
            } else {
                if (slot < 0 || slot >= runtime.inventory.capacity()) return;
                var gun = runtime.inventory.getStackInSlot(slot);
                if (gun.isEmpty()) return;
                GunSettings settings = gun.getOrDefault(DEDataComponents.GUN_SETTINGS, GunSettings.DEFAULT);
                GunForm form = GunForm.of(gun);
                GunSettings updated;
                switch (operation) {
                    case ENABLED -> updated = settings.toBuilder().enabled(!settings.enabled()).build();
                    case AMMUNITION -> {
                        if (form == GunForm.MEDICAL || argument < 1 || argument > 4) return;
                        GunAmmunition ammunition = GunAmmunition.values()[argument];
                        updated = settings.toBuilder().ammunition(ammunition == settings.ammunition() ? GunAmmunition.AE : ammunition).build();
                    }
                    case SELF -> {
                        if (form != GunForm.MEDICAL) return;
                        updated = settings.toBuilder().supportSelf(!settings.supportSelf()).build();
                    }
                    case TEAM -> {
                        if (form != GunForm.MEDICAL) return;
                        updated = settings.toBuilder().supportTeam(!settings.supportTeam()).build();
                    }
                    case GUN_INHERIT_TARGETS -> {
                        if (form == GunForm.MEDICAL) return;
                        updated = settings.toBuilder().inheritTargets(!settings.inheritTargets())
                                .targetMask(settings.inheritTargets() ? runtime.platform.getOrDefault(DEDataComponents.PLATFORM_TARGETS, GunSettings.HOSTILE) : settings.targetMask()).build();
                    }
                    case GUN_TARGETS -> {
                        if (form == GunForm.MEDICAL || settings.inheritTargets() || argument < 1 || argument > 16 || Integer.bitCount(argument) != 1) return;
                        updated = settings.toBuilder().targetMask(settings.targetMask() ^ argument).build();
                    }
                    default -> {
                        return;
                    }
                }
                gun.set(DEDataComponents.GUN_SETTINGS, updated);
                runtime.inventory.setStackInSlot(slot, gun);
            }
        }
        push(player, PlatformSnapshot.capture(runtime));
    }
}
