package com.fish_dan_.data_energistics.client.hud.weapon_platform.navigation;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.information.TacticalInformationWidget;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalCompass;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalHudFrame;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;
import com.fish_dan_.data_energistics.config.client.hud.HudModuleStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalHudWidget;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.EnumMap;

/**
 * One fixed top-center composition; data widths and visibility determine spacing without independent drag coordinates.
 */
public final class TacticalNavigationHeader extends UIElement {

    private static final int INFORMATION_HEIGHT = 11;
    private static final TacticalHudWidget[] TIMES = { TacticalHudWidget.REAL_TIME, TacticalHudWidget.GAME_TIME, TacticalHudWidget.SESSION_TIME };
    private final EnumMap<TacticalHudWidget, TacticalInformationWidget> information = new EnumMap<>(TacticalHudWidget.class);
    private final TacticalCompass compass = new TacticalCompass();
    private TacticalHudFrame frame = new TacticalHudFrame(420);
    private BlockPos position = BlockPos.ZERO;
    private long second = -1, revision = -1;
    private int viewportWidth, viewportHeight, compassFields = -1;
    private float scale = 1, frameX, frameY, bearingX, bearingY;
    private boolean showCompass, showBearing;

    public TacticalNavigationHeader() {
        setAllowHitTest(false);
        compass.setAllowHitTest(false);
        addChild(compass);
        for (TacticalHudWidget widget : TacticalHudWidget.values()) {
            if (!widget.fixedToHeader()) continue;
            TacticalInformationWidget element = new TacticalInformationWidget(widget, true);
            element.setAllowHitTest(false);
            information.put(widget, element);
            addChild(element);
        }
    }

    public void refresh(PlatformSnapshot snapshot, LocalPlayer player, int width, int height) {
        if (width <= 0 || height <= 0) return;
        long now = System.nanoTime() / 1_000_000_000L;
        var compassStyle = TacticalHudPreferences.get(HudModule.COMPASS);
        boolean changed = revision != TacticalHudPreferences.getRevision() || width != viewportWidth || height != viewportHeight || now != second || !position.equals(player.blockPosition()) || showCompass != compassStyle.visible() || compassFields != compassStyle.fields();
        if (!changed) return;
        viewportWidth = width;
        viewportHeight = height;
        second = now;
        position = player.blockPosition();
        compassFields = compassStyle.fields();
        for (TacticalInformationWidget element : information.values()) element.refresh(snapshot, player.position(), player.clientLevel.getDayTime(), false);
        arrange(Minecraft.getInstance().font);
        revision = TacticalHudPreferences.getRevision();
    }

    private void arrange(Font font) {
        var style = TacticalHudPreferences.get(HudModule.COMPASS);
        showCompass = style.visible();
        showBearing = showCompass && style.shows(HudModuleStyle.BEARING);
        float timeWidth = 0, timeHeight = 0;
        for (TacticalHudWidget widget : TIMES) {
            if (!TacticalHudPreferences.enabled(widget)) continue;
            float zoom = TacticalHudPreferences.get(widget).scale();
            if (timeWidth > 0) timeWidth += 6;
            timeWidth += information.get(widget).contentWidth(font) * zoom;
            timeHeight = Math.max(timeHeight, INFORMATION_HEIGHT * zoom);
        }
        TacticalInformationWidget coordinates = information.get(TacticalHudWidget.COORDINATES);
        boolean showCoordinates = TacticalHudPreferences.enabled(TacticalHudWidget.COORDINATES);
        float coordinatesZoom = TacticalHudPreferences.get(TacticalHudWidget.COORDINATES).scale();
        float bearingWidth = showBearing ? font.width("359°") + 4 : 0;
        float bottomWidth = bearingWidth + (showCoordinates ? coordinates.contentWidth(font) * coordinatesZoom : 0) + (showBearing && showCoordinates ? 4 : 0);
        float bottomHeight = Math.max(showBearing ? font.lineHeight : 0, showCoordinates ? INFORMATION_HEIGHT * coordinatesZoom : 0);
        float middleY = timeHeight > 0 ? timeHeight + 1 : 0;
        float bottomY = middleY + (showCompass ? TacticalCompass.HEIGHT + 1 : 0);
        float viewportScale = Math.min(viewportWidth / 854f, viewportHeight / 480f);
        scale = Math.min(style.scale() * viewportScale, Math.min(viewportWidth / (Math.max(360, Math.max(timeWidth, bottomWidth)) + 24),
                viewportHeight / (bottomY + bottomHeight + 6)));
        float top = 3 * viewportScale;
        WeaponPlatformStyle.place(this, 0, 0, viewportWidth, (int) Math.ceil(top + (bottomY + bottomHeight) * scale));
        float x = (viewportWidth - timeWidth * scale) / 2;
        for (TacticalHudWidget widget : TIMES) {
            TacticalInformationWidget element = information.get(widget);
            boolean visible = TacticalHudPreferences.enabled(widget);
            element.setDisplay(visible);
            if (!visible) continue;
            float zoom = TacticalHudPreferences.get(widget).scale();
            int width = element.contentWidth(font);
            element.configure(width, INFORMATION_HEIGHT);
            place(element, x, top + (timeHeight - INFORMATION_HEIGHT * zoom) * scale / 2, width, INFORMATION_HEIGHT, scale * zoom);
            x += (width * zoom + 6) * scale;
        }
        compass.setDisplay(showCompass);
        place(compass, (viewportWidth - TacticalCompass.WIDTH * scale) / 2, top + middleY * scale, TacticalCompass.WIDTH, TacticalCompass.HEIGHT, scale);
        x = (viewportWidth - bottomWidth * scale) / 2;
        bearingX = x;
        bearingY = top + (bottomY + (bottomHeight - font.lineHeight) / 2) * scale;
        coordinates.setDisplay(showCoordinates);
        if (showCoordinates) {
            int width = coordinates.contentWidth(font);
            coordinates.configure(width, INFORMATION_HEIGHT);
            place(coordinates, x + (bearingWidth + (showBearing ? 4 : 0)) * scale,
                    top + (bottomY + (bottomHeight - INFORMATION_HEIGHT * coordinatesZoom) / 2) * scale, width, INFORMATION_HEIGHT, scale * coordinatesZoom);
        }
        frameX = 8 * viewportScale;
        float length = viewportWidth - frameX * 2;
        float bend = 24 * scale;
        float tail = (length - TacticalCompass.WIDTH * scale) / 2 - bend;
        frameY = top + (middleY + 4) * scale;
        frame = new TacticalHudFrame(length, tail, bend, 6 * scale);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var style = TacticalHudPreferences.get(HudModule.COMPASS);
        if (showCompass) frame.horizontal(context.graphics, frameX, frameY, style.borderColor(), style.borderWidth() * scale);
        if (showBearing && context.mc.player != null) {
            var graphics = context.graphics;
            graphics.pose().pushPose();
            graphics.pose().translate(bearingX, bearingY, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(context.mc.font, Math.round(TacticalCompass.bearing(context.mc.player, context.partialTick)) + "°", 0, 0, style.textColor());
            graphics.pose().popPose();
        }
    }

    private static void place(UIElement element, float x, float y, int width, int height, float scale) {
        WeaponPlatformStyle.place(element, Math.round(x), Math.round(y), width, height);
        element.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
    }
}
