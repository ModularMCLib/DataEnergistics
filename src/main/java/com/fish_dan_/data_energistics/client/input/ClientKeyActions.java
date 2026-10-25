package com.fish_dan_.data_energistics.client.input;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmPresentationState;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudClientState;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.TacticalHudInteractionScreen;
import com.fish_dan_.data_energistics.client.input.cannon.CannonChargeInput;
import com.fish_dan_.data_energistics.client.input.weapon_platform.PlatformLockInput;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.screen.crafting.CraftingPlanTreeScreen;
import com.fish_dan_.data_energistics.client.screen.powered.MatterConvergingCrossbowConfigScreen;
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMappings;

import net.minecraft.client.gui.screens.Screen;

/** Installs client presentation and screen callbacks once, beside the server key actions. */
public final class ClientKeyActions {

    private ClientKeyActions() {}

    public static void register() {
        SyncedKeyMappings.TACTICAL_HUD.registerClientListener((client, key, down) -> {
            if (down && PlatformClientState.snapshot.hud()) client.setScreen(new TacticalHudInteractionScreen());
        }).registerScreenListener((screen, key, x, y) -> {
            if (!(screen instanceof TacticalHudInteractionScreen)) return false;
            screen.onClose();
            return true;
        });
        SyncedKeyMappings.ORBITAL_HUD.registerClientListener((client, key, down) -> {
            if (down) OrbitalControlHudClientState.toggleUserEnabled();
        });
        SyncedKeyMappings.ORBITAL.registerClientListener((client, key, down) -> {
            if (down) OrbitalMapSelectionClientSession.cancel();
        });
        SyncedKeyMappings.RALLY.captureInputWhen(client -> client.player != null && client.player.isAlive() && !client.player.isSpectator() && PlatformClientState.snapshot.present());
        SyncedKeyMappings.TARGET_LOCK.registerClientListener(PlatformLockInput::changed).registerClientTickListener(PlatformLockInput::tick);
        SyncedKeyMappings.CROSSBOW_RAIL.registerClientListener((client, key, down) -> {
            if (down) CannonChargeInput.cancel();
        }).registerScreenListener((screen, key, x, y) -> crossbow(screen, MatterConvergingCrossbowMode.RAIL));
        SyncedKeyMappings.CROSSBOW_ARMS.registerClientListener((client, key, down) -> {
            if (down) CannonChargeInput.cancel();
        }).registerScreenListener((screen, key, x, y) -> crossbow(screen, MatterConvergingCrossbowMode.CROSSBOW));
        SyncedKeyMappings.PAGE_PREV.registerScreenListener((screen, key, x, y) -> page(screen, -1));
        SyncedKeyMappings.PAGE_NEXT.registerScreenListener((screen, key, x, y) -> page(screen, 1));
        SyncedKeyMappings.TERMINAL_MOVE.registerScreenListener((screen, key, x, y) -> TerminalOverlayClient.moveToMouse(screen, x, y));
        SyncedKeyMappings.PROVIDER.registerScreenListener((screen, key, x, y) -> provider(screen, x, y, false));
        SyncedKeyMappings.PROVIDER_RENAME.registerScreenListener((screen, key, x, y) -> provider(screen, x, y, true));
    }

    private static boolean crossbow(Screen screen, MatterConvergingCrossbowMode requested) {
        return screen instanceof MatterConvergingCrossbowConfigScreen config && config.selectMode(requested);
    }

    private static boolean page(Screen screen, int direction) {
        if (screen instanceof TrinityCraftConfirmPresentationState confirmation) return confirmation.data_energistics$cycleRelatedPage(direction);
        return screen instanceof CraftingPlanTreeScreen tree && tree.cycleRelatedPage(direction);
    }

    private static boolean provider(Screen screen, double x, double y, boolean rename) {
        return TerminalOverlayClient.providerShortcut(screen, x, y, rename) || PatternEncodingUploadClient.providerShortcut(screen, x, y, rename);
    }
}
