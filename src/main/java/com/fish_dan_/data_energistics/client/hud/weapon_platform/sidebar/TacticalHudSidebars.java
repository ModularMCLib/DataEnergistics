package com.fish_dan_.data_energistics.client.hud.weapon_platform.sidebar;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.information.TacticalInformationWidget;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.layout.HudComponentSnap;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.layout.HudComponentSnap.Bounds;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.layout.HudViewportPlacement;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.HudSnapPreview;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.weapon.TacticalWeaponRow;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.HudSidebarEntry;
import com.fish_dan_.data_energistics.config.client.hud.tactical.HudSidebarLayout;
import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalHudWidget;
import com.fish_dan_.data_energistics.config.client.hud.tactical.WeaponHudSide;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;

/**
 * Client-thread owner of mixed sidebar order and free information components, using the same entries for preview and
 * display.
 */
public final class TacticalHudSidebars extends UIElement {

    private final EnumMap<WeaponHudSide, TacticalHudSidebar> columns = new EnumMap<>(WeaponHudSide.class);
    private final Int2ObjectLinkedOpenHashMap<TacticalWeaponRow> rows = new Int2ObjectLinkedOpenHashMap<>();
    private final EnumMap<TacticalHudWidget, TacticalInformationWidget> information = new EnumMap<>(TacticalHudWidget.class);
    private final EnumMap<WeaponHudSide, ObjectList<HudSidebarEntry>> visible = new EnumMap<>(WeaponHudSide.class);
    private final HudSnapPreview snapPreview = new HudSnapPreview();
    private HudSidebarLayout layout = HudSidebarLayout.EMPTY;
    private PlatformSnapshot displayed = PlatformSnapshot.EMPTY;
    private long revision = -1, informationSecond = -1;
    private int occupiedMask = -1;
    private int viewportWidth, viewportHeight;
    private boolean interactive;

    public TacticalHudSidebars() {
        setAllowHitTest(false);
        for (WeaponHudSide side : WeaponHudSide.values()) {
            TacticalHudSidebar column = new TacticalHudSidebar(side);
            columns.put(side, column);
            WindowDragHelper.setDragMove(column.getHandle(), column, event -> interactive && event.button == 0, event -> savePosition(column));
            addChild(column);
        }
        for (TacticalHudWidget widget : TacticalHudWidget.values()) {
            if (widget.weapons()) continue;
            TacticalInformationWidget element = new TacticalInformationWidget(widget);
            information.put(widget, element);
            installDrag(element, new HudSidebarEntry.Information(widget), new TextTexture(Component.translatable(widget.translationKey()).getString()));
            addChild(element);
        }
        addChild(snapPreview);
    }

    public void refresh(PlatformSnapshot snapshot, LocalPlayer player, boolean editing, int width, int height) {
        if (width <= 0 || height <= 0) return;
        int mask = 0;
        for (var gun : snapshot.guns()) mask |= 1 << gun.slot();
        boolean settingsChanged = revision != TacticalHudPreferences.getRevision() || !displayed.systems().hud().equals(snapshot.systems().hud());
        boolean changed = mask != occupiedMask || settingsChanged || interactive != editing || displayed.connected() != snapshot.connected() || viewportWidth != width || viewportHeight != height;
        interactive = editing;
        viewportWidth = width;
        viewportHeight = height;
        occupiedMask = mask;
        boolean dataChanged = displayed != snapshot;
        displayed = snapshot;
        if (changed) {
            HudSidebarLayout saved = TacticalHudPreferences.appearance().weapons();
            layout = saved.assign(mask);
            if (layout != saved) TacticalHudPreferences.setWeapons(layout);
            reflow();
        }
        if (changed || dataChanged) {
            var fields = TacticalHudPreferences.get(HudModule.WEAPONS);
            for (WeaponHudSide side : WeaponHudSide.values()) {
                int color = TacticalHudPreferences.get(side.getWidget()).textColor();
                for (HudSidebarEntry entry : visible.get(side)) {
                    if (entry instanceof HudSidebarEntry.Weapon(int slot)) {
                        for (var gun : snapshot.guns()) if (gun.slot() == slot) rows.get(slot).refresh(gun, snapshot.network(), fields, color);
                    }
                }
            }
        }
        long second = System.nanoTime() / 1_000_000_000L;
        if (changed || dataChanged || informationSecond != second) {
            informationSecond = second;
            for (TacticalInformationWidget element : information.values()) element.refresh(snapshot, player.position(), player.clientLevel.getDayTime(),
                    layout.contains(new HudSidebarEntry.Information(element.getWidget())));
        }
        revision = TacticalHudPreferences.getRevision();
    }

