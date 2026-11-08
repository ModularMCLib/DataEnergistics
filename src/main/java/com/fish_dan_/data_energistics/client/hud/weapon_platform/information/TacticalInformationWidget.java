package com.fish_dan_.data_energistics.client.hud.weapon_platform.information;

import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalHudWidget;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import org.joml.Vector2f;

/** A single reusable information component, either freely placed or inserted into a sidebar. */
public final class TacticalInformationWidget extends UIElement {

    public static final int HEIGHT = 18;
    @Getter
    private final TacticalHudWidget widget;
    private static final ObjectList<Vector2f> CLOCK = clockOutline();
    private final Label label = new Label();

    public TacticalInformationWidget(TacticalHudWidget widget) {
        this.widget = widget;
        label.addClasses("platform-hud-label", "platform-hud-information-label");
        label.setAllowHitTest(false);
        style(style -> style.tooltips(Component.translatable(widget.translationKey())));
        addChild(label);
    }

    public int freeWidth() {
        return switch (widget) {
            case REAL_TIME, GAME_TIME -> 64;
            case SESSION_TIME -> 82;
            case CONNECTION -> 220;
            case ENERGY, FLOW -> 240;
            case COORDINATES -> 184;
            default -> throw new IllegalStateException("Sidebar containers are not information widgets");
        };
    }

    public void configure(int width, int height) {
        WeaponPlatformStyle.place(label, 14, 2, width - 18, height - 4);
    }

    public void refresh(PlatformSnapshot snapshot, Vec3 position, long dayTime, boolean docked) {
        var style = TacticalHudPreferences.get(widget);
        label.textStyle(value -> value.textColor(style.textColor()).textShadow(true));
        Component value = switch (widget) {
            case REAL_TIME -> Component.literal(TacticalHudClock.realTime());
            case GAME_TIME -> Component.literal(TacticalHudClock.gameTime(dayTime));
            case SESSION_TIME -> Component.literal(TacticalHudClock.sessionTime());
            case COORDINATES -> Component.literal("X " + (int) Math.floor(position.x) + (docked ? "\nY " : "  Y ") + (int) Math.floor(position.y) + "  Z " + (int) Math.floor(position.z));
            case CONNECTION -> {
                var connection = Component.translatable("gui.data_energistics.weapon_platform.network_short." + (snapshot.failed() ? "error" : snapshot.connected() ? "connected" : "disconnected"));
                if (snapshot.connected()) connection.append(docked ? "\n" : " · ").append(Component.translatable("hud.data_energistics.weapon_platform.quantum_link", snapshot.network().frequency()));
                yield connection;
            }
            case ENERGY -> snapshot.connected() ? Component.translatable(docked ? "hud.data_energistics.weapon_platform.energy_compact" : "hud.data_energistics.weapon_platform.energy",
                    amount(snapshot.network().stored()), amount(snapshot.network().capacity())) : unavailable();
            case FLOW -> snapshot.connected() ? Component.translatable(docked ? "hud.data_energistics.weapon_platform.energy_flow_stacked" : "hud.data_energistics.weapon_platform.energy_flow",
                    amount(snapshot.network().input()), amount(snapshot.network().usage())) : unavailable();
            default -> throw new IllegalStateException("Sidebar containers are not information widgets");
        };
        if (!value.equals(label.getValue())) label.setValue(value);
    }

    private Component unavailable() {
        return Component.translatable(widget.translationKey()).append(" —");
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var style = TacticalHudPreferences.get(widget);
        int color = style.textColor(), line = style.lineColor();
        int x = Math.round(getPositionX()) + 2, y = Math.round(getPositionY()) + 3;
        var graphics = context.graphics;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        switch (widget) {
            case REAL_TIME, GAME_TIME, SESSION_TIME -> {
                DrawerHelper.drawLines(graphics, CLOCK, color, color, 1);
                graphics.fill(4, 2, 5, 5, color);
                graphics.fill(4, 4, 7, 5, color);
                if (widget == TacticalHudWidget.SESSION_TIME) graphics.fill(3, -2, 6, -1, color);
                if (widget == TacticalHudWidget.REAL_TIME) graphics.fill(1, 8, 8, 9, color);
            }
            case CONNECTION -> {
                graphics.renderOutline(0, 2, 4, 4, color);
                graphics.renderOutline(5, 2, 4, 4, color);
                graphics.fill(3, 3, 6, 5, color);
            }
            case ENERGY -> {
                graphics.renderOutline(0, 1, 8, 7, color);
                graphics.fill(8, 3, 9, 6, color);
                graphics.fill(2, 3, 6, 6, color);
            }
            case FLOW -> {
                graphics.fill(0, 1, 7, 2, color);
                graphics.fill(6, 0, 8, 4, color);
                graphics.fill(2, 6, 9, 7, color);
                graphics.fill(1, 4, 3, 8, color);
            }
            case COORDINATES -> {
                graphics.fill(4, 0, 5, 9, color);
                graphics.fill(0, 4, 9, 5, color);
                graphics.renderOutline(2, 2, 5, 5, color);
            }
            default -> throw new IllegalStateException("Sidebar containers are not information widgets");
        }
        graphics.pose().popPose();
        if (style.lineWidth() > 0) graphics.fill(Math.round(getPositionX()), Math.round(getPositionY() + getSizeHeight() - style.lineWidth()),
                Math.round(getPositionX() + getSizeWidth()), Math.round(getPositionY() + getSizeHeight()), line);
    }

    private static String amount(double value) {
        return GenericStackDisplayHelper.formatCompactAmount((long) value);
    }

    private static ObjectList<Vector2f> clockOutline() {
        ObjectList<Vector2f> points = new ObjectArrayList<>(17);
        for (int index = 0; index <= 16; index++) {
            double angle = Math.PI * 2 * index / 16;
            points.add(new Vector2f(4 + (float) Math.cos(angle) * 4, 4 + (float) Math.sin(angle) * 4));
        }
        return points;
    }
}
