package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.crafting.tree.CraftingPlanTreeEntry;
import com.fish_dan_.data_energistics.client.screen.crafting.confirm.TrinityCraftConfirmScreenRouter;
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.client.screen.terminal.Ae2TerminalKeyOverlay;
import com.fish_dan_.data_energistics.client.screen.terminal.UniversalTerminalScreenHook;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.client.terminal.rendering.TerminalCarriedItemRenderer;
import com.fish_dan_.data_energistics.client.terminal.rendering.TerminalTooltipRenderer;
import com.fish_dan_.data_energistics.client.ui.orbital.OrbitalControlClientBindings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Data_Energistics.MOD_ID, value = Dist.CLIENT)
public final class ClientScreenEvents {

    private ClientScreenEvents() {}

    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        PatternEncodingUploadClient.init(event);
        CraftingPlanTreeEntry.onInit(event);
        UniversalTerminalScreenHook.onScreenInitPost(event);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        OrbitalControlClientBindings.onScreenOpening(Minecraft.getInstance().screen);
        Screen replacement = TrinityCraftConfirmScreenRouter.routeOpeningScreen(event.getCurrentScreen());
        if (replacement != null) {
            event.setNewScreen(replacement);
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        TerminalOverlayClient.closing(event);
        PatternEncodingUploadClient.closing(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeScreenRender(ScreenEvent.Render.Pre event) {
        TerminalOverlayClient.beginRender(event);
        TerminalCarriedItemRenderer.beginRender(event);
        TerminalTooltipRenderer.beginRender(event);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        UniversalTerminalScreenHook.onScreenRenderPost(event);
        Ae2TerminalKeyOverlay.onScreenRenderPost(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderTerminalForeground(ScreenEvent.Render.Post event) {
        TerminalOverlayClient.render(event);
        TerminalCarriedItemRenderer.render(event);
        TerminalTooltipRenderer.render(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        UniversalTerminalScreenHook.onContainerForeground(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        TerminalOverlayClient.tooltip(event);
        if (!event.isCanceled()) {
            PatternEncodingUploadClient.tooltip(event);
        }
        if (!event.isCanceled() && !UniversalTerminalScreenHook.isRenderingSelectorTooltip()) {
            UniversalTerminalScreenHook.onRenderTooltip(event);
        }
    }
}
