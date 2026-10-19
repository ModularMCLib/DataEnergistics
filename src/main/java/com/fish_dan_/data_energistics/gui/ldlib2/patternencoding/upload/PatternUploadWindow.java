package com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.window.TerminalSubWindow;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import appeng.api.stacks.GenericStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Standalone host for the shared upload view, with no dependency on a terminal screen or menu. */
public final class PatternUploadWindow {

    public static final int WIDTH = 190;
    public static final int HEIGHT = 188;
    private final UIElement popup = TerminalUiLayout.popup("upload");
    private final PatternUploadUi view;
    private final TerminalSubWindow window;
    private final ModularUI ui;
    private final Consumer<Rect2i> moved;
    private final Runnable close;
    private ObjectList<Rect2i> exclusions = ObjectImmutableList.of();
    private Rect2i bounds = new Rect2i(0, 0, WIDTH, HEIGHT);
    private boolean visible;
    private int pointerButton = -1;
    private boolean rendering;

    public PatternUploadWindow(Player player, PatternUploadActions actions, Runnable close, Consumer<Rect2i> moved) {
        this.close = close;
        this.moved = moved;
        view = new PatternUploadUi(popup, actions);
        window = new TerminalSubWindow(popup, close);
        popup.addClass("standalone-upload-popup");
        UIElement root = new UIElement();
        root.addClass("terminal-root");
        root.setAllowHitTest(false);
        root.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100).paddingAll(0));
        root.addChild(popup);
        TerminalUiLayout.require(popup, "popup_title", Label.class).setText(Component.translatable("gui.data_energistics.player_terminal.upload"));
        TerminalUiLayout.require(popup, "close", UIElement.class).style(style -> style.tooltips(TerminalTooltipLines.split(
                Component.translatable("gui.data_energistics.player_terminal.close"), Component.translatable("gui.data_energistics.player_terminal.hold_to_drag"))));
        ui = new ModularUI(UI.of(root, ObjectImmutableList.of(TerminalUiLayout.stylesheet())), player);
    }

    public void show(Screen screen, Rect2i preferred) {
        visible = true;
        popup.setDisplay(true);
        window.place(screen.width, screen.height, preferred, 4, 4);
        ui.setScreenAndInit(screen);
        updateBounds();
    }

    public void hide() {
        visible = false;
        pointerButton = -1;
        window.cancelDrag();
        ui.clearFocus();
        popup.setDisplay(false);
    }

    public void dispose() {
        hide();
        ui.onRemoved();
    }

    public void apply(SyncedPatternProviderList providers, @Nullable GenericStack pattern) {
        view.apply(providers, pattern, true);
        view.tick();
    }

    public boolean visible() {
        return visible;
    }

    public boolean contains(double x, double y) {
        return visible && bounds.contains((int) x, (int) y);
    }

    public boolean overHandle(double x, double y) {
        return contains(x, y) && TerminalUiLayout.require(popup, "close", UIElement.class).isIntersectWithPoint(x, y);
    }

    public ObjectList<Rect2i> exclusions() {
        return visible ? exclusions : ObjectImmutableList.of();
    }

    public boolean suppressesTooltip(double x, double y) {
        return !rendering && contains(x, y);
    }

    /** Composites once, after the native screen has finished drawing, in absolute GUI coordinates. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        rendering = true;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 600);
            ui.getWidget().mouseMoved(mouseX, mouseY);
            ui.getWidget().render(graphics, mouseX, mouseY, partialTick);
            updateBounds();
        } finally {
            graphics.pose().popPose();
            rendering = false;
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (!contains(x, y)) {
            ui.clearFocus();
            return false;
        }
        pointerButton = button;
        if (!window.beginDrag(x, y, button)) {
            ui.refreshHoveredElementAtScreen((float) x, (float) y);
            ui.getWidget().mouseClicked(x, y, button);
        }
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (pointerButton != button) return false;
        boolean dragging = window.dragging();
        if (window.endDrag()) {
            if (dragging) moved.accept(bounds);
            pointerButton = -1;
            return true;
        }
        ui.getWidget().mouseReleased(x, y, button);
        pointerButton = -1;
        return true;
    }

    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (pointerButton != button) return false;
        if (window.drag(x, y)) return true;
        ui.getWidget().mouseDragged(x, y, button, dragX, dragY);
        return true;
    }

    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!contains(x, y)) return false;
        ui.refreshHoveredElementAtScreen((float) x, (float) y);
        ui.getWidget().mouseScrolled(x, y, scrollX, scrollY);
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!visible) return false;
        if (ui.getFocusedElement() != null && ui.getWidget().keyPressed(key, scan, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close.run();
            return true;
        }
        return ui.getFocusedElement() != null;
    }

    public boolean charTyped(char character, int modifiers) {
        if (!visible || ui.getFocusedElement() == null) return false;
        ui.getWidget().charTyped(character, modifiers);
        return true;
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        if (!visible || ui.getFocusedElement() == null) return false;
        ui.getWidget().keyReleased(key, scan, modifiers);
        return true;
    }

    private void updateBounds() {
        int x = Math.round(popup.getPositionX());
        int y = Math.round(popup.getPositionY());
        if (x == bounds.getX() && y == bounds.getY() && !exclusions.isEmpty()) return;
        bounds = new Rect2i(x, y, WIDTH, HEIGHT);
        exclusions = ObjectImmutableList.of(bounds);
    }
}
