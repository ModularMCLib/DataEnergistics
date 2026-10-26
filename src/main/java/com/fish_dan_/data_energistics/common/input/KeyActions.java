package com.fish_dan_.data_energistics.common.input;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.item.depot.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.common.item.powered.cannon.rail.RailLauncher;
import com.fish_dan_.data_energistics.common.orbital.control.ui.OrbitalControlPlayerMenu;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.util.input.KeyPressedListener;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMapping;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMappingEvent;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMappings;

import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative actions registered against the synchronized keys, not client polling. */
public final class KeyActions {

    // Built-in actions live for the mod lifetime; the input registry holds listeners weakly.
    private static final KeyPressedListener OPEN_PLATFORM = pressed((player, key, down) -> platform(player, PlatformRpc.OPEN, 0));
    private static final KeyPressedListener RALLY = pressed((player, key, down) -> platform(player, PlatformRpc.RALLY, 0));
    private static final KeyPressedListener PROSPECTING = pressed((player, key, down) -> platform(player, PlatformRpc.ABILITY, PlatformAbility.PROSPECTING.ordinal()));
    private static final KeyPressedListener DOMAIN = pressed((player, key, down) -> platform(player, PlatformRpc.DOMAIN, 0));
    private static final KeyPressedListener OPEN_ORBITAL = pressed((player, key, down) -> OrbitalControlPlayerMenu.open(player));
    private static final KeyPressedListener DEPOT_BUCKET = pressed(KeyActions::bucket);
    private static final KeyPressedListener CROSSBOW_RAIL = pressed((player, key, down) -> crossbow(player, MatterConvergingCrossbowMode.RAIL));
    private static final KeyPressedListener CROSSBOW_ARMS = pressed((player, key, down) -> crossbow(player, MatterConvergingCrossbowMode.CROSSBOW));

    private KeyActions() {}

    public static void register(SyncedKeyMappingEvent event) {
        SyncedKeyMappings.PLATFORM.registerGlobalListener(OPEN_PLATFORM);
        SyncedKeyMappings.RALLY.registerGlobalListener(RALLY);
        SyncedKeyMappings.PROSPECTING.registerGlobalListener(PROSPECTING);
        SyncedKeyMappings.DOMAIN.registerGlobalListener(DOMAIN);
        SyncedKeyMappings.ORBITAL.registerGlobalListener(OPEN_ORBITAL);
        SyncedKeyMappings.DEPOT_BUCKET.registerGlobalListener(DEPOT_BUCKET);
        SyncedKeyMappings.CROSSBOW_RAIL.registerGlobalListener(CROSSBOW_RAIL);
        SyncedKeyMappings.CROSSBOW_ARMS.registerGlobalListener(CROSSBOW_ARMS);
    }

    private static KeyPressedListener pressed(KeyPressedListener action) {
        return (player, key, down) -> {
            if (down && player.isAlive() && !player.isSpectator() && player.containerMenu == player.inventoryMenu) action.onKeyPressed(player, key, true);
        };
    }

    private static void platform(ServerPlayer player, int operation, int argument) {
        PlatformRpc.request(RPCSender.ofClient(player), operation, 0, argument);
    }

    private static void bucket(ServerPlayer player, SyncedKeyMapping key, boolean down) {
        InteractionHand hand = DigitalStorageDepotBlockItem.isDepotStack(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!DigitalStorageDepotBlockItem.isDepotStack(stack)) return;
        ItemStack updated = stack.copy();
        boolean enabled = DigitalStorageDepotBlockItem.toggleBucketMode(updated);
        player.setItemInHand(hand, updated);
        player.displayClientMessage(Component.translatable(enabled ?
                "message.data_energistics.digital_storage_depot.bucket_mode_on" :
                "message.data_energistics.digital_storage_depot.bucket_mode_off"), true);
    }

    private static void crossbow(ServerPlayer player, MatterConvergingCrossbowMode requested) {
        InteractionHand hand = player.getMainHandItem().getItem() instanceof MatterConvergingCrossbowItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof MatterConvergingCrossbowItem)) return;
        MatterConvergingCrossbowMode mode = MatterConvergingCrossbowItem.mode(stack) == requested ? MatterConvergingCrossbowMode.GRENADE : requested;
        if (!MatterConvergingCrossbowItem.isModeUnlocked(stack, mode)) return;
        RailLauncher.cancel(stack);
        ItemStack updated = stack.copy();
        updated.remove(DEDataComponents.CANNON_CHARGE.get());
        updated.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), mode.id());
        player.setItemInHand(hand, updated);
    }
}
