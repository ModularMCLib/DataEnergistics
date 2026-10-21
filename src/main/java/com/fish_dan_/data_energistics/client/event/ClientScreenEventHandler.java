package com.fish_dan_.data_energistics.client.event;

import com.fish_dan_.data_energistics.client.crafting.tree.CraftingPlanTreeEntry;
import com.fish_dan_.data_energistics.client.screen.crafting.confirm.TrinityCraftConfirmScreenRouter;
import com.fish_dan_.data_energistics.client.screen.terminal.Ae2TerminalKeyOverlay;
import com.fish_dan_.data_energistics.client.screen.terminal.UniversalTerminalScreenHook;
import com.fish_dan_.data_energistics.client.ui.orbital.OrbitalControlClientBindings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

final class ClientScreenEventHandler {

    private ClientScreenEventHandler() {}

    static void onScreenInitPost(ScreenEvent.Init.Post event) {
        CraftingPlanTreeEntry.onInit(event);
        UniversalTerminalScreenHook.onScreenInitPost(event);
    }

    static void onScreenOpening(ScreenEvent.Opening event) {
        OrbitalControlClientBindings.onScreenOpening(Minecraft.getInstance().screen);
        Screen replacement = TrinityCraftConfirmScreenRouter.routeOpeningScreen(event.getCurrentScreen());
        if (replacement != null) {
            event.setNewScreen(replacement);
        }
    }

    static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        UniversalTerminalScreenHook.onScreenRenderPost(event);
        Ae2TerminalKeyOverlay.onScreenRenderPost(event);
    }

    static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        UniversalTerminalScreenHook.onContainerForeground(event);
    }

    static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (UniversalTerminalScreenHook.isRenderingSelectorTooltip()) {
            return;
        }
        UniversalTerminalScreenHook.onRenderTooltip(event);
    }
}
