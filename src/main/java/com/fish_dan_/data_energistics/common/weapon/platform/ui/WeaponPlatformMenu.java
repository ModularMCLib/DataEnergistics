package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.WeaponPlatformTicker;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WeaponPlatformMenu {

    private static final ResourceLocation ID = Data_Energistics.id("weapon_platform");

    private WeaponPlatformMenu() {}

    public static void register() {
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(PlatformSnapshot.class)
                .codec(PlatformSnapshot.CODEC).streamCodec(PlatformSnapshot.STREAM_CODEC).build(), 100);
        PlatformRpc.register();
        PlayerUIMenuType.register(ID, Holder::new);
    }

    public static void open(ServerPlayer player) {
        if (!EquippedWeaponPlatform.find(player).isEmpty() && player.isAlive() && !player.isSpectator()) PlayerUIMenuType.openUI(player, ID);
    }

    public static boolean isOpen(ServerPlayer player) {
        return player.containerMenu instanceof ModularUIContainerMenu menu && menu.uiHolder instanceof Holder;
    }

    private static final class Holder implements PlayerUIMenuType.PlayerUIHolder {

        private final ItemStack platform;
        private PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;
        private long captured = Long.MIN_VALUE;

        private Holder(Player player) {
            platform = EquippedWeaponPlatform.find(player);
        }

        @Override
        public ModularUI createUI(Player player) {
            return WeaponPlatformUi.create(player, platform, () -> {
                if (!(player instanceof ServerPlayer serverPlayer)) return PlatformSnapshot.EMPTY;
                long now = player.level().getGameTime();
                if (now / 5 != captured / 5) {
                    var runtime = WeaponPlatformTicker.runtime(serverPlayer);
                    snapshot = runtime == null ? PlatformSnapshot.EMPTY : PlatformSnapshot.capture(runtime);
                    captured = now;
                }
                return snapshot;
            });
        }

        @Override
        public boolean isStillValid(Player player) {
            return player.isAlive() && !player.isSpectator() && (player.level().isClientSide ? !EquippedWeaponPlatform.find(player).isEmpty() : EquippedWeaponPlatform.find(player) == platform);
        }
    }
}