    private void reflow() {
        WeaponPlatformStyle.place(this, 0, 0, viewportWidth, viewportHeight);
        WeaponPlatformStyle.place(snapPreview, 0, 0, viewportWidth, viewportHeight);
        for (TacticalHudSidebar column : columns.values()) column.getList().clearAllChildren();
        for (TacticalInformationWidget element : information.values()) removeChild(element);
        rows.int2ObjectEntrySet().removeIf(entry -> (occupiedMask & 1 << entry.getIntKey()) == 0);
        for (var gun : displayed.guns()) rows.computeIfAbsent(gun.slot(), this::createRow);
        for (TacticalHudSidebar column : columns.values()) {
            ObjectList<HudSidebarEntry> entries = layout.visible(column.getSide(), this::visibleEntry);
            visible.put(column.getSide(), entries);
            int contentWidth = TacticalHudSidebar.WIDTH - 24;
            for (HudSidebarEntry entry : entries) {
                if (entry instanceof HudSidebarEntry.Information(TacticalHudWidget widget)) {
                    contentWidth = Math.max(contentWidth, (int) Math.ceil((TacticalHudSidebar.WIDTH - 24) * TacticalHudPreferences.get(widget).scale()));
                }
            }
            int offset = 0;
            for (HudSidebarEntry entry : entries) {
                UIElement element = element(entry);
                int height = entryHeight(entry);
                if (element instanceof TacticalInformationWidget info) {
                    float scale = TacticalHudPreferences.get(info.getWidget()).scale();
                    WeaponPlatformStyle.place(element, 0, offset, TacticalHudSidebar.WIDTH - 24, 28);
                    element.style(value -> value.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
                    info.configure(TacticalHudSidebar.WIDTH - 24, 28);
                } else {
                    ((TacticalWeaponRow) element).arrange(column.getSide());
                    WeaponPlatformStyle.place(element, 0, offset, contentWidth, height);
                    element.style(value -> value.transform2D(Transform2D.identity()));
                }
                element.setDisplay(true);
                element.setAllowHitTest(interactive);
                column.getList().addChild(element);
                offset += height;
            }
            var style = TacticalHudPreferences.get(column.getSide().getWidget());
            int height = column.configure(offset, contentWidth, style, interactive);
            var bounds = HudViewportPlacement.resolve(viewportWidth, viewportHeight, contentWidth + 24, height, style.scale(), style.x(), style.y());
            place(column, bounds);
            column.setDisplay(interactive || style.visible() && !entries.isEmpty());
        }
        for (TacticalInformationWidget element : information.values()) {
            var widget = element.getWidget();
            if (layout.contains(new HudSidebarEntry.Information(widget))) continue;
            var style = TacticalHudPreferences.get(widget);
            element.configure(element.freeWidth(), TacticalInformationWidget.HEIGHT);
            var bounds = HudViewportPlacement.resolve(viewportWidth, viewportHeight, element.freeWidth(), TacticalInformationWidget.HEIGHT, style.scale(), style.x(), style.y());
            place(element, bounds);
            element.setDisplay(visibleEntry(new HudSidebarEntry.Information(widget)));
            element.setAllowHitTest(interactive);
            addChild(element);
        }
    }

    private boolean visibleEntry(HudSidebarEntry entry) {
        return switch (entry) {
            case HudSidebarEntry.Weapon(int slot) -> (occupiedMask & 1 << slot) != 0 && (interactive || TacticalHudPreferences.get(HudModule.WEAPONS).visible());
            case HudSidebarEntry.Information(TacticalHudWidget widget) -> interactive || TacticalHudPreferences.enabled(widget) && (widget != TacticalHudWidget.ENERGY && widget != TacticalHudWidget.FLOW || displayed.connected());
        };
    }

    private TacticalWeaponRow createRow(int slot) {
        TacticalWeaponRow row = new TacticalWeaponRow();
        installDrag(row, new HudSidebarEntry.Weapon(slot), row.getModel());
        return row;
    }

    private void installDrag(UIElement element, HudSidebarEntry entry, IGuiTexture texture) {
        element.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!interactive || event.button != 0) return;
            var local = element.getLocalMouse(event.x, event.y);
            element.startDrag(new ComponentDrag(entry, (local.x - element.getPositionX()) / element.getSizeWidth(),
                    (local.y - element.getPositionY()) / element.getSizeHeight()), texture).setDragTexture(-12, -8, 72, 16);
            event.stopPropagation();
        });
        element.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            if (!(event.dragHandler.draggingObject instanceof ComponentDrag drag)) return;
            TacticalHudSidebar target = nearbyColumn(event);
            for (TacticalHudSidebar column : columns.values()) column.insertion(column == target ? insertionOffset(column, insertion(column, event)) : -1);
            snapPreview.clear();
            if (target == null && entry instanceof HudSidebarEntry.Information(TacticalHudWidget widget)) {
                Bounds moving = draggedInformationBounds(widget, event, drag);
                HudComponentSnap snap = nearbyInformation(widget, moving);
                if (snap != null) snapPreview.show(new Bounds(snap.x(), snap.y(), moving.width(), moving.height()), TacticalHudPreferences.get(widget).lineColor());
            }
        });
        element.addEventListener(UIEvents.DRAG_END, event -> {
            if (!(event.dragHandler.draggingObject instanceof ComponentDrag drag)) return;
            if (event.relatedTarget != null && visibleEntry(entry)) {
                TacticalHudSidebar target = nearbyColumn(event);
                if (target != null) {
                    int index = insertion(target, event);
                    int source = visible.get(target.getSide()).indexOf(entry);
                    if (source >= 0 && source < index) index--;
                    TacticalHudPreferences.setWeapons(layout.move(entry, target.getSide(), index, this::visibleEntry));
                } else if (entry instanceof HudSidebarEntry.Information(TacticalHudWidget widget)) {
                    undock(widget, event, drag);
                }
            }
            for (TacticalHudSidebar column : columns.values()) column.insertion(-1);
            snapPreview.clear();
        });
    }

    private void undock(TacticalHudWidget widget, UIEvent event, ComponentDrag drag) {
        var style = TacticalHudPreferences.get(widget);
        Bounds moving = draggedInformationBounds(widget, event, drag);
        HudComponentSnap snap = nearbyInformation(widget, moving);
        float left = snap == null ? moving.x() : snap.x();
        float top = snap == null ? moving.y() : snap.y();
        double x = left / Math.max(1d, viewportWidth - moving.width());
        double y = top / Math.max(1d, viewportHeight - moving.height());
        TacticalHudPreferences.save(TacticalHudPreferences.appearance().withWeapons(layout.remove(new HudSidebarEntry.Information(widget)))
                .withWidget(widget, style.toBuilder().x(Math.clamp(x, 0, 1)).y(Math.clamp(y, 0, 1)).build()));
    }

    private Bounds draggedInformationBounds(TacticalHudWidget widget, UIEvent event, ComponentDrag drag) {
        var style = TacticalHudPreferences.get(widget);
        var bounds = HudViewportPlacement.resolve(viewportWidth, viewportHeight, information.get(widget).freeWidth(), TacticalInformationWidget.HEIGHT, style.scale(), style.x(), style.y());
        var mouse = getLocalMouse(event.x, event.y);
        float width = bounds.width() * bounds.scale(), height = bounds.height() * bounds.scale();
        return new Bounds(Math.clamp(mouse.x - drag.grabX() * width, 0, Math.max(0, viewportWidth - width)),
                Math.clamp(mouse.y - drag.grabY() * height, 0, Math.max(0, viewportHeight - height)), width, height);
    }

    private @Nullable HudComponentSnap nearbyInformation(TacticalHudWidget widget, Bounds moving) {
        HudComponentSnap nearest = null;
        float viewportScale = Math.min(viewportWidth / 854f, viewportHeight / 480f);
        float gap = 4 * viewportScale;
        for (var entry : information.entrySet()) {
            TacticalHudWidget candidate = entry.getKey();
            TacticalInformationWidget target = entry.getValue();
            if (candidate == widget || !target.isDisplayed() || layout.contains(new HudSidebarEntry.Information(candidate))) continue;
            var top = target.getWorldMouse(target.getPositionX(), target.getPositionY());
            var bottom = target.getWorldMouse(target.getPositionX() + target.getSizeWidth(), target.getPositionY() + target.getSizeHeight());
            top = getLocalMouse(top.x, top.y);
            bottom = getLocalMouse(bottom.x, bottom.y);
            HudComponentSnap snap = HudComponentSnap.nearby(moving, new Bounds(top.x, top.y, bottom.x - top.x, bottom.y - top.y), gap, 24 * viewportScale, viewportWidth, viewportHeight);
            if (snap != null && (nearest == null || snap.distanceSquared() < nearest.distanceSquared())) nearest = snap;
        }
        return nearest;
    }

    private int insertion(TacticalHudSidebar column, UIEvent event) {
        float y = column.getLocalMouse(event.x, event.y).y - column.getPositionY() - TacticalHudSidebar.CONTENT_TOP;
        int offset = 0, index = 0;
        for (HudSidebarEntry entry : visible.get(column.getSide())) {
            int height = entryHeight(entry);
            if (y < offset + height / 2f) break;
            offset += height;
            index++;
        }
        return index;
    }

    private int insertionOffset(TacticalHudSidebar column, int index) {
        int offset = 0;
        ObjectList<HudSidebarEntry> entries = visible.get(column.getSide());
        for (int cursor = 0; cursor < index; cursor++) offset += entryHeight(entries.get(cursor));
        return offset;
    }

    private @Nullable TacticalHudSidebar nearbyColumn(UIEvent event) {
        TacticalHudSidebar nearest = null;
        float nearestDistance = 12 * 12;
        for (TacticalHudSidebar column : columns.values()) {
            var top = column.getWorldMouse(column.getPositionX(), column.getPositionY());
            var bottom = column.getWorldMouse(column.getPositionX() + column.getSizeWidth(), column.getPositionY() + column.getSizeHeight());
            float dx = Math.max(Math.max(top.x - event.x, event.x - bottom.x), 0);
            float dy = Math.max(Math.max(top.y - event.y, event.y - bottom.y), 0);
            float distance = dx * dx + dy * dy;
            if (distance <= nearestDistance && (distance > 0 || column.isSelfOrChildHover())) {
                nearest = column;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void savePosition(TacticalHudSidebar column) {
        var widget = column.getSide().getWidget();
        var old = TacticalHudPreferences.get(widget);
        var bounds = HudViewportPlacement.resolve(viewportWidth, viewportHeight, Math.round(column.getSizeWidth()), Math.round(column.getSizeHeight()), old.scale(), old.x(), old.y());
        double x = column.getLayoutX() / Math.max(1, viewportWidth - bounds.width() * bounds.scale());
        double y = column.getLayoutY() / Math.max(1, viewportHeight - bounds.height() * bounds.scale());
        TacticalHudPreferences.set(widget, old.toBuilder().x(Math.clamp(x, 0, 1)).y(Math.clamp(y, 0, 1)).build());
    }

    private UIElement element(HudSidebarEntry entry) {
        return switch (entry) {
            case HudSidebarEntry.Weapon(int slot) -> rows.get(slot);
            case HudSidebarEntry.Information(TacticalHudWidget widget) -> information.get(widget);
        };
    }

    private int entryHeight(HudSidebarEntry entry) {
        return switch (entry) {
            case HudSidebarEntry.Weapon weapon -> TacticalHudSidebar.ROW_HEIGHT;
            case HudSidebarEntry.Information(TacticalHudWidget widget) -> Math.round(28 * TacticalHudPreferences.get(widget).scale());
        };
    }

    private static void place(UIElement element, HudViewportPlacement bounds) {
        WeaponPlatformStyle.place(element, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        element.style(value -> value.transform2D(Transform2D.identity().pivot(0, 0).scale(bounds.scale())));
    }

    private record ComponentDrag(HudSidebarEntry entry, float grabX, float grabY) {}
}
