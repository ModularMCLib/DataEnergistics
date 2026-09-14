package com.fish_dan_.data_energistics.client.widget.ioport;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Tooltip;
import appeng.client.gui.style.Blitter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;

import it.unimi.dsi.fastutil.objects.ObjectList;

/** One continuous column for all nine IO port upgrades. */
public final class DataIoPortUpgradePanel implements ICompositeWidget {

    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private final ObjectList<Slot> slots;
    private final IUpgradeableObject host;
    private final Blitter background = Blitter.texture("guis/extra_panels.png", 128, 128);
    private final Rect2i bounds;

    public DataIoPortUpgradePanel(ObjectList<Slot> slots, IUpgradeableObject host) {
        this.slots = slots;
        this.host = host;
        this.bounds = new Rect2i(0, 0, SLOT_SIZE + 2 * PADDING, slots.size() * SLOT_SIZE + 2 * PADDING + 2);
    }

    @Override
    public void setPosition(Point position) {
        bounds.setX(position.getX());
        bounds.setY(position.getY());
    }

    @Override
    public void setSize(int width, int height) {
        bounds.setWidth(Math.max(SLOT_SIZE + 2 * PADDING, width));
        bounds.setHeight(Math.max(slots.size() * SLOT_SIZE + 2 * PADDING + 2, height));
    }

    @Override
    public Rect2i getBounds() {
        return bounds;
    }

    @Override
    public void updateBeforeRender() {
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            slot.x = bounds.getX() + PADDING + 1;
            slot.y = bounds.getY() + PADDING + 1 + index * SLOT_SIZE;
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i screenBounds, Point mouse) {
        int x = screenBounds.getX() + bounds.getX();
        int y = screenBounds.getY() + bounds.getY();
        for (int index = 0; index < slots.size(); index++) {
            boolean first = index == 0;
            int top = first ? 0 : PADDING;
            int height = SLOT_SIZE + (first ? PADDING : 0) + (index == slots.size() - 1 ? PADDING + 2 : 0);
            background.src(0, top, SLOT_SIZE + 2 * PADDING, height)
                    .dest(x, y + index * SLOT_SIZE + (first ? 0 : PADDING)).blit(graphics);
        }
    }

    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        return new Tooltip(Upgrades.getTooltipLinesForMachine(host.getUpgrades().getUpgradableItem()));
    }
}
