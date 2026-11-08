package com.fish_dan_.data_energistics.client.hud.weapon_platform.ui;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.layout.HudViewportPlacement;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.WeaponHudLayout;
import com.fish_dan_.data_energistics.config.client.hud.tactical.WeaponHudSide;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;

/** Client-thread owner of occupied rows, shared layout, drag/drop and the two movable columns. */
public final class TacticalWeaponColumns extends UIElement {

    private final EnumMap<WeaponHudSide, TacticalWeaponColumn> columns = new EnumMap<>(WeaponHudSide.class);
    private final Int2ObjectLinkedOpenHashMap<TacticalWeaponRow> rows = new Int2ObjectLinkedOpenHashMap<>();
    private WeaponHudLayout layout = WeaponHudLayout.EMPTY;
    private PlatformSnapshot displayed = PlatformSnapshot.EMPTY;
    private long revision = -1;
    private int occupiedMask = -1;
    private int viewportWidth, viewportHeight;
    private boolean interactive;

    public TacticalWeaponColumns() {
        setAllowHitTest(false);
        for (WeaponHudSide side : WeaponHudSide.values()) {
            TacticalWeaponColumn column = new TacticalWeaponColumn(side);
            columns.put(side, column);
            WindowDragHelper.setDragMove(column.handle, column, event -> interactive && event.button == 0, event -> savePosition(column));
            column.addEventListener(UIEvents.DRAG_ENTER, event -> preview(column, event));
            column.addEventListener(UIEvents.DRAG_UPDATE, event -> preview(column, event));
            column.addEventListener(UIEvents.DRAG_LEAVE, event -> column.insertion(-1));
            addChild(column);
        }
    }

    public void refresh(PlatformSnapshot snapshot, boolean editing, int width, int height) {
        if (width <= 0 || height <= 0) return;
        int mask = 0;
        for (var gun : snapshot.guns()) mask |= 1 << gun.slot();
        boolean settingsChanged = revision != TacticalHudPreferences.getRevision() || !displayed.systems().hud().equals(snapshot.systems().hud());
        boolean repack = mask != occupiedMask || settingsChanged;
        boolean replace = repack || interactive != editing || viewportWidth != width || viewportHeight != height;
        interactive = editing;
        viewportWidth = width;
        viewportHeight = height;
        occupiedMask = mask;
        if (repack) {
            WeaponHudLayout saved = TacticalHudPreferences.appearance().weapons();
            layout = saved.assign(mask);
            if (layout != saved) TacticalHudPreferences.setWeapons(layout);
            for (TacticalWeaponColumn column : columns.values()) column.list.clearAllChildren();
            rows.int2ObjectEntrySet().removeIf(entry -> (occupiedMask & 1 << entry.getIntKey()) == 0);
            for (var gun : snapshot.guns()) rows.computeIfAbsent(gun.slot(), this::createRow);
            for (TacticalWeaponColumn column : columns.values()) {
                IntList slots = layout.visible(column.side, mask);
                for (int index = 0; index < slots.size(); index++) {
                    TacticalWeaponRow row = rows.get(slots.getInt(index));
                    WeaponPlatformStyle.place(row, 0, index * TacticalWeaponColumn.ROW_HEIGHT, TacticalWeaponColumn.WIDTH - 24, TacticalWeaponColumn.ROW_HEIGHT);
                    column.list.addChild(row);
                }
            }
        }
        if (replace) {
            WeaponPlatformStyle.place(this, 0, 0, width, height);
            for (TacticalWeaponColumn column : columns.values()) {
                var style = TacticalHudPreferences.get(column.side.widget);
                int count = layout.visible(column.side, mask).size();
                int columnHeight = column.configure(count, style, editing);
                var bounds = HudViewportPlacement.resolve(width, height, TacticalWeaponColumn.WIDTH, columnHeight, style.scale(), style.x(), style.y());
                WeaponPlatformStyle.place(column, bounds.x(), bounds.y(), bounds.width(), bounds.height());
                column.style(value -> value.transform2D(Transform2D.identity().pivot(0, 0).scale(bounds.scale())));
                column.setDisplay(editing || TacticalHudPreferences.enabled(column.side.widget) && count > 0);
            }
            for (TacticalWeaponRow row : rows.values()) row.setAllowHitTest(editing);
        }
        if (repack || displayed != snapshot) {
            var fields = TacticalHudPreferences.get(HudModule.WEAPONS);
            for (WeaponHudSide side : WeaponHudSide.values()) {
                int color = TacticalHudPreferences.get(side.widget).textColor();
                IntList slots = layout.visible(side, mask);
                for (var gun : snapshot.guns()) {
                    if (slots.contains(gun.slot())) rows.get(gun.slot()).refresh(gun, snapshot.network(), fields, color);
                }
            }
        }
        displayed = snapshot;
        revision = TacticalHudPreferences.getRevision();
    }

