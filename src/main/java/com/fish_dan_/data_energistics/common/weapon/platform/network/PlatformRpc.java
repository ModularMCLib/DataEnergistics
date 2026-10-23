package com.fish_dan_.data_energistics.common.weapon.platform.network;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.WeaponPlatformTicker;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformMenu;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketHandler;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;

import net.minecraft.server.level.ServerPlayer;

/** LDLib2 RPC boundary. Settings are accepted only for the equipped platform and an unlocked control surface. */
public final class PlatformRpc {

    public static final String REQUEST = "data_energistics:weapon_platform_request";
    public static final String SNAPSHOT = "data_energistics:weapon_platform_snapshot";
    public static final int OPEN = 0, LOCK = 1, ENABLED = 2, AMMUNITION = 3, TARGETS = 4, SELF = 5, TEAM = 6, PROTECTION = 7;

    private PlatformRpc() {}

    public static void register() {
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

    @RPCPacket(REQUEST)
    public static void request(RPCSender sender, int operation, int slot, int argument) {
        ServerPlayer player = sender.asPlayer();
        if (player == null) return;
        PlatformRuntime runtime = WeaponPlatformTicker.runtime(player);
        if (runtime == null) return;
        if (operation == OPEN) {
            WeaponPlatformMenu.open(player);
            return;
        }
        if (operation == LOCK) runtime.toggleLock();
        else {
            boolean hud = ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(BiochipResearch.HUD);
            if (!hud && !WeaponPlatformMenu.isOpen(player)) return;
            if (operation == PROTECTION) runtime.platform.set(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, !runtime.platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
            else {
                if (slot < 0 || slot >= runtime.inventory.capacity()) return;
                var gun = runtime.inventory.getStackInSlot(slot);
                if (gun.isEmpty()) return;
                GunSettings settings = gun.getOrDefault(DEDataComponents.GUN_SETTINGS, GunSettings.DEFAULT);
                GunForm form = GunForm.of(gun);
                GunSettings updated;
                switch (operation) {
                    case ENABLED -> updated = new GunSettings(!settings.enabled(), settings.ammunition(), settings.targets(), settings.supportSelf(), settings.supportTeam());
                    case AMMUNITION -> {
                        if (form == GunForm.MEDICAL || argument < 1 || argument > 4 || argument > 2 && !ItemResearchUpgrades.of(gun).ids().contains(BiochipResearch.AMMUNITION)) return;
                        GunAmmunition ammunition = GunAmmunition.values()[argument];
                        updated = new GunSettings(settings.enabled(), ammunition == settings.ammunition() ? GunAmmunition.AE : ammunition, settings.targets(), settings.supportSelf(), settings.supportTeam());
                    }
                    case TARGETS -> {
                        if (form == GunForm.MEDICAL || argument < 1 || argument > 16 || Integer.bitCount(argument) != 1) return;
                        updated = new GunSettings(settings.enabled(), settings.ammunition(), settings.targets() ^ argument, settings.supportSelf(), settings.supportTeam());
                    }
                    case SELF -> {
                        if (form != GunForm.MEDICAL) return;
                        updated = new GunSettings(settings.enabled(), settings.ammunition(), settings.targets(), !settings.supportSelf(), settings.supportTeam());
                    }
                    case TEAM -> {
                        if (form != GunForm.MEDICAL) return;
                        updated = new GunSettings(settings.enabled(), settings.ammunition(), settings.targets(), settings.supportSelf(), !settings.supportTeam());
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
