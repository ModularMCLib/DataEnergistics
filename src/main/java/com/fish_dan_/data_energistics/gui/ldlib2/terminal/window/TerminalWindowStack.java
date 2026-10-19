package com.fish_dan_.data_energistics.gui.ldlib2.terminal.window;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.client.renderer.Rect2i;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiPredicate;

/** Independent floating windows share only their stacking order and the current pointer capture. */
@NullMarked
public final class TerminalWindowStack {

    private final UIElement root;
    private final Object2ObjectMap<String, Window> windows = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<String, String> parents = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectList<String> order = new ObjectArrayList<>();
    private @Nullable TerminalSubWindow pointerWindow;

    public TerminalWindowStack(UIElement root) {
        this.root = root;
    }

    public void add(String name, UIElement element) {
        element.setVisible(false);
        windows.put(name, new Window(element, new TerminalSubWindow(element, () -> close(name))));
        root.addChild(element);
    }

    public UIElement root(String name) {
        return window(name).element;
    }

    public ObjectSet<String> names() {
        return windows.keySet();
    }

    public void extraHitArea(String name, BiPredicate<Double, Double> hitTest) {
        window(name).extraHitArea = hitTest;
    }

    public void place(String name, int width, int height, Rect2i preferred, int leftMargin, int topMargin) {
        window(name).placement.place(width, height, preferred, leftMargin, topMargin);
    }

    public void open(String name, @Nullable String parent) {
        if (parent == null) parents.remove(name);
        else parents.put(name, parent);
        window(name).element.setVisible(true);
        raise(name);
    }

    public boolean contains(double x, double y) {
        return at(x, y) != null;
    }

    public boolean beginDrag(double x, double y, int button) {
        String name = at(x, y);
        if (name == null) return false;
        raise(name);
        TerminalSubWindow placement = window(name).placement;
        if (!placement.beginDrag(x, y, button)) return false;
        pointerWindow = placement;
        return true;
    }

    public boolean drag(double x, double y) {
        return pointerWindow != null && pointerWindow.drag(x, y);
    }

    public boolean endDrag() {
        TerminalSubWindow captured = pointerWindow;
        pointerWindow = null;
        return captured != null && captured.endDrag();
    }

    public void cancelDrag() {
        if (pointerWindow != null) pointerWindow.cancelDrag();
        pointerWindow = null;
    }

    public boolean dragging() {
        return pointerWindow != null && pointerWindow.dragging();
    }

    public boolean closeTop() {
        if (order.isEmpty()) return false;
        close(order.getLast());
        return true;
    }

    public void close(String name) {
        for (String child : new ObjectArrayList<>(order)) {
            if (name.equals(parents.get(child))) close(child);
        }
        Window window = window(name);
        if (window.element.selfAndAllChildren().anyMatch(UIElement::isFocused))
            Objects.requireNonNull(root.getModularUI()).clearFocus();
        window.placement.cancelDrag();
        if (pointerWindow == window.placement) pointerWindow = null;
        window.element.setVisible(false);
        order.remove(name);
        parents.remove(name);
        layers();
    }

    public void closeAll() {
        while (!order.isEmpty()) close(order.getLast());
    }

    public ObjectList<Rect2i> rectangles() {
        ObjectList<Rect2i> areas = new ObjectArrayList<>();
        for (String name : order) areas.add(rect(root(name)));
        return areas;
    }

    /** A viewer drop must never target a cell covered by a different floating window. */
    public boolean covers(Rect2i area, @Nullable String owner) {
        for (int index = order.size() - 1; index >= 0; index--) {
            String name = order.get(index);
            if (name.equals(owner)) return false;
            Rect2i window = rect(root(name));
            if (area.getX() < window.getX() + window.getWidth() && area.getX() + area.getWidth() > window.getX() &&
                    area.getY() < window.getY() + window.getHeight() && area.getY() + area.getHeight() > window.getY())
                return true;
        }
        return false;
    }

    private @Nullable String at(double x, double y) {
        for (int index = order.size() - 1; index >= 0; index--) {
            String name = order.get(index);
            Window window = window(name);
            Rect2i area = rect(window.element);
            if (area.contains((int) x, (int) y) || window.extraHitArea != null && window.extraHitArea.test(x, y)) return name;
        }
        return null;
    }

    private void raise(String name) {
        if (!order.isEmpty() && order.getLast().equals(name)) return;
        order.remove(name);
        order.add(name);
        layers();
    }

    private void layers() {
        for (int index = 0; index < order.size(); index++) {
            int layer = 600 + index;
            root(order.get(index)).style(style -> style.zIndex(layer));
        }
    }

    private Window window(String name) {
        return Objects.requireNonNull(windows.get(name), "Unknown terminal window " + name);
    }

    private static Rect2i rect(UIElement element) {
        return new Rect2i(Math.round(element.getPositionX()), Math.round(element.getPositionY()),
                Math.round(element.getSizeWidth()), Math.round(element.getSizeHeight()));
    }

    private static final class Window {

        private final UIElement element;
        private final TerminalSubWindow placement;
        private @Nullable BiPredicate<Double, Double> extraHitArea;

        private Window(UIElement element, TerminalSubWindow placement) {
            this.element = element;
            this.placement = placement;
        }
    }
}
