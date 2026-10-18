package com.fish_dan_.data_energistics.gui.ldlib2.terminal.window;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.client.renderer.Rect2i;

import org.jspecify.annotations.NullMarked;

/** One child's percentage placement and shared click-to-close, hold-to-drag handle, owned by the client UI. */
@NullMarked
public final class TerminalSubWindow {

    private final UIElement root;
    private final TerminalActionButton handle;
    private final Runnable close;
    private int screenWidth;
    private int screenHeight;
    private int minimumX;
    private int minimumY;
    private int maximumX;
    private int maximumY;
    private int x;
    private int y;
    private float xPercent;
    private float yPercent;
    private boolean positioned;
    private boolean pressed;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private double pointerX;
    private double pointerY;
    private int originX;
    private int originY;

    public TerminalSubWindow(UIElement root, Runnable close) {
        this.root = root;
        this.close = close;
        handle = TerminalUiLayout.require(root, "close", TerminalActionButton.class);
    }

    public void place(int screenWidth, int screenHeight, Rect2i preferred, int leftMargin, int topMargin) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        maximumX = Math.max(0, screenWidth - preferred.getWidth());
        maximumY = Math.max(0, screenHeight - preferred.getHeight());
        minimumX = Math.min(leftMargin, maximumX);
        minimumY = Math.min(topMargin, maximumY);
        x = positioned ? minimumX + Math.round(xPercent * (maximumX - minimumX)) : Math.clamp(preferred.getX(), minimumX, maximumX);
        y = positioned ? minimumY + Math.round(yPercent * (maximumY - minimumY)) : Math.clamp(preferred.getY(), minimumY, maximumY);
        applyPosition();
    }

    public boolean beginDrag(double mouseX, double mouseY, int button) {
        if (button != 0 || !overHandle(mouseX, mouseY)) return false;
        pressed = true;
        dragging = false;
        pressX = pointerX = mouseX;
        pressY = pointerY = mouseY;
        originX = x;
        originY = y;
        handle.press();
        return true;
    }

    public boolean drag(double mouseX, double mouseY) {
        if (!pressed) return false;
        pointerX = mouseX;
        pointerY = mouseY;
        if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) < 3) return true;
        dragging = true;
        positioned = true;
        x = Math.clamp(originX + (int) (mouseX - pressX), minimumX, maximumX);
        y = Math.clamp(originY + (int) (mouseY - pressY), minimumY, maximumY);
        xPercent = maximumX == minimumX ? 0 : (float) (x - minimumX) / (maximumX - minimumX);
        yPercent = maximumY == minimumY ? 0 : (float) (y - minimumY) / (maximumY - minimumY);
        applyPosition();
        return true;
    }

    public boolean endDrag() {
        if (!pressed) return false;
        boolean clicked = !dragging && overHandle(pointerX, pointerY);
        cancelDrag();
        if (clicked) close.run();
        return true;
    }

    public void cancelDrag() {
        pressed = false;
        dragging = false;
        handle.resetInteraction();
    }

    public boolean dragging() {
        return dragging;
    }

    private void applyPosition() {
        root.layout(layout -> layout.leftPercent(screenWidth == 0 ? 0 : x * 100F / screenWidth)
                .topPercent(screenHeight == 0 ? 0 : y * 100F / screenHeight));
    }

    private boolean overHandle(double mouseX, double mouseY) {
        return root.isVisible() && mouseX >= handle.getPositionX() && mouseY >= handle.getPositionY() &&
                mouseX < handle.getPositionX() + handle.getSizeWidth() && mouseY < handle.getPositionY() + handle.getSizeHeight();
    }
}
