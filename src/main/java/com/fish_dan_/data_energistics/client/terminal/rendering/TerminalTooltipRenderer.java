package com.fish_dan_.data_energistics.client.terminal.rendering;

import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Retains accepted native tooltips until the terminal windows and carried item have finished drawing. */
@NullMarked
public final class TerminalTooltipRenderer {

    private static @Nullable Frame frame;
    private static @Nullable Draw currentDraw;
    private static @Nullable Pending pending;
    private static RenderTooltipEvent.@Nullable Pre replay;

    private TerminalTooltipRenderer() {}

    /** Runs on the client render thread; the native draw is retained with its original screen transform. */
    public static void capture(GuiGraphics graphics, Runnable nativeRender) {
        Draw previous = currentDraw;
        Frame target = frame;
        currentDraw = target != null && target.graphics() == graphics ? new Draw(graphics.pose().last().copy(), nativeRender) : null;
        try {
            nativeRender.run();
        } finally {
            currentDraw = previous;
        }
    }

    /** Completes native visibility checks once, deferring only the tooltip that those checks accepted. */
    public static RenderTooltipEvent.Pre prepare(Supplier<RenderTooltipEvent.Pre> nativeEvent) {
        RenderTooltipEvent.Pre accepted = replay;
        if (accepted != null) {
            replay = null;
            return accepted;
        }
        RenderTooltipEvent.Pre event = nativeEvent.get();
        Draw draw = currentDraw;
        if (draw != null && !event.isCanceled()) {
            pending = new Pending(draw, event);
            event.setCanceled(true);
        }
        return event;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginRender(ScreenEvent.Render.Pre event) {
        pending = null;
        frame = TerminalOverlayClient.exclusionAreas(event.getScreen()).isEmpty() && PatternEncodingUploadClient.exclusions(event.getScreen()).isEmpty() ?
                null : new Frame(event.getScreen(), event.getGuiGraphics());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(ScreenEvent.Render.Post event) {
        Frame target = frame;
        Pending tooltip = pending;
        frame = null;
        pending = null;
        if (target == null || tooltip == null || target.screen() != event.getScreen()) return;
        GuiGraphics graphics = target.graphics();
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        replay = tooltip.event();
        replay.setCanceled(false);
        try {
            pose.last().pose().set(tooltip.draw().pose().pose());
            pose.last().normal().set(tooltip.draw().pose().normal());
            // Native tooltips add another 400; use one foreground depth regardless of the owning window.
            pose.last().pose().m32(1600);
            tooltip.draw().render().run();
            graphics.flush();
        } finally {
            replay = null;
            pose.popPose();
        }
    }

    private record Frame(Screen screen, GuiGraphics graphics) {}

    private record Draw(PoseStack.Pose pose, Runnable render) {}

    private record Pending(Draw draw, RenderTooltipEvent.Pre event) {}
}
