package com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload;

import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.window.TerminalSubWindow;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

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

    private final UIElement popup = TerminalUiLayout.popup("upload");
    private final PatternUploadUi view;
    private final TerminalSubWindow window;
    private final ModularUI ui;
    private final Runnable close;
    private ObjectList<Rect2i> exclusions = ObjectImmutableList.of();
    private Rect2i bounds = new Rect2i(0, 0, PatternUploadUi.MIN_WIDTH, PatternUploadUi.MIN_HEIGHT);
    private boolean visible;
    private int pointerButton = -1;
    private boolean rendering;

    public PatternUploadWindow(Player player, PatternUploadActions actions, Runnable close, Consumer<TerminalSubWindow.Geometry> geometryChanged) {
        this.close = close;
        view = new PatternUploadUi(popup, actions);
        window = new TerminalSubWindow(popup, close);
        window.resizable(PatternUploadUi.MIN_WIDTH, PatternUploadUi.MIN_HEIGHT, view::resize);
        window.onGeometryChanged(geometryChanged);
        popup.addClass("standalone-upload-popup");
        UIElement root = new UIElement();
        root.addClass("terminal-root");
        root.setAllowHitTest(false);
        root.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100).paddingAll(0));
        root.addChild(popup);
        TerminalUiLayout.require(popup, "popup_title", Label.class).setText(Component.translatable("gui.data_energistics.player_terminal.upload"));
        TerminalUiLayout.require(popup, "close", UIElement.class).style(style -> style.tooltips(TerminalTooltipLines.split(
                Component.translatable("screen.data_energistics.pattern_writer_preview.drag_handle"))));
        ui = new ModularUI(UI.of(root, ObjectImmutableList.of(TerminalUiLayout.stylesheet())), player);
    }

    public void show(Screen screen, Rect2i preferred) {
        visible = true;
        popup.setDisplay(true);
        view.fit(screen.width - 8, screen.height - 8);
        preferred = new Rect2i(preferred.getX(), preferred.getY(), view.width(), view.height());
        window.place(screen.width, screen.height, preferred, new Rect2i(4, 4, Math.max(0, screen.width - 8), Math.max(0, screen.height - 8)));
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

    public int width() {
        return view.width();
    }

    public int height() {
        return view.height();
    }

    public void restoreSize(Screen screen, int width, int height) {
        view.resize(Math.clamp(width, PatternUploadUi.MIN_WIDTH, Math.max(PatternUploadUi.MIN_WIDTH, screen.width - 8)),
                Math.clamp(height, PatternUploadUi.MIN_HEIGHT, Math.max(PatternUploadUi.MIN_HEIGHT, screen.height - 8)));
    }

    public boolean contains(double x, double y) {
        return visible && bounds.contains((int) x, (int) y);
    }

    public boolean textInputFocused() {
        if (!visible) return false;
        for (UIElement focused = ui.getFocusedElement(); focused != null; focused = focused.getParent())
            if (focused instanceof TextField) return true;
        return false;
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
        if (window.endDrag()) {
            updateBounds();
            pointerButton = -1;
            return true;
        }
        ui.getWidget().mouseReleased(x, y, button);
        pointerButton = -1;
        return true;
    }

    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (pointerButton != button) return false;
        if (window.drag(x, y)) {
            updateBounds();
            return true;
        }
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
        Rect2i current = window.bounds();
        if (current.getX() == bounds.getX() && current.getY() == bounds.getY() &&
                current.getWidth() == bounds.getWidth() && current.getHeight() == bounds.getHeight() && !exclusions.isEmpty())
            return;
        bounds = current;
        exclusions = ObjectImmutableList.of(bounds);
    }
}
