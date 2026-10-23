package com.fish_dan_.data_energistics.client.event;

import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudClientState;
import com.fish_dan_.data_energistics.client.input.cannon.CannonChargeInput;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.common.item.connector.RemoteLinkConnectorItem;
import com.fish_dan_.data_energistics.common.item.depot.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.common.item.vacuum.MeVacuumItem;
import com.fish_dan_.data_energistics.common.network.packets.action.ConnectorClipboardAction;
import com.fish_dan_.data_energistics.common.network.packets.action.ConnectorClipboardPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.ConnectorScrollPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.DigitalStorageDepotBucketModePayload;
import com.fish_dan_.data_energistics.common.network.packets.action.DigitalStorageDepotScrollPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.MatterConvergingCrossbowModePayload;
import com.fish_dan_.data_energistics.common.network.packets.action.MeVacuumLaunchPayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.control.OrbitalControlOpenPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

final class ClientInputHandler {

    private ClientInputHandler() {}

    static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        if (event.isAttack() && CannonChargeInput.cannonHand(minecraft.player) != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isAttack() || !tryLaunchMeVacuum(minecraft)) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
    }

    static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyAttack.matchesMouse(event.getButton())) {
            return;
        }

        if (tryLaunchMeVacuum(minecraft)) {
            event.setCanceled(true);
        }
    }

    static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (tryConnectorClipboardShortcut(minecraft, event.getKey())) {
            return;
        }
        if (minecraft.options.keyAttack.matches(event.getKey(), event.getScanCode())) {
            tryLaunchMeVacuum(minecraft);
        }
    }

    private static boolean tryConnectorClipboardShortcut(Minecraft minecraft, int key) {
        if (!Screen.hasControlDown() || minecraft.player == null || minecraft.screen != null) {
            return false;
        }
        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();
        boolean offHandConnector = !RemoteLinkConnectorItem.isConnectorStack(mainHand) && RemoteLinkConnectorItem.isConnectorStack(offHand);
        if (!RemoteLinkConnectorItem.isConnectorStack(mainHand) && !RemoteLinkConnectorItem.isConnectorStack(offHand)) {
            return false;
        }
        ConnectorClipboardAction operation = switch (key) {
            case GLFW.GLFW_KEY_A -> ConnectorClipboardAction.SELECT_ALL;
            case GLFW.GLFW_KEY_C -> ConnectorClipboardAction.COPY;
            case GLFW.GLFW_KEY_X -> ConnectorClipboardAction.CUT;
            case GLFW.GLFW_KEY_V -> ConnectorClipboardAction.PASTE;
            default -> null;
        };
        if (operation == null) {
            return false;
        }
        PacketDistributor.sendToServer(new ConnectorClipboardPayload(operation, offHandConnector));
        return true;
    }

    private static boolean tryLaunchMeVacuum(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null) {
            return false;
        }

        if (!minecraft.player.isUsingItem() || minecraft.player.isShiftKeyDown()) {
            return false;
        }

        ItemStack stack = minecraft.player.getUseItem();
        if (!(stack.getItem() instanceof MeVacuumItem)) {
            return false;
        }

        InteractionHand usedHand = minecraft.player.getUsedItemHand();
        PacketDistributor.sendToServer(new MeVacuumLaunchPayload(usedHand == InteractionHand.OFF_HAND));
        return true;
    }

    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        boolean controlDown = Screen.hasControlDown();
        boolean altDown = Screen.hasAltDown();
        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();
        boolean useConnectorMain = RemoteLinkConnectorItem.isConnectorStack(mainHand);
        boolean useConnectorOff = !useConnectorMain && RemoteLinkConnectorItem.isConnectorStack(offHand);
        if (useConnectorMain || useConnectorOff) {
            double delta = event.getScrollDeltaY();
            if (delta != 0 && (controlDown || Screen.hasShiftDown())) {
                PacketDistributor.sendToServer(new ConnectorScrollPayload(
                        delta < 0, useConnectorOff, controlDown, Screen.hasShiftDown()));
                event.setCanceled(true);
            }
            return;
        }
        if (controlDown == altDown) {
            return;
        }
        boolean useMainHand = DigitalStorageDepotBlockItem.isDepotStack(mainHand);
        boolean useOffHand = !useMainHand && DigitalStorageDepotBlockItem.isDepotStack(offHand);
        if (!useMainHand && !useOffHand) {
            return;
        }

        double delta = event.getScrollDeltaY();
        if (delta == 0) {
            return;
        }

        PacketDistributor.sendToServer(new DigitalStorageDepotScrollPayload(delta < 0, useOffHand, altDown));
        event.setCanceled(true);
    }

    static void toggleDepotBucketMode(Minecraft minecraft) {
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }

        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();
        boolean useMainHand = DigitalStorageDepotBlockItem.isDepotStack(mainHand);
        boolean useOffHand = !useMainHand && DigitalStorageDepotBlockItem.isDepotStack(offHand);
        if (!useMainHand && !useOffHand) {
            return;
        }

        PacketDistributor.sendToServer(new DigitalStorageDepotBucketModePayload(useOffHand));
    }

    static boolean consumeToggleDepotBucketModeClick() {
        return DEKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE.consumeClick();
    }

    static boolean consumeOpenOrbitalControlClick() {
        return DEKeyMappings.OPEN_ORBITAL_CONTROL.consumeClick();
    }

    static void requestOrbitalControl(Minecraft minecraft) {
        if (minecraft.screen == null && minecraft.player != null) {
            OrbitalMapSelectionClientSession.cancel();
            PacketDistributor.sendToServer(OrbitalControlOpenPayload.INSTANCE);
        }
    }

    static boolean consumeToggleOrbitalHudClick() {
        return DEKeyMappings.TOGGLE_ORBITAL_CONTROL_HUD.consumeClick();
    }

    static void toggleOrbitalHud() {
        OrbitalControlHudClientState.toggleUserEnabled();
    }

    static void handleCrossbowModeKeys(Minecraft minecraft) {
        if (minecraft.screen != null || minecraft.player == null) return;
        boolean rail = DEKeyMappings.TOGGLE_CROSSBOW_RAIL.consumeClick();
        boolean arms = DEKeyMappings.TOGGLE_CROSSBOW_ARMS.consumeClick();
        if (!rail && !arms) return;
        ItemStack stack = minecraft.player.getMainHandItem();
        boolean offHand = false;
        if (!(stack.getItem() instanceof MatterConvergingCrossbowItem)) {
            stack = minecraft.player.getOffhandItem();
            offHand = true;
        }
        if (!(stack.getItem() instanceof MatterConvergingCrossbowItem)) return;
        MatterConvergingCrossbowMode requested = arms ? MatterConvergingCrossbowMode.CROSSBOW : MatterConvergingCrossbowMode.RAIL;
        MatterConvergingCrossbowMode current = MatterConvergingCrossbowItem.mode(stack);
        MatterConvergingCrossbowMode mode = current == requested ? MatterConvergingCrossbowMode.GRENADE : requested;
        if (!MatterConvergingCrossbowItem.isModeUnlocked(stack, mode)) return;
        CannonChargeInput.cancel();
        PacketDistributor.sendToServer(new MatterConvergingCrossbowModePayload(offHand, mode));
    }
}
