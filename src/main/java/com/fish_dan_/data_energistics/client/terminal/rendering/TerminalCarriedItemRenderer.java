package com.fish_dan_.data_energistics.client.terminal.rendering;

import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Defers the native carried-stack draw, retaining its split count, text and screen transform for this frame. */
@NullMarked
public final class TerminalCarriedItemRenderer {

    private static @Nullable Pending pending;

    private TerminalCarriedItemRenderer() {}

    /** Called on the client render thread; returns false when no terminal window needs a foreground cursor. */
    public static boolean defer(Screen screen, GuiGraphics graphics, ItemStack stack, Consumer<ItemStack> nativeRender) {
        if (TerminalOverlayClient.exclusionAreas(screen).isEmpty() && PatternEncodingUploadClient.exclusions(screen).isEmpty()) return false;
        pending = new Pending(screen, graphics.pose().last().copy(),
                () -> nativeRender.accept(TerminalOverlayClient.carriedPreview(screen, stack)));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginRender(ScreenEvent.Render.Pre event) {
        pending = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(ScreenEvent.Render.Post event) {
        Pending draw = pending;
        pending = null;
        if (draw == null || draw.screen() != event.getScreen()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.last().pose().set(draw.pose().pose());
            pose.last().normal().set(draw.pose().normal());
            pose.translate(0, 0, 1600);
            draw.render().run();
            graphics.flush();
        } finally {
            pose.popPose();
        }
    }

    private record Pending(Screen screen, PoseStack.Pose pose, Runnable render) {}
}