    private TacticalWeaponRow createRow(int slot) {
        TacticalWeaponRow row = new TacticalWeaponRow();
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!interactive || event.button != 0) return;
            row.startDrag(new WeaponDrag(slot), row.model).setDragTexture(-10, -10, 20, 20);
            event.stopPropagation();
        });
        row.addEventListener(UIEvents.DRAG_END, event -> {
            TacticalWeaponColumn target = nearbyColumn(event);
            if (event.relatedTarget != null && target != null && (occupiedMask & 1 << slot) != 0) {
                IntList visible = layout.visible(target.side, occupiedMask);
                int index = insertion(target, event);
                int source = visible.indexOf(slot);
                if (source >= 0 && source < index) index--;
                TacticalHudPreferences.setWeapons(layout.move(slot, target.side, index, occupiedMask));
            }
            for (TacticalWeaponColumn column : columns.values()) column.insertion(-1);
        });
        row.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            TacticalWeaponColumn target = nearbyColumn(event);
            for (TacticalWeaponColumn column : columns.values()) column.insertion(column == target ? insertion(column, event) : -1);
        });
        return row;
    }

    private void preview(TacticalWeaponColumn column, UIEvent event) {
        if (event.dragHandler.draggingObject instanceof WeaponDrag drag && (occupiedMask & 1 << drag.slot()) != 0) {
            column.insertion(insertion(column, event));
            event.stopPropagation();
        }
    }

    private int insertion(TacticalWeaponColumn column, UIEvent event) {
        IntList visible = layout.visible(column.side, occupiedMask);
        float localY = column.getLocalMouse(event.x, event.y).y - column.getPositionY() - TacticalWeaponColumn.CONTENT_TOP;
        return Math.clamp(Math.round(localY / TacticalWeaponColumn.ROW_HEIGHT), 0, visible.size());
    }

    private @Nullable TacticalWeaponColumn nearbyColumn(UIEvent event) {
        TacticalWeaponColumn nearest = null;
        float nearestDistance = 12 * 12;
        for (TacticalWeaponColumn column : columns.values()) {
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

    private void savePosition(TacticalWeaponColumn column) {
        var old = TacticalHudPreferences.get(column.side.widget);
        var bounds = HudViewportPlacement.resolve(viewportWidth, viewportHeight, TacticalWeaponColumn.WIDTH, Math.round(column.getSizeHeight()), old.scale(), old.x(), old.y());
        double x = column.getLayoutX() / Math.max(1, viewportWidth - bounds.width() * bounds.scale());
        double y = column.getLayoutY() / Math.max(1, viewportHeight - bounds.height() * bounds.scale());
        TacticalHudPreferences.set(column.side.widget, old.toBuilder().x(Math.clamp(x, 0, 1)).y(Math.clamp(y, 0, 1)).build());
    }

    private record WeaponDrag(int slot) {}
}
