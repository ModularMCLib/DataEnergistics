package com.fish_dan_.data_energistics.client.hud.weapon_platform;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.gui.style.DEStylesheets;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** A vanilla status-row anchor with LDLib2 layout and the authored shield bar textures. */
public final class PlatformShieldHudLayer implements ModularHudLayer {

    private final UIElement root = new UIElement();
    private final UIElement bar = new UIElement();
    private final UIElement reveal = new UIElement();
    private @Nullable LocalPlayer owner;
    private @Nullable ModularUI ui;

    private PlatformShieldHudLayer() {
        bar.addClass("platform-shield-bar");
        bar.setAllowHitTest(false);
        reveal.setAllowHitTest(false);
        reveal.setOverflowVisible(false);
        UIElement fill = new UIElement();
        fill.addClass("platform-shield-bar-fill");
        fill.setAllowHitTest(false);
        reveal.addChild(fill);
        bar.addChild(reveal);
        root.addChild(bar);
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, Data_Energistics.id("platform_shield"), new PlatformShieldHudLayer());
    }

    @Override
    public @Nullable ModularUI getModularUI() {
        Minecraft client = Minecraft.getInstance();
        var snapshot = PlatformClientState.snapshot;
        if (client.player == null || client.options.hideGui || client.gameMode == null || !client.gameMode.canHurtPlayer() || !snapshot.present() || !snapshot.systems().shield().active()) return null;
        if (ui == null || owner != client.player) {
            if (ui != null) ui.onRemoved();
            owner = client.player;
            ui = ModularUI.of(UI.of(root, ObjectList.of(DEStylesheets.WEAPON_PLATFORM.get()), viewport -> viewport), owner);
        }
        var size = getScreenSize();
        bar.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(size.width / 2f + 21).top(size.height - client.gui.rightHeight + 2));
        int filled = (int) Math.round(70d * snapshot.systems().shield().charge() / snapshot.systems().shieldUpgrades().capacity());
        reveal.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(filled).height(6));
        client.gui.rightHeight += 8;
        return ui;
    }
}
